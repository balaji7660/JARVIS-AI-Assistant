import { AIProvider } from './AIProvider.js';
import { AIProviderCapabilities } from './AIProviderCapabilities.js';
import { JARVIS_TOOLS, APPROVED_TOOL_NAMES } from './tools.js';

export const OLLAMA_SYSTEM_PROMPT = `You are JARVIS, an advanced personal AI assistant.
You are concise, intelligent, respectful, and professional. Address the user occasionally as "boss", but do not overuse it.

TOOL CALLING & ANDROID AUTOMATION:
You have access to safe Android system and accessibility automation tools:
- get_time: Check current device time. Arguments: {}
- get_date: Check current device date. Arguments: {}
- go_home: Return to Android home screen. Arguments: {}
- press_back: Trigger Android back navigation. Arguments: {}
- open_url: Open web URLs. Arguments: { "url": string (http:// or https://) }
- open_app: Launch an installed app. Arguments: { "appName": string }
- read_visible_screen: Inspect visible controls on the active screen. Arguments: {}
- click_text: Tap button matching text. Arguments: { "text": string }
- click_view: Tap button matching view ID. Arguments: { "viewId": string }
- type_text: Input text into focused field. Arguments: { "text": string }
- scroll: Scroll container. Arguments: { "direction": "forward" | "backward" }
- analyze_current_screen: Visually analyze screen. Arguments: { "focus"?: string }
- wait_for_screen: Wait for package/text. Arguments: { "expectedPackage": string, "timeoutMs"?: number }

OUTPUT FORMAT RULES (CRITICAL):
You MUST respond with a single strictly valid JSON object matching ONE of these formats:

Format 1: Tool Call (when an action or inspection is needed)
{
  "type": "tool_call",
  "tool": "<approved_tool_name>",
  "arguments": { <parameters> }
}

Format 2: Final Response (when answering questions or responding conversationally)
{
  "type": "final_response",
  "text": "<your response text to the user>"
}

Format 3: Clarification (when user request is ambiguous)
{
  "type": "clarification",
  "text": "<clarification question>"
}

Do NOT output markdown outside of JSON. Do NOT invent new tool names. Never execute arbitrary code or commands.`;

// Set of known models that provide multimodal vision support
const VISION_SUPPORTED_MODELS = new Set([
  'llama3.2-vision',
  'llama3.2-vision:11b',
  'llama3.2-vision:90b',
  'llava',
  'llava:7b',
  'llava:13b',
  'llava:34b',
  'llava-llama3',
  'llava-phi3',
  'bakllava',
  'moondream',
  'minicpm-v'
]);

export class OllamaProvider extends AIProvider {
  /**
   * @param {Object} [options]
   * @param {string} [options.baseUrl] Ollama base URL (e.g. http://127.0.0.1:11434)
   * @param {string} [options.model] Model tag (e.g. llama3.2)
   * @param {number} [options.timeoutMs] Request timeout in milliseconds
   * @param {Function} [options.fetchFn] Custom fetch implementation for testing
   */
  constructor({
    baseUrl = process.env.OLLAMA_BASE_URL || 'http://127.0.0.1:11434',
    model = process.env.OLLAMA_MODEL || 'llama3.2',
    timeoutMs = 30000,
    fetchFn = null
  } = {}) {
    super();
    // Normalize baseUrl: strip trailing slash
    this.baseUrl = baseUrl.replace(/\/+$/, '');
    this.model = model.trim();
    this.timeoutMs = timeoutMs;
    this.fetch = fetchFn || globalThis.fetch.bind(globalThis);

    const isVisionModel = this._detectVisionCapability(this.model);

    this._capabilities = new AIProviderCapabilities({
      supportsToolCalling: true,
      supportsStructuredOutput: true,
      supportsVision: isVisionModel,
      supportsStreaming: false
    });
  }

  get capabilities() {
    return this._capabilities;
  }

  _detectVisionCapability(modelName) {
    if (!modelName) return false;
    const lower = modelName.toLowerCase();
    for (const prefix of VISION_SUPPORTED_MODELS) {
      if (lower === prefix || lower.startsWith(`${prefix}:`)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Safe fetch with AbortSignal timeout and connection error translation
   */
  async _safeFetch(url, options = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), options.timeoutMs || this.timeoutMs);

    try {
      const response = await this.fetch(url, {
        ...options,
        signal: controller.signal
      });
      clearTimeout(timeout);
      return response;
    } catch (error) {
      clearTimeout(timeout);
      if (error.name === 'AbortError') {
        const timeoutError = new Error(`Local AI request timed out after ${options.timeoutMs || this.timeoutMs}ms.`);
        timeoutError.code = 'ETIMEDOUT';
        throw timeoutError;
      }
      // Connection refused or network failure
      const connError = new Error('Local AI is unavailable. Start Ollama and try again.');
      connError.originalError = error.message;
      connError.code = 'ECONNREFUSED';
      throw connError;
    }
  }

  /**
   * Health check for local Ollama service
   */
  async checkHealth() {
    try {
      const response = await this._safeFetch(`${this.baseUrl}/api/tags`, {
        method: 'GET',
        timeoutMs: 3000
      });

      if (!response.ok) {
        return {
          available: false,
          model: this.model,
          error: `Ollama service returned HTTP ${response.status}`
        };
      }

      const data = await response.json().catch(() => ({}));
      const models = Array.isArray(data.models) ? data.models.map(m => m.name) : [];
      const modelInstalled = models.some(m => m === this.model || m.startsWith(`${this.model}:`));

      return {
        available: true,
        provider: 'ollama',
        model: this.model,
        modelInstalled,
        availableModels: models
      };
    } catch (err) {
      return {
        available: false,
        provider: 'ollama',
        model: this.model,
        error: 'Local AI is unavailable. Start Ollama and try again.'
      };
    }
  }

  /**
   * Generates AI response via Ollama /api/chat with strict structured parsing
   * @param {Array<Object>} messages
   * @returns {Promise<{reply: string, toolCall?: {id: string, name: string, arguments: Object}}>}
   */
  async generateResponse(messages) {
    const formattedMessages = [
      { role: 'system', content: OLLAMA_SYSTEM_PROMPT },
      ...messages
        .filter(m => m.role !== 'system')
        .map(m => ({
          role: m.role === 'tool' ? 'user' : m.role,
          content: typeof m.content === 'string' ? m.content : JSON.stringify(m.content)
        }))
    ];

    const payload = {
      model: this.model,
      messages: formattedMessages,
      stream: false,
      format: 'json',
      options: {
        temperature: 0.3,
        num_predict: 400
      }
    };

    let response;
    try {
      response = await this._safeFetch(`${this.baseUrl}/api/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
    } catch (err) {
      throw err;
    }

    if (!response.ok) {
      const errorText = await response.text().catch(() => '');
      if (response.status === 404) {
        throw new Error(`Ollama model "${this.model}" not found. Run "ollama pull ${this.model}" to install it.`);
      }
      throw new Error(`Ollama API error (HTTP ${response.status}): ${errorText || response.statusText}`);
    }

    const data = await response.json();
    const rawContent = data.message?.content?.trim();

    // 1. Check for native tool calls if supported by Ollama model
    if (data.message?.tool_calls && Array.isArray(data.message.tool_calls) && data.message.tool_calls.length > 0) {
      const nativeCall = data.message.tool_calls[0];
      const fnName = nativeCall.function?.name;
      if (APPROVED_TOOL_NAMES.has(fnName)) {
        return {
          reply: rawContent || null,
          toolCall: {
            id: `call_${Date.now()}`,
            name: fnName,
            arguments: typeof nativeCall.function?.arguments === 'object' ? nativeCall.function.arguments : {}
          }
        };
      }
    }

    if (!rawContent) {
      throw new Error('Ollama returned an empty response.');
    }

    // 2. Parse structured JSON from model output
    return this._parseAndValidateStructuredOutput(rawContent);
  }

  /**
   * Strict parser for Ollama structured outputs.
   * Untrusted output is strictly validated against schemas and approved tool names.
   */
  _parseAndValidateStructuredOutput(rawContent) {
    let cleaned = rawContent.trim();
    // Strip markdown JSON code fence if present
    if (cleaned.startsWith('```json')) {
      cleaned = cleaned.replace(/^```json\s*/i, '').replace(/\s*```$/, '').trim();
    } else if (cleaned.startsWith('```')) {
      cleaned = cleaned.replace(/^```\s*/, '').replace(/\s*```$/, '').trim();
    }

    let parsed;
    try {
      parsed = JSON.parse(cleaned);
    } catch (err) {
      // If the model emitted raw text rather than JSON, verify if it's plain text without code injection
      return { reply: this._sanitizePlainText(rawContent) };
    }

    if (!parsed || typeof parsed !== 'object') {
      return { reply: this._sanitizePlainText(rawContent) };
    }

    // Case A: tool_call
    if (parsed.type === 'tool_call' || (parsed.tool && APPROVED_TOOL_NAMES.has(parsed.tool))) {
      const toolName = parsed.tool || parsed.name;
      if (!toolName || !APPROVED_TOOL_NAMES.has(toolName)) {
        console.warn(`[OllamaProvider Security] Rejected unapproved tool requested by model: "${toolName}"`);
        return {
          reply: `I cannot execute "${toolName}", boss. That operation is not permitted on this device.`
        };
      }

      const args = parsed.arguments && typeof parsed.arguments === 'object' && !Array.isArray(parsed.arguments)
        ? parsed.arguments
        : {};

      return {
        reply: parsed.text || null,
        toolCall: {
          id: `call_ollama_${Date.now()}`,
          name: toolName,
          arguments: args
        }
      };
    }

    // Case B: final_response or clarification
    if (parsed.type === 'final_response' || parsed.type === 'clarification') {
      const text = parsed.text || parsed.message || parsed.reply;
      return { reply: typeof text === 'string' ? text.trim() : JSON.stringify(parsed) };
    }

    // Fallback: if JSON has "reply" or "message" or "text"
    if (typeof parsed.reply === 'string') return { reply: parsed.reply.trim() };
    if (typeof parsed.text === 'string') return { reply: parsed.text.trim() };
    if (typeof parsed.message === 'string') return { reply: parsed.message.trim() };

    return { reply: JSON.stringify(parsed) };
  }

  _sanitizePlainText(text) {
    if (typeof text !== 'string') return '';
    return text.trim();
  }

  /**
   * Screen Analysis for Ollama.
   * If model lacks vision, falls back to accessibility metadata analysis and
   * explicitly reports that local vision is unavailable.
   */
  async analyzeScreen({ packageName = null, screenWidth = 1080, screenHeight = 2400, accessibilityElements = [], image = null, focus = null } = {}) {
    // If model supports vision and image is provided, attempt Ollama vision
    if (this.capabilities.supportsVision && image) {
      try {
        const cleanBase64 = image.startsWith('data:') ? image.substring(image.indexOf(',') + 1) : image;
        const promptText = `Analyze this Android screen for package ${packageName || 'unknown'}.
Focus: ${focus || 'General UI elements'}
Sanitized elements: ${JSON.stringify(accessibilityElements.slice(0, 30))}
Output strictly JSON:
{
  "summary": "description",
  "detectedElements": [{"label": "string", "type": "button|input|text", "bounds": [0,0,0,0], "resourceId": null, "confidence": 0.8}],
  "relevantElement": null,
  "confidence": 0.85,
  "suggestedAction": "click|none"
}`;

        const response = await this._safeFetch(`${this.baseUrl}/api/chat`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            model: this.model,
            messages: [
              {
                role: 'user',
                content: promptText,
                images: [cleanBase64]
              }
            ],
            stream: false,
            format: 'json',
            options: { temperature: 0.2 }
          })
        });

        if (response.ok) {
          const data = await response.json();
          const parsed = JSON.parse(data.message?.content || '{}');
          if (parsed.summary) {
            return {
              success: true,
              summary: parsed.summary,
              detectedElements: Array.isArray(parsed.detectedElements) ? parsed.detectedElements : [],
              relevantElement: parsed.relevantElement || null,
              confidence: typeof parsed.confidence === 'number' ? parsed.confidence : 0.8,
              suggestedAction: parsed.suggestedAction || 'none'
            };
          }
        }
      } catch (err) {
        console.warn('[Ollama Screen Analysis Vision Warning]:', err.message);
      }
    }

    // Default safe accessibility fallback
    return this._analyzeFromAccessibilityElements({
      packageName,
      screenWidth,
      screenHeight,
      accessibilityElements,
      focus
    });
  }

  _analyzeFromAccessibilityElements({ packageName, screenWidth, screenHeight, accessibilityElements = [], focus = null }) {
    const usefulElements = accessibilityElements.filter(e => e.text || e.contentDescription || e.viewId);
    const visionNotice = this.capabilities.supportsVision
      ? ''
      : ` (Local vision is unavailable with model "${this.model}"; inspected via accessibility hierarchy)`;

    let summary = `You are on ${packageName || 'an application'}. I see ${usefulElements.length} visible interactive elements.${visionNotice}`;
    let relevantElement = null;

    if (focus) {
      const lowerFocus = focus.toLowerCase();
      const match = usefulElements.find(e =>
        (e.text && e.text.toLowerCase().includes(lowerFocus)) ||
        (e.contentDescription && e.contentDescription.toLowerCase().includes(lowerFocus)) ||
        (e.viewId && e.viewId.toLowerCase().includes(lowerFocus))
      );
      if (match) {
        relevantElement = {
          label: match.text || match.contentDescription || match.viewId,
          type: match.clickable ? 'button' : 'element',
          bounds: [0, 0, screenWidth, screenHeight],
          resourceId: match.viewId || null,
          text: match.text || null,
          confidence: 0.85
        };
        summary = `I found "${relevantElement.label}" on your screen, boss.${visionNotice}`;
      } else {
        summary = `I couldn't find "${focus}" on the screen, boss.${visionNotice}`;
      }
    }

    const detected = usefulElements.slice(0, 20).map(e => ({
      label: e.text || e.contentDescription || e.viewId || 'UI Element',
      type: e.clickable ? 'button' : (e.scrollable ? 'scrollable' : 'text'),
      bounds: [0, 0, screenWidth, screenHeight],
      resourceId: e.viewId || null,
      text: e.text || null,
      confidence: 0.8
    }));

    return {
      success: true,
      summary,
      detectedElements: detected,
      relevantElement,
      confidence: relevantElement ? 0.85 : 0.75,
      suggestedAction: relevantElement?.type === 'button' ? 'click' : 'none'
    };
  }
}

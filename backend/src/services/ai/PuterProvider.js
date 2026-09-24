import { AIProvider } from './AIProvider.js';
import { AIProviderCapabilities } from './AIProviderCapabilities.js';
import { JARVIS_TOOLS, APPROVED_TOOL_NAMES } from './tools.js';
import { JARVIS_SYSTEM_PROMPT } from './OpenAIProvider.js';

// Multimodal models supported by Puter that accept image inputs
const VISION_SUPPORTED_MODELS = new Set([
  'gpt-4o',
  'gpt-4o-mini',
  'gpt-4-vision-preview',
  'gpt-4-turbo',
  'claude-3-5-sonnet',
  'claude-3-7-sonnet',
  'claude-3-haiku',
  'claude-3-opus',
  'gemini-1.5-pro',
  'gemini-1.5-flash',
  'gemini-2.0-flash'
]);

export class PuterProvider extends AIProvider {
  /**
   * @param {Object} [options]
   * @param {string} [options.authToken] Puter auth token
   * @param {string} [options.model] Configured model identifier
   * @param {number} [options.timeoutMs] Bounded request timeout in ms (default: 30000)
   * @param {Object} [options.client] Injected Puter client or mock for testing
   */
  constructor({
    authToken = process.env.PUTER_AUTH_TOKEN,
    model = process.env.PUTER_MODEL || 'gpt-4o-mini',
    timeoutMs = 30000,
    client = null
  } = {}) {
    super();
    Object.defineProperty(this, 'authToken', {
      value: authToken ? authToken.trim() : '',
      enumerable: false,
      writable: true,
      configurable: true
    });
    this.model = (model || 'gpt-4o-mini').trim();
    this.timeoutMs = timeoutMs;
    this.client = client;
    this._puterInstance = client || null;

    const isVisionSupported = this._detectVisionCapability(this.model);
    this._capabilities = new AIProviderCapabilities({
      supportsToolCalling: true,
      supportsStructuredOutput: true,
      supportsVision: isVisionSupported,
      supportsStreaming: false
    });
  }

  toJSON() {
    return {
      provider: 'puter',
      model: this.model,
      capabilities: this.capabilities
    };
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
   * Initializes or returns the authenticated Puter SDK instance.
   * Throws clear configuration error if PUTER_AUTH_TOKEN is missing.
   */
  async _getPuterClient() {
    if (this._puterInstance) {
      return this._puterInstance;
    }

    if (!this.authToken || this.authToken === 'your_token_here') {
      throw new Error('PUTER_AUTH_TOKEN is not configured in the backend environment.');
    }

    try {
      const { init } = await import('@heyputer/puter.js/src/init.cjs');
      this._puterInstance = init(this.authToken);
      return this._puterInstance;
    } catch (err) {
      throw new Error(`Failed to initialize Puter SDK: ${err.message}`);
    }
  }

  /**
   * Wraps an async operation with bounded timeout.
   */
  async _withTimeout(promise, ms = this.timeoutMs) {
    let timer;
    const timeoutPromise = new Promise((_, reject) => {
      timer = setTimeout(() => {
        const error = new Error(`Puter AI request timed out after ${ms}ms.`);
        error.code = 'ETIMEDOUT';
        reject(error);
      }, ms);
    });

    try {
      return await Promise.race([promise, timeoutPromise]);
    } finally {
      clearTimeout(timer);
    }
  }

  /**
   * Health and status check for Puter AI service
   */
  async checkHealth() {
    const isConfigured = Boolean(this.authToken && this.authToken !== 'your_token_here');
    if (!isConfigured) {
      return {
        available: false,
        provider: 'puter',
        model: this.model,
        configured: false,
        error: 'PUTER_AUTH_TOKEN is not configured.'
      };
    }

    try {
      if (this.client && typeof this.client.checkHealth === 'function') {
        const custom = await this.client.checkHealth();
        return {
          available: custom.available !== false,
          provider: 'puter',
          model: this.model,
          configured: true,
          ...custom
        };
      }

      return {
        available: true,
        provider: 'puter',
        model: this.model,
        configured: true
      };
    } catch (err) {
      return {
        available: false,
        provider: 'puter',
        model: this.model,
        configured: true,
        error: 'Puter AI is currently unavailable. Please check the Puter connection.'
      };
    }
  }

  /**
   * Formats chat messages for Puter SDK.
   */
  _formatMessages(messages) {
    const hasSystem = messages.some(m => m.role === 'system');
    const formatted = [];

    if (!hasSystem) {
      formatted.push({ role: 'system', content: JARVIS_SYSTEM_PROMPT });
    }

    for (let i = 0; i < messages.length; i++) {
      const msg = messages[i];
      if (msg.role === 'tool') {
        const callId = msg.tool_call_id || 'call_default';
        const lastMsg = formatted[formatted.length - 1];
        // Puter/OpenAI requires that a message with role 'tool' is preceded by an assistant message with tool_calls
        if (!lastMsg || lastMsg.role !== 'assistant' || !lastMsg.tool_calls) {
          formatted.push({
            role: 'assistant',
            content: null,
            tool_calls: [
              {
                id: callId,
                type: 'function',
                function: {
                  name: 'system_action',
                  arguments: '{}'
                }
              }
            ]
          });
        }
        formatted.push({
          role: 'tool',
          tool_call_id: callId,
          content: typeof msg.content === 'string' ? msg.content : JSON.stringify(msg.content)
        });
      } else if (msg.role === 'assistant' && msg.tool_calls && Array.isArray(msg.tool_calls) && msg.tool_calls.length > 0) {
        // Collect following tool responses in the messages array
        const respondedCallIds = new Set();
        for (let j = i + 1; j < messages.length; j++) {
          if (messages[j].role === 'tool') {
            if (messages[j].tool_call_id) respondedCallIds.add(messages[j].tool_call_id);
          } else {
            break;
          }
        }

        formatted.push({
          role: 'assistant',
          content: msg.content ?? null,
          tool_calls: msg.tool_calls
        });

        // Ensure every tool_call in this assistant message is responded to before subsequent messages
        for (const call of msg.tool_calls) {
          const callId = call.id || 'call_default';
          if (!respondedCallIds.has(callId)) {
            formatted.push({
              role: 'tool',
              tool_call_id: callId,
              content: JSON.stringify({ success: true, status: 'acknowledged' })
            });
          }
        }
      } else {
        formatted.push({
          role: msg.role,
          content: typeof msg.content === 'string' ? msg.content : JSON.stringify(msg.content)
        });
      }
    }

    return formatted;
  }

  /**
   * Main chat and tool execution interface
   * @param {Array<Object>} messages
   * @returns {Promise<{reply: string, toolCall?: {id: string, name: string, arguments: Object}}>}
   */
  async generateResponse(messages) {
    console.log('[AI] Provider: puter');
    console.log('[AI] Puter request started');

    const puter = await this._getPuterClient();
    const formattedMessages = this._formatMessages(messages);

    const callPromise = (async () => {
      // Handle injected mock client interface or official Puter SDK
      if (typeof puter.ai?.chat === 'function') {
        return puter.ai.chat(formattedMessages, {
          model: this.model,
          tools: JARVIS_TOOLS,
          stream: false
        });
      } else if (typeof puter.chat === 'function') {
        return puter.chat(formattedMessages, {
          model: this.model,
          tools: JARVIS_TOOLS,
          stream: false
        });
      } else {
        throw new Error('Puter AI chat interface is unavailable on the client.');
      }
    })();

    let response;
    try {
      response = await this._withTimeout(callPromise, this.timeoutMs);
    } catch (err) {
      if (err.code === 'ETIMEDOUT' || err.message?.includes('timed out')) {
        const timeoutError = new Error(`Puter AI request timed out after ${this.timeoutMs}ms.`);
        timeoutError.code = 'ETIMEDOUT';
        throw timeoutError;
      }
      if (
        err.message?.includes('auth') ||
        err.message?.includes('token') ||
        err.message?.includes('401') ||
        err.message?.includes('403')
      ) {
        const authError = new Error('Puter authentication failed. Please verify PUTER_AUTH_TOKEN.');
        authError.code = 'EAUTH';
        throw authError;
      }
      throw err;
    }

    return this._parseAndValidatePuterResponse(response);
  }

  /**
   * Strictly parses and validates Puter output.
   * Proposes tools ONLY from APPROVED_TOOL_NAMES.
   * Rejects arbitrary code, shell commands, or unapproved tools safely.
   */
  _parseAndValidatePuterResponse(response) {
    if (!response) {
      throw new Error('Puter returned an empty response.');
    }

    // Extract tool calls from standard response structures
    const message = response.message || response.choices?.[0]?.message || response;
    const toolCalls = message.tool_calls || response.tool_calls;

    // 1. Tool Call handling
    if (Array.isArray(toolCalls) && toolCalls.length > 0) {
      const firstCall = toolCalls[0];
      const fn = firstCall.function || firstCall;
      const toolName = fn.name;

      console.log(`[AI] Puter tool call received: ${toolName}`);

      // Strict allowlist validation
      if (!toolName || !APPROVED_TOOL_NAMES.has(toolName)) {
        console.warn(`[PuterProvider Security] Rejected unapproved tool requested by model: "${toolName}"`);
        return {
          reply: `I cannot execute "${toolName}", boss. That operation is not permitted on this device.`
        };
      }

      console.log('[AI] Tool validation passed');

      let parsedArgs = {};
      if (typeof fn.arguments === 'string') {
        try {
          parsedArgs = JSON.parse(fn.arguments);
        } catch (e) {
          console.warn('[PuterProvider] Failed to parse tool arguments JSON:', fn.arguments);
          parsedArgs = {};
        }
      } else if (typeof fn.arguments === 'object' && fn.arguments !== null && !Array.isArray(fn.arguments)) {
        parsedArgs = fn.arguments;
      }

      return {
        reply: typeof message.content === 'string' && message.content.trim() ? message.content.trim() : null,
        toolCall: {
          id: firstCall.id || `call_puter_${Date.now()}`,
          name: toolName,
          arguments: parsedArgs
        }
      };
    }

    // 2. Text response handling
    let rawContent = '';
    if (typeof message.content === 'string') {
      rawContent = message.content;
    } else if (typeof response.text === 'string') {
      rawContent = response.text;
    } else if (typeof response === 'string') {
      rawContent = response;
    } else if (typeof message === 'string') {
      rawContent = message;
    }

    const trimmed = rawContent.trim();
    if (!trimmed) {
      throw new Error('Puter returned an empty response.');
    }

    // Check if the model returned JSON containing a tool_call format
    if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
      try {
        const parsed = JSON.parse(trimmed);
        if (parsed.type === 'tool_call' || (parsed.tool && APPROVED_TOOL_NAMES.has(parsed.tool))) {
          const toolName = parsed.tool || parsed.name;
          if (!toolName || !APPROVED_TOOL_NAMES.has(toolName)) {
            console.warn(`[PuterProvider Security] Rejected unapproved tool requested by model: "${toolName}"`);
            return {
              reply: `I cannot execute "${toolName}", boss. That operation is not permitted on this device.`
            };
          }
          console.log(`[AI] Puter tool call received (from JSON): ${toolName}`);
          console.log('[AI] Tool validation passed');
          return {
            reply: parsed.text || null,
            toolCall: {
              id: `call_puter_${Date.now()}`,
              name: toolName,
              arguments: parsed.arguments && typeof parsed.arguments === 'object' ? parsed.arguments : {}
            }
          };
        }
        if (typeof parsed.reply === 'string') {
          console.log('[AI] Puter final response received');
          return { reply: parsed.reply.trim() };
        }
        if (typeof parsed.text === 'string') {
          console.log('[AI] Puter final response received');
          return { reply: parsed.text.trim() };
        }
      } catch (e) {
        // Not a tool JSON, fall through to return trimmed content
      }
    }

    console.log('[AI] Puter final response received');
    return {
      reply: trimmed
    };
  }

  /**
   * Screen Analysis using Puter
   * Multimodal vision if supported by model, otherwise sanitized accessibility fallback.
   */
  async analyzeScreen({ packageName = null, screenWidth = 1080, screenHeight = 2400, accessibilityElements = [], image = null, focus = null } = {}) {
    if (this.capabilities.supportsVision && image) {
      try {
        const cleanBase64 = image.startsWith('data:') ? image.substring(image.indexOf(',') + 1) : image;
        const puter = await this._getPuterClient();

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

        const messages = [
          {
            role: 'user',
            content: [
              { type: 'text', text: promptText },
              {
                type: 'image_url',
                image_url: { url: `data:image/jpeg;base64,${cleanBase64}` }
              }
            ]
          }
        ];

        const chatFn = puter.ai?.chat?.bind(puter.ai) || puter.chat?.bind(puter);
        const res = await this._withTimeout(chatFn(messages, { model: this.model }), this.timeoutMs);
        const content = res.message?.content || res.text || (typeof res === 'string' ? res : '');

        if (content) {
          const cleaned = content.replace(/^```json\s*/i, '').replace(/\s*```$/, '').trim();
          try {
            return JSON.parse(cleaned);
          } catch (e) {
            // Fall through
          }
        }
      } catch (err) {
        console.warn(`[PuterProvider] Vision error: ${err.message}. Falling back to accessibility metadata.`);
      }
    }

    // Text / accessibility metadata fallback
    const buttons = accessibilityElements.filter(e => e.isClickable && e.text).map(e => e.text);
    const summary = accessibilityElements.length > 0
      ? `Screen for ${packageName || 'current app'} with ${accessibilityElements.length} elements detected. Visible actions: ${buttons.slice(0, 5).join(', ')}.`
      : `Screen for ${packageName || 'current app'} is active.`;

    let relevantElement = null;
    if (focus && accessibilityElements.length > 0) {
      const match = accessibilityElements.find(e =>
        e.text?.toLowerCase().includes(focus.toLowerCase()) ||
        e.contentDescription?.toLowerCase().includes(focus.toLowerCase())
      );
      if (match) {
        relevantElement = {
          label: match.text || match.contentDescription,
          type: match.isClickable ? 'button' : 'text',
          bounds: match.boundsInScreen || [0, 0, 0, 0],
          resourceId: match.viewIdResourceName || null,
          confidence: 0.9
        };
      }
    }

    return {
      summary,
      detectedElements: accessibilityElements.slice(0, 10).map(e => ({
        label: e.text || e.contentDescription || 'unknown',
        type: e.isClickable ? 'button' : (e.isEditable ? 'input' : 'text'),
        bounds: e.boundsInScreen || [0, 0, 0, 0],
        resourceId: e.viewIdResourceName || null,
        confidence: 0.75
      })),
      relevantElement,
      confidence: relevantElement ? 0.9 : 0.7,
      suggestedAction: relevantElement ? 'click' : 'none',
      visionAvailable: this.capabilities.supportsVision
    };
  }
}

import { OpenAI } from 'openai';
import { AIProvider } from './AIProvider.js';
import { JARVIS_TOOLS, APPROVED_TOOL_NAMES } from './tools.js';

export const JARVIS_SYSTEM_PROMPT = `You are JARVIS, an advanced personal AI assistant.

You are concise, intelligent, respectful, and professional.
Address the user occasionally as "boss", but do not overuse it.

TOOL CALLING & ANDROID AUTOMATION:
- You have access to safe Android system and accessibility automation tools:
  * get_time: Check current device time.
  * get_date: Check current device date.
  * go_home: Return to Android home screen.
  * press_back: Trigger Android back navigation.
  * open_url: Open web URLs (http:// or https://).
  * open_app: Launch an installed application by name (e.g. Settings, YouTube, Chrome).
  * read_visible_screen: Inspect visible controls on the active Android screen.
  * click_text: Tap an on-screen button or control matching a text label.
  * click_view: Tap an on-screen control matching a view ID.
  * type_text: Input text into the currently focused field (requires confirmation).
  * scroll: Scroll visible container ("forward" or "backward").

MULTI-STEP WORKFLOWS:
- When a user asks you to perform an automation workflow (e.g. "Open Settings and tap Wi-Fi"):
  1. Open the application first.
  2. In the next turn, inspect the screen or tap the visible control.
- NEVER assume or claim a screen state or action succeeded before observing the tool result.
- If an action fails (e.g. "No visible clickable element matching Wi-Fi was found"), explain truthfully:
  "I opened Settings, boss, but I couldn't find Wi-Fi on the visible screen."
- Never invent UI buttons or fake outcomes.`;

export class OpenAIProvider extends AIProvider {
  /**
   * @param {Object} options
   * @param {string} [options.apiKey]
   * @param {string} [options.model]
   * @param {OpenAI} [options.client] Optional injected client for testing
   */
  constructor({ apiKey = process.env.OPENAI_API_KEY, model = process.env.OPENAI_MODEL || 'gpt-4o-mini', client = null } = {}) {
    super();
    this.apiKey = apiKey;
    this.model = model;
    this.client = client || (apiKey && apiKey !== 'your_key_here' ? new OpenAI({ apiKey }) : null);
  }

  /**
   * Generates response from OpenAI, returning text reply and optional structured tool call.
   * @param {Array<Object>} messages
   * @returns {Promise<{reply: string, toolCall?: {id: string, name: string, arguments: Object}}>}
   */
  async generateResponse(messages) {
    if (!this.client) {
      if (!this.apiKey || this.apiKey === 'your_key_here') {
        throw new Error('OPENAI_API_KEY is not configured in the backend environment.');
      }
      this.client = new OpenAI({ apiKey: this.apiKey });
    }

    const formattedMessages = [
      { role: 'system', content: JARVIS_SYSTEM_PROMPT },
      ...messages.filter(m => m.role !== 'system')
    ];

    const response = await this.client.chat.completions.create({
      model: this.model,
      messages: formattedMessages,
      tools: JARVIS_TOOLS,
      tool_choice: 'auto',
      temperature: 0.7,
      max_tokens: 300
    });

    const choice = response.choices?.[0];
    if (!choice || !choice.message) {
      throw new Error('OpenAI returned an empty response.');
    }

    const message = choice.message;
    const toolCalls = message.tool_calls;

    if (toolCalls && toolCalls.length > 0) {
      const rawToolCall = toolCalls[0];
      const functionName = rawToolCall.function?.name;

      if (APPROVED_TOOL_NAMES.has(functionName)) {
        let parsedArgs = {};
        try {
          if (rawToolCall.function?.arguments) {
            parsedArgs = JSON.parse(rawToolCall.function.arguments);
          }
        } catch {
          parsedArgs = {};
        }

        return {
          reply: message.content?.trim() || null,
          toolCall: {
            id: rawToolCall.id,
            name: functionName,
            arguments: parsedArgs
          }
        };
      }
    }

    const reply = message.content?.trim();
    if (!reply) {
      throw new Error('OpenAI returned an empty response without tool calls.');
    }

    return { reply };
  }

  /**
   * Dedicated Screen Analysis using OpenAI Vision + Accessibility Metadata.
   * Does NOT persist any image data.
   */
  async analyzeScreen({ packageName = null, screenWidth = 1080, screenHeight = 2400, accessibilityElements = [], image = null, focus = null } = {}) {
    if (this.client && image) {
      try {
        const imageUrl = image.startsWith('data:') ? image : `data:image/jpeg;base64,${image}`;
        const userPromptText = `You are analyzing the user's active Android screen.
Active Package: ${packageName || 'unknown'}
Screen Dimensions: ${screenWidth}x${screenHeight}
User Focus / Query: ${focus || 'General screen overview and visible interactive elements'}
Sanitized Accessibility Elements: ${JSON.stringify(accessibilityElements.slice(0, 50))}

Instructions:
1. Analyze the provided screenshot image and supplemental accessibility metadata.
2. Only report elements that are truly visible on screen. Do NOT hallucinate or infer hidden screens.
3. Never expose or analyze passwords, PINs, or credentials.
4. Output strictly valid JSON matching this schema:
{
  "summary": "Concise natural language summary of what is visible on the screen",
  "detectedElements": [
    {
      "label": "Human readable name or text of control",
      "type": "button | input | text | icon | image | scrollable",
      "bounds": [left, top, right, bottom],
      "resourceId": "optional view id or null",
      "text": "text on control or null",
      "confidence": 0.0 to 1.0
    }
  ],
  "relevantElement": {
    "label": "Element matching user focus if requested, or most prominent control",
    "type": "button | input | ...",
    "bounds": [left, top, right, bottom],
    "resourceId": "...",
    "text": "...",
    "confidence": 0.95
  },
  "confidence": 0.0 to 1.0,
  "suggestedAction": "click | type | scroll | read | none"
}`;

        const response = await this.client.chat.completions.create({
          model: this.model,
          messages: [
            {
              role: 'user',
              content: [
                { type: 'text', text: userPromptText },
                {
                  type: 'image_url',
                  image_url: {
                    url: imageUrl,
                    detail: 'low'
                  }
                }
              ]
            }
          ],
          response_format: { type: 'json_object' },
          max_tokens: 600,
          temperature: 0.2
        });

        const rawContent = response.choices?.[0]?.message?.content;
        if (rawContent) {
          const parsed = JSON.parse(rawContent);
          return {
            success: true,
            summary: parsed.summary || 'I analyzed the screen, boss.',
            detectedElements: Array.isArray(parsed.detectedElements) ? parsed.detectedElements : [],
            relevantElement: parsed.relevantElement || null,
            confidence: typeof parsed.confidence === 'number' ? parsed.confidence : 0.85,
            suggestedAction: parsed.suggestedAction || 'none'
          };
        }
      } catch (err) {
        console.warn('[Screen Analysis Vision Warning]:', err.message, '- Falling back to accessibility metadata.');
      }
    }

    return this._analyzeFromAccessibilityElements({ packageName, screenWidth, screenHeight, accessibilityElements, focus });
  }

  _analyzeFromAccessibilityElements({ packageName, screenWidth, screenHeight, accessibilityElements = [], focus = null }) {
    const usefulElements = accessibilityElements.filter(e => e.text || e.contentDescription || e.viewId);
    let summary = `You are on ${packageName || 'an application'}. I see ${usefulElements.length} interactive elements.`;
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
          confidence: 0.9
        };
        summary = `I found "${relevantElement.label}" on your screen, boss.`;
      } else {
        summary = `I couldn't find "${focus}" on the screen, boss.`;
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
      confidence: relevantElement ? 0.9 : 0.75,
      suggestedAction: relevantElement?.type === 'button' ? 'click' : 'none'
    };
  }
}

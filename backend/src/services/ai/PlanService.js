import { OpenAI } from 'openai';
import { APPROVED_TOOL_NAMES } from './tools.js';
import { OllamaProvider } from './OllamaProvider.js';
import { PuterProvider } from './PuterProvider.js';
import { getActiveProviderName } from './providerFactory.js';

export const PLANNER_SYSTEM_PROMPT = `You are JARVIS's Autonomous Task Planner.
Your job is to convert the user's high-level Android automation request into a minimal, bounded, structured sequence of verifiable steps.

RULES:
1. Return ONLY valid JSON adhering strictly to the TaskPlan schema:
{
  "type": "task_plan",
  "userRequest": "<original request>",
  "estimatedRisk": "LOW" | "MEDIUM" | "HIGH",
  "requiresConfirmation": boolean,
  "steps": [
    {
      "stepId": number,
      "action": "<tool_name>",
      "arguments": { ... },
      "expectedResult": {
        "expectedPackage": "<optional package name>",
        "expectedText": "<optional text to appear or verify>",
        "expectedScreenState": "<optional state description>"
      },
      "riskLevel": "LOW" | "MEDIUM" | "HIGH",
      "requiresConfirmation": boolean,
      "timeoutMs": number,
      "retryCount": number
    }
  ]
}

2. STRICT TOOL CONSTRAINTS:
Allowed actions:
- open_app: { "appName": string }
- wait_for_screen: { "expectedPackage": string, "expectedText"?: string, "timeoutMs"?: number }
- read_visible_screen: {}
- click_text: { "text": string }
- click_view: { "viewId": string }
- type_text: { "text": string } (Risk: MEDIUM, requiresConfirmation: true)
- scroll: { "direction": "forward" | "backward" }
- analyze_current_screen: { "focus"?: string }
- get_time: {}
- get_date: {}
- go_home: {}
- press_back: {}
- open_url: { "url": string }

3. STRICT SAFETY & BOUNDS:
- Maximum steps: 12
- Step timeoutMs: max 5000 (default 3000)
- Step retryCount: max 2 (default 1)
- NEVER automate passwords, OTPs, PINs, card numbers, or banking credentials.
- NEVER invent tools, shell commands, or arbitrary Android intents.
- When typing is required, riskLevel MUST be MEDIUM and requiresConfirmation MUST be true.
- Always include an expectedResult so the client can verify post-action state.
- Keep the plan minimal. Do not add redundant steps.`;

export class PlanService {
  /**
   * @param {Object} [options]
   * @param {import('./AIProvider.js').AIProvider} [options.aiProvider]
   * @param {string} [options.apiKey]
   * @param {string} [options.model]
   * @param {OpenAI} [options.client]
   */
  constructor({
    aiProvider = null,
    apiKey = process.env.OPENAI_API_KEY,
    model = null,
    client = null
  } = {}) {
    this.aiProvider = aiProvider;
    this.apiKey = apiKey;
    this.model = model || process.env.OPENAI_MODEL || 'gpt-4o-mini';
    this.client = client;
  }

  /**
   * Generates a structured TaskPlan from user prompt and context.
   * @param {Object} params
   * @param {string} params.prompt
   * @param {string} [params.memoryContext]
   * @param {string} [params.currentPackage]
   * @param {string} [params.visibleScreenText]
   * @param {Object} [params.context]
   * @returns {Promise<Object>}
   */
  async generatePlan({ prompt, memoryContext = '', currentPackage = '', visibleScreenText = '', context = null }) {
    if (!prompt || typeof prompt !== 'string' || prompt.trim().length === 0) {
      throw new Error('Prompt is required for task planning.');
    }

    const trimmedPrompt = prompt.trim();
    let activeProvider = getActiveProviderName();
    if (this.aiProvider) {
      if (this.aiProvider instanceof PuterProvider) {
        activeProvider = 'puter';
      } else if (this.aiProvider instanceof OllamaProvider) {
        activeProvider = 'ollama';
      } else {
        activeProvider = 'openai';
      }
    }

    const contextSection = context ? `
Context:
- Current App: ${context.currentApp || 'None'}
- Current Package: ${context.currentPackage || 'None'}
- Screen Summary: ${context.screenSummary || 'None'}
- Current Target: ${context.currentTarget || 'None'}
- Last Action: ${context.lastAction || 'None'}
- Recent Entities: ${(context.recentEntities || []).join(', ') || 'None'}` : '';

    const userMessageContent = `User Request: "${trimmedPrompt}"
Current Foreground Package: ${currentPackage || 'Unknown'}
Visible Screen Context: ${visibleScreenText || 'None'}
User Memory Context: ${memoryContext || 'None'}${contextSection}`;

    // 1. Puter Provider branch (Zero OpenAI / Zero Ollama involvement)
    if (activeProvider === 'puter') {
      const puter = this.aiProvider instanceof PuterProvider ? this.aiProvider : new PuterProvider();
      try {
        const client = await puter._getPuterClient();
        const messages = [
          { role: 'system', content: PLANNER_SYSTEM_PROMPT },
          { role: 'user', content: userMessageContent }
        ];

        const chatFn = client.ai?.chat?.bind(client.ai) || client.chat?.bind(client);
        if (!chatFn) {
          throw new Error('Puter AI chat interface unavailable.');
        }

        const response = await puter._withTimeout(
          chatFn(messages, {
            model: puter.model,
            stream: false
          }),
          puter.timeoutMs
        );

        const content = response.message?.content || response.text || (typeof response === 'string' ? response : null);
        if (!content) {
          throw new Error('Empty plan content returned from Puter.');
        }

        const cleaned = content.replace(/^```json\s*/i, '').replace(/\s*```$/, '').trim();
        const parsedPlan = JSON.parse(cleaned);
        return this.sanitizeAndValidatePlan(parsedPlan, trimmedPrompt);
      } catch (err) {
        console.warn(`[PlanService - Puter] Error: ${err.message}. Falling back to deterministic plan.`);
        return this.generateDeterministicPlan(trimmedPrompt, { memoryContext, currentPackage, visibleScreenText, context });
      }
    }

    // 2. Ollama Provider branch (Zero OpenAI involvement)
    if (activeProvider === 'ollama') {

      const ollama = this.aiProvider instanceof OllamaProvider ? this.aiProvider : new OllamaProvider();
      try {
        const response = await ollama._safeFetch(`${ollama.baseUrl}/api/chat`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            model: ollama.model,
            messages: [
              { role: 'system', content: PLANNER_SYSTEM_PROMPT },
              { role: 'user', content: userMessageContent }
            ],
            stream: false,
            format: 'json',
            options: {
              temperature: 0.2,
              num_predict: 600
            }
          })
        });

        if (!response.ok) {
          throw new Error(`Ollama plan generation failed with status ${response.status}`);
        }

        const data = await response.json();
        const content = data.message?.content;
        if (!content) {
          throw new Error('Empty plan content returned from Ollama.');
        }

        const parsedPlan = JSON.parse(content);
        return this.sanitizeAndValidatePlan(parsedPlan, trimmedPrompt);
      } catch (err) {
        console.warn(`[PlanService - Ollama] Error: ${err.message}. Falling back to deterministic plan.`);
        return this.generateDeterministicPlan(trimmedPrompt, { memoryContext, currentPackage, visibleScreenText, context });
      }
    }

    // 2. OpenAI Provider branch
    if (!this.client && (!this.apiKey || this.apiKey === 'your_key_here')) {
      return this.generateDeterministicPlan(trimmedPrompt, { memoryContext, currentPackage, visibleScreenText, context });
    }

    if (!this.client) {
      this.client = new OpenAI({ apiKey: this.apiKey });
    }

    try {
      const response = await this.client.chat.completions.create({
        model: this.model,
        messages: [
          { role: 'system', content: PLANNER_SYSTEM_PROMPT },
          { role: 'user', content: userMessageContent }
        ],
        response_format: { type: 'json_object' },
        temperature: 0.2
      });

      const content = response.choices?.[0]?.message?.content;
      if (!content) {
        throw new Error('Empty response from planning model.');
      }

      const parsedPlan = JSON.parse(content);
      return this.sanitizeAndValidatePlan(parsedPlan, trimmedPrompt);
    } catch (err) {
      console.warn(`[PlanService] Upstream planner error: ${err.message}. Falling back to deterministic plan.`);
      return this.generateDeterministicPlan(trimmedPrompt, { memoryContext, currentPackage, visibleScreenText, context });
    }
  }

  /**
   * Validates and cleans raw plan JSON from model before returning.
   */
  sanitizeAndValidatePlan(plan, originalRequest) {
    const steps = Array.isArray(plan.steps) ? plan.steps : [];
    const sanitizedSteps = [];

    for (let i = 0; i < Math.min(steps.length, 12); i++) {
      const s = steps[i];
      if (!s || !s.action || !APPROVED_TOOL_NAMES.has(s.action)) {
        continue;
      }

      const isTyping = s.action === 'type_text';
      const riskLevel = isTyping ? 'MEDIUM' : (s.riskLevel === 'HIGH' ? 'HIGH' : (s.riskLevel === 'MEDIUM' ? 'MEDIUM' : 'LOW'));
      const requiresConfirmation = isTyping ? true : Boolean(s.requiresConfirmation);

      sanitizedSteps.push({
        stepId: i + 1,
        action: s.action,
        arguments: s.arguments && typeof s.arguments === 'object' ? s.arguments : {},
        expectedResult: s.expectedResult && typeof s.expectedResult === 'object' ? s.expectedResult : {},
        riskLevel,
        requiresConfirmation,
        timeoutMs: Math.min(Math.max(Number(s.timeoutMs) || 3000, 500), 5000),
        retryCount: Math.min(Math.max(Number(s.retryCount) || 1, 0), 2)
      });
    }

    const hasMediumOrHigh = sanitizedSteps.some(s => s.riskLevel !== 'LOW' || s.requiresConfirmation);

    return {
      type: 'task_plan',
      taskId: `plan_${Date.now()}`,
      userRequest: originalRequest,
      estimatedRisk: hasMediumOrHigh ? 'MEDIUM' : 'LOW',
      requiresConfirmation: hasMediumOrHigh,
      steps: sanitizedSteps
    };
  }

  /**
   * Deterministic template generator for offline mode or fallback.
   */
  generateDeterministicPlan(prompt, { memoryContext, currentPackage, context = null } = {}) {
    const lower = prompt.toLowerCase();
    const steps = [];

    // Contextual ordinal result selection (e.g. "open the first result")
    if (lower.startsWith('open ') && (lower.includes('first result') || lower.includes('1st result') || lower.includes('the first'))) {
      steps.push({
        stepId: 1,
        action: 'click_text',
        arguments: { text: context?.currentTarget || 'Search Result' },
        expectedResult: {},
        riskLevel: 'LOW',
        requiresConfirmation: false,
        timeoutMs: 2000,
        retryCount: 1
      });
    } else if (lower.startsWith('search for ') || lower.startsWith('search ')) {
      // Contextual follow-up search when in app (e.g. YouTube)
      const queryPart = lower.startsWith('search for ') ? prompt.substring(11).trim() : prompt.substring(7).trim();
      const effectiveApp = (context?.currentApp || '').toLowerCase();
      if (effectiveApp === 'youtube' || (currentPackage && currentPackage.includes('youtube'))) {
        steps.push({
          stepId: 1,
          action: 'click_text',
          arguments: { text: 'Search' },
          expectedResult: { expectedText: 'Search' },
          riskLevel: 'LOW',
          requiresConfirmation: false,
          timeoutMs: 2000,
          retryCount: 1
        });
        steps.push({
          stepId: 2,
          action: 'type_text',
          arguments: { text: queryPart },
          expectedResult: { expectedText: queryPart },
          riskLevel: 'MEDIUM',
          requiresConfirmation: true,
          timeoutMs: 3000,
          retryCount: 1
        });
        steps.push({
          stepId: 3,
          action: 'wait_for_screen',
          arguments: { expectedPackage: 'com.google.android.youtube', timeoutMs: 7000 },
          expectedResult: { expectedScreenState: 'YouTube search results active' },
          riskLevel: 'LOW',
          requiresConfirmation: false,
          timeoutMs: 7000,
          retryCount: 1
        });
      }
    } else if (lower.startsWith('open ') && lower.includes(' and search for ')) {
      const openPart = lower.substring(5, lower.indexOf(' and search for ')).trim();
      const queryPart = prompt.substring(lower.indexOf(' and search for ') + 16).trim();

      const appName = openPart.toLowerCase() === 'youtube' ? 'YouTube' :
                      openPart.toLowerCase() === 'chrome' ? 'Chrome' :
                      openPart.toLowerCase() === 'settings' ? 'Settings' :
                      openPart.charAt(0).toUpperCase() + openPart.slice(1);
      const pkgName = openPart.toLowerCase() === 'youtube' ? 'com.google.android.youtube' :
                      openPart.toLowerCase() === 'chrome' ? 'com.android.chrome' :
                      openPart.toLowerCase() === 'settings' ? 'com.android.settings' : null;

      steps.push({
        stepId: 1,
        action: 'open_app',
        arguments: { appName },
        expectedResult: pkgName ? { expectedPackage: pkgName } : {},
        riskLevel: 'LOW',
        requiresConfirmation: false,
        timeoutMs: 3000,
        retryCount: 1
      });

      steps.push({
        stepId: 2,
        action: 'wait_for_screen',
        arguments: { expectedPackage: pkgName || '', timeoutMs: 7000 },
        expectedResult: { expectedScreenState: `${appName} screen active` },
        riskLevel: 'LOW',
        requiresConfirmation: false,
        timeoutMs: 7000,
        retryCount: 1
      });

      steps.push({
        stepId: 3,
        action: 'click_text',
        arguments: { text: 'Search' },
        expectedResult: { expectedText: 'Search' },
        riskLevel: 'LOW',
        requiresConfirmation: false,
        timeoutMs: 2000,
        retryCount: 1
      });

      steps.push({
        stepId: 4,
        action: 'type_text',
        arguments: { text: queryPart },
        expectedResult: { expectedText: queryPart },
        riskLevel: 'MEDIUM',
        requiresConfirmation: true,
        timeoutMs: 3000,
        retryCount: 1
      });
    } else if (lower.startsWith('open ') || lower.startsWith('launch ')) {
      const appName = prompt.substring(prompt.indexOf(' ') + 1).trim();
      steps.push({
        stepId: 1,
        action: 'open_app',
        arguments: { appName },
        expectedResult: {},
        riskLevel: 'LOW',
        requiresConfirmation: false,
        timeoutMs: 3000,
        retryCount: 1
      });
    } else if (lower.includes('what time') || lower.includes('time is it')) {
      steps.push({
        stepId: 1,
        action: 'get_time',
        arguments: {},
        expectedResult: {},
        riskLevel: 'LOW',
        requiresConfirmation: false,
        timeoutMs: 1000,
        retryCount: 0
      });
    } else if (lower.includes('what is on my screen') || lower.includes('analyze my screen')) {
      steps.push({
        stepId: 1,
        action: 'analyze_current_screen',
        arguments: {},
        expectedResult: {},
        riskLevel: 'LOW',
        requiresConfirmation: false,
        timeoutMs: 5000,
        retryCount: 1
      });
    } else {
      steps.push({
        stepId: 1,
        action: 'read_visible_screen',
        arguments: {},
        expectedResult: {},
        riskLevel: 'LOW',
        requiresConfirmation: false,
        timeoutMs: 2000,
        retryCount: 0
      });
    }

    const hasMedium = steps.some(s => s.riskLevel !== 'LOW' || s.requiresConfirmation);

    return {
      type: 'task_plan',
      taskId: `plan_local_${Date.now()}`,
      userRequest: prompt,
      estimatedRisk: hasMedium ? 'MEDIUM' : 'LOW',
      requiresConfirmation: hasMedium,
      steps
    };
  }
}

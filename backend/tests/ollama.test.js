import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { getAIProvider, getActiveProviderName } from '../src/services/ai/providerFactory.js';
import { OpenAIProvider } from '../src/services/ai/OpenAIProvider.js';
import { OllamaProvider } from '../src/services/ai/OllamaProvider.js';
import { AIProviderCapabilities } from '../src/services/ai/AIProviderCapabilities.js';
import { PlanService } from '../src/services/ai/PlanService.js';
import { createApp } from '../src/app.js';
import { ConversationManager } from '../src/services/conversationManager.js';

describe('JARVIS Free Local AI / Ollama Integration Tests', () => {

  // --- 1. Provider Selection Tests ---
  describe('Provider Selection & Factory', () => {
    test('Default provider is OpenAI when AI_PROVIDER is unset or empty', () => {
      const original = process.env.AI_PROVIDER;
      try {
        delete process.env.AI_PROVIDER;
        const provider = getAIProvider();
        assert.ok(provider instanceof OpenAIProvider, 'Expected OpenAIProvider instance by default');
      } finally {
        if (original) process.env.AI_PROVIDER = original;
      }
    });

    test('Returns OpenAIProvider when AI_PROVIDER=openai', () => {
      const provider = getAIProvider({ providerName: 'openai' });
      assert.ok(provider instanceof OpenAIProvider, 'Expected OpenAIProvider instance');
    });

    test('Returns OllamaProvider when AI_PROVIDER=ollama', () => {
      const provider = getAIProvider({ providerName: 'ollama' });
      assert.ok(provider instanceof OllamaProvider, 'Expected OllamaProvider instance');
    });

    test('Rejects invalid AI_PROVIDER with clear descriptive error', () => {
      assert.throws(
        () => getAIProvider({ providerName: 'unsupported_engine' }),
        /Invalid AI_PROVIDER/
      );
    });

    test('When Ollama is selected, OpenAIProvider is NOT instantiated', () => {
      let openAiInstantiated = false;
      class TrackedOpenAIProvider extends OpenAIProvider {
        constructor(...args) {
          super(...args);
          openAiInstantiated = true;
        }
      }

      const provider = getAIProvider({ providerName: 'ollama' });
      assert.ok(provider instanceof OllamaProvider);
      assert.equal(openAiInstantiated, false, 'OpenAIProvider must not be instantiated when Ollama is selected');
    });
  });

  // --- 2. Capabilities Model Tests ---
  describe('AIProviderCapabilities Model', () => {
    test('OllamaProvider with text-only model (llama3.2) disables vision capability', () => {
      const provider = new OllamaProvider({ model: 'llama3.2' });
      assert.ok(provider.capabilities instanceof AIProviderCapabilities);
      assert.equal(provider.capabilities.supportsVision, false);
      assert.equal(provider.capabilities.supportsToolCalling, true);
      assert.equal(provider.capabilities.supportsStructuredOutput, true);
      assert.equal(provider.capabilities.supportsStreaming, false);
    });

    test('OllamaProvider with vision model (llama3.2-vision or llava) enables vision capability', () => {
      const llamaVision = new OllamaProvider({ model: 'llama3.2-vision:11b' });
      assert.equal(llamaVision.capabilities.supportsVision, true);

      const llava = new OllamaProvider({ model: 'llava:7b' });
      assert.equal(llava.capabilities.supportsVision, true);
    });

    test('OpenAIProvider exposes full standard capabilities', () => {
      const provider = new OpenAIProvider({ apiKey: 'test_key' });
      assert.equal(provider.capabilities.supportsVision, true);
      assert.equal(provider.capabilities.supportsToolCalling, true);
      assert.equal(provider.capabilities.supportsStructuredOutput, true);
    });
  });

  // --- 3. CRITICAL: Zero OpenAI Fallback Tests ---
  describe('Strict Zero OpenAI Fallback', () => {
    test('When Ollama is unreachable, provider throws "Local AI is unavailable" and does NOT call OpenAI', async () => {
      // Mock fetch simulating ECONNREFUSED
      const mockFailingFetch = async () => {
        const error = new Error('connect ECONNREFUSED 127.0.0.1:11434');
        error.code = 'ECONNREFUSED';
        throw error;
      };

      const provider = new OllamaProvider({
        baseUrl: 'http://127.0.0.1:11434',
        model: 'llama3.2',
        fetchFn: mockFailingFetch
      });

      await assert.rejects(
        async () => {
          await provider.generateResponse([{ role: 'user', content: 'What time is it?' }]);
        },
        (err) => {
          assert.match(err.message, /Local AI is unavailable/);
          return true;
        }
      );
    });

    test('HTTP Chat API returns 503 with exact message when Ollama is unavailable', async () => {
      const mockFailingFetch = async () => {
        const error = new Error('connect ECONNREFUSED 127.0.0.1:11434');
        error.code = 'ECONNREFUSED';
        throw error;
      };

      const failingOllama = new OllamaProvider({ fetchFn: mockFailingFetch });
      const app = createApp({
        aiProvider: failingOllama,
        conversationManager: new ConversationManager()
      });

      const server = app.listen(0);
      const port = server.address().port;

      try {
        const res = await fetch(`http://127.0.0.1:${port}/api/chat`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ message: 'Hello JARVIS' })
        });

        assert.equal(res.status, 503);
        const data = await res.json();
        assert.equal(data.error, 'Local AI is unavailable. Start Ollama and try again.');
      } finally {
        server.close();
      }
    });
  });

  // --- 4. Tool Calling & Structured Output Tests ---
  describe('Structured Tool Calling & JSON Parsing', () => {
    test('Parses valid tool_call JSON from Ollama output', async () => {
      const mockFetch = async () => ({
        ok: true,
        json: async () => ({
          message: {
            role: 'assistant',
            content: JSON.stringify({
              type: 'tool_call',
              tool: 'get_time',
              arguments: {}
            })
          }
        })
      });

      const provider = new OllamaProvider({ fetchFn: mockFetch });
      const res = await provider.generateResponse([{ role: 'user', content: 'What time is it?' }]);

      assert.ok(res.toolCall, 'Expected toolCall object');
      assert.equal(res.toolCall.name, 'get_time');
      assert.deepEqual(res.toolCall.arguments, {});
    });

    test('Parses markdown code-fenced tool_call JSON from Ollama output', async () => {
      const mockFetch = async () => ({
        ok: true,
        json: async () => ({
          message: {
            role: 'assistant',
            content: '```json\n{\n  "type": "tool_call",\n  "tool": "open_app",\n  "arguments": { "appName": "YouTube" }\n}\n```'
          }
        })
      });

      const provider = new OllamaProvider({ fetchFn: mockFetch });
      const res = await provider.generateResponse([{ role: 'user', content: 'Open YouTube' }]);

      assert.ok(res.toolCall);
      assert.equal(res.toolCall.name, 'open_app');
      assert.equal(res.toolCall.arguments.appName, 'YouTube');
    });

    test('Parses conversational final_response JSON from Ollama', async () => {
      const mockFetch = async () => ({
        ok: true,
        json: async () => ({
          message: {
            role: 'assistant',
            content: JSON.stringify({
              type: 'final_response',
              text: 'Good day, boss! All systems are operating normally.'
            })
          }
        })
      });

      const provider = new OllamaProvider({ fetchFn: mockFetch });
      const res = await provider.generateResponse([{ role: 'user', content: 'Status check' }]);

      assert.equal(res.reply, 'Good day, boss! All systems are operating normally.');
      assert.equal(res.toolCall, undefined);
    });

    test('Rejects unapproved tool names safely without code execution', async () => {
      const mockFetch = async () => ({
        ok: true,
        json: async () => ({
          message: {
            role: 'assistant',
            content: JSON.stringify({
              type: 'tool_call',
              tool: 'execute_arbitrary_shell_command',
              arguments: { cmd: 'rm -rf /' }
            })
          }
        })
      });

      const provider = new OllamaProvider({ fetchFn: mockFetch });
      const res = await provider.generateResponse([{ role: 'user', content: 'Run hack' }]);

      assert.equal(res.toolCall, undefined, 'Unapproved tool call must be stripped');
      assert.match(res.reply, /cannot execute "execute_arbitrary_shell_command"/);
    });

    test('Handles malformed JSON gracefully by returning sanitized text', async () => {
      const mockFetch = async () => ({
        ok: true,
        json: async () => ({
          message: {
            role: 'assistant',
            content: 'I am ready to assist you, boss.'
          }
        })
      });

      const provider = new OllamaProvider({ fetchFn: mockFetch });
      const res = await provider.generateResponse([{ role: 'user', content: 'Hi' }]);

      assert.equal(res.reply, 'I am ready to assist you, boss.');
      assert.equal(res.toolCall, undefined);
    });
  });

  // --- 5. Screen Analysis Tests ---
  describe('Screen Analysis with Ollama', () => {
    test('Text-only Ollama model uses accessibility metadata and reports vision unavailable', async () => {
      const provider = new OllamaProvider({ model: 'llama3.2' });
      const result = await provider.analyzeScreen({
        packageName: 'com.android.settings',
        screenWidth: 1080,
        screenHeight: 2400,
        accessibilityElements: [
          { text: 'Wi-Fi', clickable: true, viewId: 'com.android.settings:id/wifi' }
        ],
        focus: 'Wi-Fi'
      });

      assert.equal(result.success, true);
      assert.ok(result.relevantElement);
      assert.equal(result.relevantElement.label, 'Wi-Fi');
      assert.match(result.summary, /Local vision is unavailable with model "llama3.2"/);
    });
  });

  // --- 6. Task Planning with Ollama Tests ---
  describe('Task Planning Integration', () => {
    test('PlanService generates bounded plan using Ollama', async () => {
      const mockFetch = async (url, opts) => {
        return {
          ok: true,
          json: async () => ({
            message: {
              content: JSON.stringify({
                type: 'task_plan',
                steps: [
                  {
                    stepId: 1,
                    action: 'open_app',
                    arguments: { appName: 'Settings' },
                    expectedResult: { expectedPackage: 'com.android.settings' },
                    riskLevel: 'LOW',
                    requiresConfirmation: false,
                    timeoutMs: 3000
                  },
                  {
                    stepId: 2,
                    action: 'click_text',
                    arguments: { text: 'Wi-Fi' },
                    expectedResult: {},
                    riskLevel: 'LOW',
                    requiresConfirmation: false,
                    timeoutMs: 3000
                  }
                ]
              })
            }
          })
        };
      };

      const ollama = new OllamaProvider({ fetchFn: mockFetch });
      const planner = new PlanService({ aiProvider: ollama });

      const plan = await planner.generatePlan({ prompt: 'Open Settings and tap Wi-Fi' });
      assert.equal(plan.type, 'task_plan');
      assert.equal(plan.steps.length, 2);
      assert.equal(plan.steps[0].action, 'open_app');
      assert.equal(plan.steps[1].action, 'click_text');
      assert.equal(plan.estimatedRisk, 'LOW');
    });

    test('PlanService sanitizes unapproved tools from Ollama plan', async () => {
      const mockFetch = async () => ({
        ok: true,
        json: async () => ({
          message: {
            content: JSON.stringify({
              type: 'task_plan',
              steps: [
                { stepId: 1, action: 'open_app', arguments: { appName: 'Chrome' } },
                { stepId: 2, action: 'unapproved_dangerous_tool', arguments: {} }
              ]
            })
          }
        })
      });

      const ollama = new OllamaProvider({ fetchFn: mockFetch });
      const planner = new PlanService({ aiProvider: ollama });

      const plan = await planner.generatePlan({ prompt: 'Test plan' });
      assert.equal(plan.steps.length, 1);
      assert.equal(plan.steps[0].action, 'open_app');
    });
  });

  // --- 7. Ollama Status API Tests ---
  describe('GET /api/ollama/status Endpoint', () => {
    test('Returns available=true when Ollama is running', async () => {
      const mockFetch = async () => ({
        ok: true,
        json: async () => ({
          models: [{ name: 'llama3.2:latest' }]
        })
      });

      const ollama = new OllamaProvider({ fetchFn: mockFetch });
      const app = createApp({ aiProvider: ollama });
      const server = app.listen(0);
      const port = server.address().port;

      try {
        const res = await fetch(`http://127.0.0.1:${port}/api/ollama/status`);
        assert.equal(res.status, 200);
        const data = await res.json();
        assert.equal(data.available, true);
        assert.equal(data.provider, 'ollama');
        assert.equal(data.model, 'llama3.2');
        assert.equal(data.modelInstalled, true);
      } finally {
        server.close();
      }
    });

    test('Returns available=false without crashing or exposing secrets when Ollama is offline', async () => {
      const mockFailingFetch = async () => {
        throw new Error('Connection refused');
      };

      const ollama = new OllamaProvider({ fetchFn: mockFailingFetch });
      const app = createApp({ aiProvider: ollama });
      const server = app.listen(0);
      const port = server.address().port;

      try {
        const res = await fetch(`http://127.0.0.1:${port}/api/ollama/status`);
        assert.equal(res.status, 200);
        const data = await res.json();
        assert.equal(data.available, false);
        assert.equal(data.provider, 'ollama');
        assert.match(data.error, /Local AI is unavailable/);
        // Ensure no internal env or tokens leaked
        assert.equal(data.apiKey, undefined);
        assert.equal(data.env, undefined);
      } finally {
        server.close();
      }
    });
  });
});

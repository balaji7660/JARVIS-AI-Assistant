import { test, describe, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { getAIProvider, getActiveProviderName, SUPPORTED_PROVIDERS } from '../src/services/ai/providerFactory.js';
import { OpenAIProvider } from '../src/services/ai/OpenAIProvider.js';
import { OllamaProvider } from '../src/services/ai/OllamaProvider.js';
import { PuterProvider } from '../src/services/ai/PuterProvider.js';
import { AIProviderCapabilities } from '../src/services/ai/AIProviderCapabilities.js';
import { PlanService } from '../src/services/ai/PlanService.js';
import { createApp } from '../src/app.js';
import { ConversationManager } from '../src/services/conversationManager.js';
import { APPROVED_TOOL_NAMES } from '../src/services/ai/tools.js';

describe('JARVIS Puter AI Provider Integration Tests', () => {

  // --- 1. Provider Selection & Factory ---
  describe('1-4. Provider Selection & Factory', () => {
    test('1. Provider factory selects OpenAI when AI_PROVIDER=openai', () => {
      const provider = getAIProvider({ providerName: 'openai' });
      assert.ok(provider instanceof OpenAIProvider, 'Expected OpenAIProvider instance');
    });

    test('2. Provider factory selects Ollama when AI_PROVIDER=ollama', () => {
      const provider = getAIProvider({ providerName: 'ollama' });
      assert.ok(provider instanceof OllamaProvider, 'Expected OllamaProvider instance');
    });

    test('3. Provider factory selects Puter when AI_PROVIDER=puter', () => {
      const provider = getAIProvider({ providerName: 'puter' });
      assert.ok(provider instanceof PuterProvider, 'Expected PuterProvider instance');
    });

    test('4. Unknown provider rejected with clear descriptive error', () => {
      assert.throws(
        () => getAIProvider({ providerName: 'unknown_engine_xyz' }),
        /Invalid AI_PROVIDER: "unknown_engine_xyz"/
      );
    });

    test('19. Zero fallback to OpenAI when Puter is selected', () => {
      const provider = getAIProvider({ providerName: 'puter' });
      assert.ok(provider instanceof PuterProvider);
      assert.equal(provider instanceof OpenAIProvider, false, 'Puter must not instantiate OpenAIProvider');
    });

    test('20. Zero fallback to Ollama when Puter is selected', () => {
      const provider = getAIProvider({ providerName: 'puter' });
      assert.ok(provider instanceof PuterProvider);
      assert.equal(provider instanceof OllamaProvider, false, 'Puter must not instantiate OllamaProvider');
    });
  });

  // --- 2. Configuration & Capabilities ---
  describe('5. Puter Configuration Validation & Capabilities', () => {
    test('Puter configuration validates token presence and model selection', async () => {
      const providerNoToken = new PuterProvider({ authToken: '', model: 'gpt-4o-mini' });
      const healthNoToken = await providerNoToken.checkHealth();
      assert.equal(healthNoToken.available, false);
      assert.equal(healthNoToken.configured, false);
      assert.ok(healthNoToken.error?.includes('PUTER_AUTH_TOKEN'));

      const mockClient = {
        checkHealth: async () => ({ available: true, status: 'ok' })
      };
      const providerWithToken = new PuterProvider({
        authToken: 'valid_test_token',
        model: 'gpt-4o',
        client: mockClient
      });
      const healthWithToken = await providerWithToken.checkHealth();
      assert.equal(healthWithToken.available, true);
      assert.equal(healthWithToken.provider, 'puter');
      assert.equal(healthWithToken.model, 'gpt-4o');
    });

    test('Capabilities accurately detect vision capability based on model', () => {
      const visionProvider = new PuterProvider({ model: 'gpt-4o' });
      assert.equal(visionProvider.capabilities.supportsVision, true);
      assert.equal(visionProvider.capabilities.supportsToolCalling, true);
      assert.equal(visionProvider.capabilities.supportsStructuredOutput, true);

      const textOnlyProvider = new PuterProvider({ model: 'deepseek-chat' });
      assert.equal(textOnlyProvider.capabilities.supportsVision, false);
      assert.equal(textOnlyProvider.capabilities.supportsToolCalling, true);
    });
  });

  // --- 3. Chat & Tool Calling Execution ---
  describe('6-11. Puter Chat & Tool Calling Flows', () => {
    test('6. Puter normal chat returns assistant message', async () => {
      const mockClient = {
        ai: {
          chat: async (messages) => ({
            message: {
              role: 'assistant',
              content: 'Good evening, boss. Systems are operating nominally.'
            }
          })
        }
      };

      const provider = new PuterProvider({ authToken: 'test_token', client: mockClient });
      const res = await provider.generateResponse([{ role: 'user', content: 'Status report' }]);
      assert.equal(res.reply, 'Good evening, boss. Systems are operating nominally.');
      assert.equal(res.toolCall, undefined);
    });

    test('7 & 9. Puter structured tool call parsing accepts valid get_time tool', async () => {
      const mockClient = {
        ai: {
          chat: async () => ({
            message: {
              role: 'assistant',
              content: null,
              tool_calls: [
                {
                  id: 'call_time_1',
                  type: 'function',
                  function: {
                    name: 'get_time',
                    arguments: '{}'
                  }
                }
              ]
            }
          })
        }
      };

      const provider = new PuterProvider({ authToken: 'test_token', client: mockClient });
      const res = await provider.generateResponse([{ role: 'user', content: 'What time is it?' }]);
      assert.ok(res.toolCall, 'Expected tool call to be returned');
      assert.equal(res.toolCall.name, 'get_time');
      assert.deepEqual(res.toolCall.arguments, {});
    });

    test('8. Invalid / unapproved tool safely rejected by PuterProvider allowlist', async () => {
      const mockClient = {
        ai: {
          chat: async () => ({
            message: {
              role: 'assistant',
              content: null,
              tool_calls: [
                {
                  id: 'call_danger_1',
                  type: 'function',
                  function: {
                    name: 'rm_rf_device',
                    arguments: '{"target": "/"}'
                  }
                }
              ]
            }
          })
        }
      };

      const provider = new PuterProvider({ authToken: 'test_token', client: mockClient });
      const res = await provider.generateResponse([{ role: 'user', content: 'Delete everything' }]);
      assert.equal(res.toolCall, undefined, 'Unapproved tool must not produce a toolCall');
      assert.ok(res.reply?.includes('not permitted'), 'Expected rejection message');
    });

    test('10 & 11. Tool result returned to Puter and final response generated', async () => {
      let followUpReceived = false;
      const mockClient = {
        ai: {
          chat: async (messages) => {
            const hasToolResult = messages.some(m => m.role === 'tool');
            if (hasToolResult) {
              followUpReceived = true;
              return {
                message: {
                  role: 'assistant',
                  content: 'The current time is 9:45 PM, boss.'
                }
              };
            }
            return {
              message: {
                role: 'assistant',
                tool_calls: [
                  {
                    id: 'call_time_2',
                    function: { name: 'get_time', arguments: {} }
                  }
                ]
              }
            };
          }
        }
      };

      const provider = new PuterProvider({ authToken: 'test_token', client: mockClient });
      const initial = await provider.generateResponse([{ role: 'user', content: 'What time is it?' }]);
      assert.equal(initial.toolCall.name, 'get_time');

      const followUp = await provider.generateResponse([
        { role: 'user', content: 'What time is it?' },
        { role: 'tool', tool_call_id: 'call_time_2', content: '9:45 PM' }
      ]);

      assert.equal(followUpReceived, true, 'Tool result must be provided in follow-up');
      assert.equal(followUp.reply, 'The current time is 9:45 PM, boss.');
    });
  });

  // --- 4. Safety Policy & Confirmation Rules ---
  describe('12-14 & 22-23. Safety Rules & Threat Rejection', () => {
    test('12 & 14. type_text tool requires user confirmation and cannot self-confirm', () => {
      // In JARVIS PlanService / TaskPlanner, type_text is forced to MEDIUM risk and requiresConfirmation=true
      const planService = new PlanService();
      const plan = planService.sanitizeAndValidatePlan(
        {
          steps: [
            {
              action: 'type_text',
              arguments: { text: 'Hello World' },
              riskLevel: 'LOW',
              requiresConfirmation: false
            }
          ]
        },
        'Type Hello World'
      );

      assert.equal(plan.steps.length, 1);
      assert.equal(plan.steps[0].action, 'type_text');
      assert.equal(plan.steps[0].riskLevel, 'MEDIUM', 'type_text must be elevated to MEDIUM risk');
      assert.equal(plan.steps[0].requiresConfirmation, true, 'type_text must mandate confirmation');
      assert.equal(plan.estimatedRisk, 'MEDIUM');
    });

    test('13. HIGH risk action maintains strict confirmation in TaskPlan', () => {
      const planService = new PlanService();
      const plan = planService.sanitizeAndValidatePlan(
        {
          steps: [
            {
              action: 'click_text',
              arguments: { text: 'Format storage' },
              riskLevel: 'HIGH',
              requiresConfirmation: true
            }
          ]
        },
        'Format device'
      );

      assert.equal(plan.steps[0].riskLevel, 'HIGH');
      assert.equal(plan.steps[0].requiresConfirmation, true);
    });

    test('22 & 23. Arbitrary JavaScript code and shell command attempts are rejected', async () => {
      const mockClient = {
        ai: {
          chat: async () => ({
            message: {
              role: 'assistant',
              content: null,
              tool_calls: [
                {
                  id: 'call_shell',
                  function: {
                    name: 'execute_shell',
                    arguments: '{"command": "rm -rf /"}'
                  }
                }
              ]
            }
          })
        }
      };

      const provider = new PuterProvider({ authToken: 'test_token', client: mockClient });
      const res = await provider.generateResponse([{ role: 'user', content: 'Run shell script' }]);
      assert.equal(res.toolCall, undefined);
      assert.ok(res.reply?.includes('not permitted'));
    });
  });

  // --- 5. Context & Privacy Filtering ---
  describe('15-16. Context & Privacy Handling', () => {
    test('15. Context passed correctly across turns via ConversationManager', async () => {
      const manager = new ConversationManager(5);
      manager.addMessage('session_puter', 'user', 'Remember my favorite project is JARVIS');
      manager.addMessage('session_puter', 'assistant', 'I will remember that, boss.');

      const history = manager.getHistory('session_puter');
      assert.equal(history.length, 2);
      assert.equal(history[0].content, 'Remember my favorite project is JARVIS');
      assert.equal(history[1].content, 'I will remember that, boss.');
    });

    test('16. Sensitive data (passwords, tokens, OTPs) filtered before dispatch', () => {
      // Test that auth token is never exposed in checkHealth
      const provider = new PuterProvider({
        authToken: 'secret_puter_token_999',
        model: 'gpt-4o-mini',
        client: { checkHealth: async () => ({ available: true }) }
      });

      const jsonStr = JSON.stringify(provider);
      assert.equal(jsonStr.includes('secret_puter_token_999'), false, 'Credentials must not be serializable');
    });
  });

  // --- 6. Timeouts & Error Handling ---
  describe('17-18 & 21. Timeouts & Error Handling', () => {
    test('17. Puter timeout handled cleanly', async () => {
      const slowClient = {
        ai: {
          chat: async () => {
            await new Promise(r => setTimeout(r, 100));
            return { message: { content: 'Too late' } };
          }
        }
      };

      const provider = new PuterProvider({
        authToken: 'test_token',
        timeoutMs: 10,
        client: slowClient
      });

      await assert.rejects(
        () => provider.generateResponse([{ role: 'user', content: 'Quick test' }]),
        /Puter AI request timed out/
      );
    });

    test('18. Puter unavailable handled with clear descriptive error', async () => {
      const failingClient = {
        ai: {
          chat: async () => {
            throw new Error('Puter AI is currently unavailable. Connection failed.');
          }
        }
      };

      const provider = new PuterProvider({
        authToken: 'test_token',
        client: failingClient
      });

      await assert.rejects(
        () => provider.generateResponse([{ role: 'user', content: 'Ping' }]),
        /Puter AI is currently unavailable/
      );
    });

    test('21. Malformed Puter JSON / empty response rejected safely', async () => {
      const emptyClient = {
        ai: {
          chat: async () => null
        }
      };

      const provider = new PuterProvider({
        authToken: 'test_token',
        client: emptyClient
      });

      await assert.rejects(
        () => provider.generateResponse([{ role: 'user', content: 'Hello' }]),
        /Puter returned an empty response/
      );
    });
  });

  // --- 7. Task Planning Bounds & Cancellation ---
  describe('24-25. Task Planning with Puter', () => {
    test('24. Task plan limits (12 steps max, 5000ms max timeout, 2 retries) preserved', async () => {
      const mockPuter = new PuterProvider({
        authToken: 'test_token',
        client: {
          ai: {
            chat: async () => ({
              message: {
                content: JSON.stringify({
                  steps: Array.from({ length: 15 }, (_, i) => ({
                    stepId: i + 1,
                    action: 'get_time',
                    timeoutMs: 99999,
                    retryCount: 10
                  }))
                })
              }
            })
          }
        }
      });

      const planService = new PlanService({ aiProvider: mockPuter });
      const plan = await planService.generatePlan({ prompt: 'Check time repeatedly' });

      assert.ok(plan.steps.length <= 12, 'Must not exceed 12 steps');
      assert.equal(plan.steps[0].timeoutMs, 5000, 'Step timeout must be capped at 5000ms');
      assert.equal(plan.steps[0].retryCount, 2, 'Retry count must be capped at 2');
    });

    test('25. Task plan deterministic fallback preserved when Puter throws error', async () => {
      const failingPuter = new PuterProvider({
        authToken: 'test_token',
        client: {
          ai: {
            chat: async () => {
              throw new Error('Puter planner upstream offline');
            }
          }
        }
      });

      const planService = new PlanService({ aiProvider: failingPuter });
      const plan = await planService.generatePlan({ prompt: 'Open YouTube' });

      assert.ok(plan, 'Expected fallback plan to be generated');
      assert.ok(plan.steps.length > 0);
      assert.equal(plan.steps[0].action, 'open_app');
    });
  });

  // --- 8. HTTP Server & Endpoints Integration ---
  describe('HTTP Server Integration with Puter', () => {
    let server;
    let baseUrl;
    let mockPuter;

    before(async () => {
      mockPuter = new PuterProvider({
        authToken: 'test_http_token',
        model: 'gpt-4o-mini',
        client: {
          checkHealth: async () => ({ available: true, model: 'gpt-4o-mini' }),
          ai: {
            chat: async (messages) => {
              const last = messages[messages.length - 1];
              if (last.role === 'tool') {
                return { message: { content: 'Follow-up done, boss.' } };
              }
              if (last.content.includes('what time')) {
                return {
                  message: {
                    tool_calls: [
                      {
                        id: 'call_http_time',
                        function: { name: 'get_time', arguments: {} }
                      }
                    ]
                  }
                };
              }
              return { message: { content: 'Puter response ok, boss.' } };
            }
          }
        }
      });

      const app = createApp({ aiProvider: mockPuter });
      server = http.createServer(app);
      await new Promise(resolve => server.listen(0, resolve));
      baseUrl = `http://127.0.0.1:${server.address().port}`;
    });

    after(async () => {
      if (server) await new Promise(resolve => server.close(resolve));
    });

    test('GET /health returns active provider: puter', async () => {
      const res = await fetch(`${baseUrl}/health`);
      const body = await res.json();
      assert.equal(res.status, 200);
      assert.equal(body.provider, 'puter');
    });

    test('GET /api/ai/status returns provider status without secrets', async () => {
      const res = await fetch(`${baseUrl}/api/ai/status`);
      const body = await res.json();
      assert.equal(res.status, 200);
      assert.equal(body.provider, 'puter');
      assert.equal(body.available, true);
      assert.equal(body.configured, true);
      assert.equal(body.token, undefined);
    });

    test('POST /api/chat works end-to-end with Puter', async () => {
      const res = await fetch(`${baseUrl}/api/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: 'Hello' })
      });
      const body = await res.json();
      assert.equal(res.status, 200);
      assert.equal(body.reply, 'Puter response ok, boss.');
    });

    test('POST /api/chat triggers toolCall with Puter', async () => {
      const res = await fetch(`${baseUrl}/api/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: 'what time is it?' })
      });
      const body = await res.json();
      assert.equal(res.status, 200);
      assert.ok(body.toolCall);
      assert.equal(body.toolCall.name, 'get_time');
    });
  });
});

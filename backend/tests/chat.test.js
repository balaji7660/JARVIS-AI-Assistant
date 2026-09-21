import { test, describe, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createApp } from '../src/app.js';
import { AIProvider } from '../src/services/ai/AIProvider.js';
import { ConversationManager } from '../src/services/conversationManager.js';
import { JARVIS_TOOLS, APPROVED_TOOL_NAMES } from '../src/services/ai/tools.js';

class MockAIProvider extends AIProvider {
  constructor(handler) {
    super();
    this.handler = handler || (async (messages) => 'Mocked JARVIS response');
    this.receivedMessages = [];
  }

  async generateResponse(messages) {
    this.receivedMessages.push(messages);
    return this.handler(messages);
  }
}

describe('JARVIS Backend Chat API Tests', () => {
  let server;
  let baseUrl;
  let mockProvider;
  let conversationManager;

  before(async () => {
    mockProvider = new MockAIProvider(async (messages) => {
      const last = messages[messages.length - 1];
      const content = typeof last.content === 'string' ? last.content : '';

      if (last.role === 'tool') {
        return 'The current time is 5:30 PM, boss.';
      }
      if (content.includes('What time is it')) {
        return {
          reply: null,
          toolCall: {
            id: 'call_12345',
            name: 'get_time',
            arguments: {}
          }
        };
      }
      if (content.includes('Open https://example.com')) {
        return {
          reply: null,
          toolCall: {
            id: 'call_67890',
            name: 'open_url',
            arguments: { url: 'https://example.com' }
          }
        };
      }
      if (content.includes('My name is Balaji')) {
        return 'Nice to meet you, boss.';
      }
      if (content.includes('What is my name')) {
        const found = messages.some(m => typeof m.content === 'string' && m.content.includes('My name is Balaji'));
        return found ? 'Your name is Balaji.' : 'I do not know your name yet.';
      }
      if (content === 'trigger_error') {
        throw new Error('Simulated upstream failure');
      }
      if (content === 'trigger_key_missing') {
        throw new Error('OPENAI_API_KEY is not configured');
      }
      return 'Hello boss. I am ready.';
    });

    conversationManager = new ConversationManager(10);
    const app = createApp({
      aiProvider: mockProvider,
      conversationManager
    });

    server = http.createServer(app);
    await new Promise((resolve) => server.listen(0, resolve));
    const port = server.address().port;
    baseUrl = `http://127.0.0.1:${port}`;
  });

  after(async () => {
    if (server) {
      await new Promise((resolve) => server.close(resolve));
    }
  });

  test('GET /health returns status ok', async () => {
    const res = await fetch(`${baseUrl}/health`);
    assert.equal(res.status, 200);
    const data = await res.json();
    assert.equal(data.status, 'ok');
  });

  test('POST /api/chat with valid message returns AI reply', async () => {
    const res = await fetch(`${baseUrl}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: 'Hello Jarvis' })
    });

    assert.equal(res.status, 200);
    const data = await res.json();
    assert.equal(data.reply, 'Hello boss. I am ready.');
  });

  test('POST /api/chat with empty message returns 400', async () => {
    const res = await fetch(`${baseUrl}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: '   ' })
    });

    assert.equal(res.status, 400);
    const data = await res.json();
    assert.match(data.error, /Message is required/);
  });

  test('POST /api/chat with missing body returns 400', async () => {
    const res = await fetch(`${baseUrl}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({})
    });

    assert.equal(res.status, 400);
    const data = await res.json();
    assert.match(data.error, /Message is required/);
  });

  test('POST /api/chat returns structured toolCall when tool intent requested', async () => {
    const res = await fetch(`${baseUrl}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: 'What time is it?' })
    });

    assert.equal(res.status, 200);
    const data = await res.json();
    assert.ok(data.toolCall);
    assert.equal(data.toolCall.name, 'get_time');
    assert.equal(data.toolCall.id, 'call_12345');
  });

  test('POST /api/chat handles toolResult follow-up turn', async () => {
    const sessionId = 'test-tool-turn';
    const res = await fetch(`${baseUrl}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        sessionId,
        toolResult: { success: true, message: '17:30', data: { time: '5:30 PM' } },
        toolCallId: 'call_12345',
        toolName: 'get_time'
      })
    });

    assert.equal(res.status, 200);
    const data = await res.json();
    assert.equal(data.reply, 'The current time is 5:30 PM, boss.');
  });

  test('Approved tool registry contains all 13 base, accessibility, vision, and planning tools', () => {
    const expected = [
      'get_time',
      'get_date',
      'go_home',
      'press_back',
      'open_url',
      'open_app',
      'read_visible_screen',
      'click_text',
      'click_view',
      'type_text',
      'scroll',
      'analyze_current_screen',
      'wait_for_screen'
    ];
    assert.equal(JARVIS_TOOLS.length, 13);
    for (const name of expected) {
      assert.ok(APPROVED_TOOL_NAMES.has(name), `Missing approved tool: ${name}`);
    }
  });

  test('POST /api/chat remembers conversation context across turns', async () => {
    const sessionId = 'test-session-memory';

    // Turn 1: Tell JARVIS name
    const res1 = await fetch(`${baseUrl}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId, message: 'My name is Balaji.' })
    });
    assert.equal(res1.status, 200);
    const data1 = await res1.json();
    assert.equal(data1.reply, 'Nice to meet you, boss.');

    // Turn 2: Ask JARVIS what the name is
    const res2 = await fetch(`${baseUrl}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId, message: 'What is my name?' })
    });
    assert.equal(res2.status, 200);
    const data2 = await res2.json();
    assert.equal(data2.reply, 'Your name is Balaji.');
  });

  test('POST /api/chat handles missing API key configuration with 503', async () => {
    const res = await fetch(`${baseUrl}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: 'trigger_key_missing' })
    });

    assert.equal(res.status, 503);
    const data = await res.json();
    assert.match(data.error, /not yet configured/);
  });

  test('POST /api/chat handles upstream AI errors gracefully with 500', async () => {
    const res = await fetch(`${baseUrl}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: 'trigger_error' })
    });

    assert.equal(res.status, 500);
    const data = await res.json();
    assert.match(data.error, /trouble connecting/);
  });
});

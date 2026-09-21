import { test, describe, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createApp } from '../src/app.js';
import { PlanService } from '../src/services/ai/PlanService.js';

class MockPlanService extends PlanService {
  constructor(mockResult = null, shouldThrow = false) {
    super({ client: {} });
    this.mockResult = mockResult;
    this.shouldThrow = shouldThrow;
  }

  async generatePlan({ prompt, memoryContext, currentPackage, visibleScreenText, context }) {
    if (this.shouldThrow) {
      throw new Error('Upstream planner model failure');
    }
    if (this.mockResult) {
      return this.mockResult;
    }
    return super.generateDeterministicPlan(prompt, { memoryContext, currentPackage, visibleScreenText, context });
  }
}

describe('JARVIS Backend Task Planning API Tests', () => {
  let server;
  let baseUrl;
  let mockPlanService;

  before(async () => {
    mockPlanService = new MockPlanService();
    const app = createApp({ planService: mockPlanService });
    server = http.createServer(app);
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    const port = server.address().port;
    baseUrl = `http://127.0.0.1:${port}`;
  });

  after(async () => {
    if (server) {
      await new Promise((resolve) => server.close(resolve));
    }
  });

  test('POST /api/plan with empty prompt returns 400', async () => {
    const res = await fetch(`${baseUrl}/api/plan`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt: '' })
    });

    const body = await res.json();
    assert.strictEqual(res.status, 400);
    assert.strictEqual(body.error, 'Bad Request');
  });

  test('POST /api/plan returns valid structured task plan for single-step prompt', async () => {
    const res = await fetch(`${baseUrl}/api/plan`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt: 'What time is it?' })
    });

    const body = await res.json();
    assert.strictEqual(res.status, 200);
    assert.strictEqual(body.type, 'task_plan');
    assert.ok(Array.isArray(body.steps));
    assert.strictEqual(body.steps[0].action, 'get_time');
    assert.strictEqual(body.steps[0].riskLevel, 'LOW');
    assert.strictEqual(body.steps[0].requiresConfirmation, false);
  });

  test('POST /api/plan returns multi-step plan with confirmation for typing', async () => {
    const res = await fetch(`${baseUrl}/api/plan`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt: 'Open YouTube and search for Java Spring Boot tutorials' })
    });

    const body = await res.json();
    assert.strictEqual(res.status, 200);
    assert.strictEqual(body.type, 'task_plan');
    assert.ok(body.steps.length >= 3);
    
    // Check open_app step
    const openStep = body.steps.find(s => s.action === 'open_app');
    assert.ok(openStep);
    assert.strictEqual(openStep.arguments.appName, 'YouTube');

    // Check type_text step has MEDIUM risk and requiresConfirmation
    const typeStep = body.steps.find(s => s.action === 'type_text');
    assert.ok(typeStep);
    assert.strictEqual(typeStep.riskLevel, 'MEDIUM');
    assert.strictEqual(typeStep.requiresConfirmation, true);
    assert.strictEqual(body.requiresConfirmation, true);
  });

  test('POST /api/plan handles upstream planner service error gracefully', async () => {
    const errorService = new MockPlanService(null, true);
    const errorApp = createApp({ planService: errorService });
    const errServer = http.createServer(errorApp);
    await new Promise((resolve) => errServer.listen(0, '127.0.0.1', resolve));
    const errUrl = `http://127.0.0.1:${errServer.address().port}`;

    try {
      const res = await fetch(`${errUrl}/api/plan`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt: 'Open Settings' })
      });

      const body = await res.json();
      assert.strictEqual(res.status, 500);
      assert.strictEqual(body.error, 'Internal Server Error');
    } finally {
      await new Promise((resolve) => errServer.close(resolve));
    }
  });

  test('POST /api/plan rejects non-object context with 400', async () => {
    const res = await fetch(`${baseUrl}/api/plan`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        prompt: 'Search Spring Boot',
        context: 'invalid_string_context'
      })
    });

    const body = await res.json();
    assert.strictEqual(res.status, 400);
    assert.strictEqual(body.error, 'Bad Request');
    assert.ok(body.message.includes('must be an object'));
  });

  test('POST /api/plan rejects context exceeding MAX_CONTEXT_TEXT_LENGTH with 400', async () => {
    const hugeSummary = 'x'.repeat(4500);
    const res = await fetch(`${baseUrl}/api/plan`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        prompt: 'Search Spring Boot',
        context: {
          screenSummary: hugeSummary
        }
      })
    });

    const body = await res.json();
    assert.strictEqual(res.status, 400);
    assert.strictEqual(body.error, 'Bad Request');
    assert.ok(body.message.includes('exceeds maximum limit'));
  });

  test('POST /api/plan understands follow-up search in YouTube context', async () => {
    const res = await fetch(`${baseUrl}/api/plan`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        prompt: 'Search Spring Boot',
        context: {
          currentApp: 'YouTube',
          currentPackage: 'com.google.android.youtube'
        }
      })
    });

    const body = await res.json();
    assert.strictEqual(res.status, 200);
    assert.strictEqual(body.type, 'task_plan');
    assert.ok(body.steps.length >= 2);
    assert.strictEqual(body.steps[0].action, 'click_text');
    assert.strictEqual(body.steps[0].arguments.text, 'Search');
    assert.strictEqual(body.steps[1].action, 'type_text');
    assert.strictEqual(body.steps[1].arguments.text, 'Spring Boot');
    assert.strictEqual(body.steps[1].riskLevel, 'MEDIUM');
    assert.strictEqual(body.steps[1].requiresConfirmation, true);
  });

  test('POST /api/plan understands "Open the first result" with context', async () => {
    const res = await fetch(`${baseUrl}/api/plan`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        prompt: 'Open the first result',
        context: {
          currentApp: 'YouTube',
          currentTarget: 'Spring Boot 3 Crash Course'
        }
      })
    });

    const body = await res.json();
    assert.strictEqual(res.status, 200);
    assert.strictEqual(body.type, 'task_plan');
    assert.strictEqual(body.steps[0].action, 'click_text');
    assert.strictEqual(body.steps[0].arguments.text, 'Spring Boot 3 Crash Course');
  });
});

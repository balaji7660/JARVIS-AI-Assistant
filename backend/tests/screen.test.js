import test, { describe, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createApp } from '../src/app.js';
import { AIProvider } from '../src/services/ai/AIProvider.js';

class MockScreenAIProvider extends AIProvider {
  constructor() {
    super();
    this.shouldThrow = false;
  }

  async generateResponse() {
    return 'Mock chat response';
  }

  async analyzeScreen({ packageName, screenWidth, screenHeight, accessibilityElements = [], image = null, focus = null }) {
    if (this.shouldThrow) {
      throw new Error('Upstream vision analysis error');
    }

    let relevantElement = null;
    if (focus) {
      relevantElement = {
        label: focus,
        type: 'button',
        bounds: [100, 200, 300, 400],
        resourceId: 'com.jarvis.assistant:id/focus_btn',
        text: focus,
        confidence: 0.95
      };
    }

    return {
      success: true,
      summary: `Analyzed screen of ${packageName || 'app'}.`,
      detectedElements: accessibilityElements.map(e => ({
        label: e.text || e.viewId || 'element',
        type: 'button',
        bounds: [0, 0, screenWidth, screenHeight],
        resourceId: e.viewId || null,
        text: e.text || null,
        confidence: 0.9
      })),
      relevantElement,
      confidence: 0.9,
      suggestedAction: relevantElement ? 'click' : 'none'
    };
  }
}

describe('JARVIS Backend Screen Analysis API Tests', () => {
  let server;
  let baseUrl;
  let mockProvider;

  before(async () => {
    mockProvider = new MockScreenAIProvider();
    const app = createApp({ aiProvider: mockProvider });
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

  test('POST /api/screen/analyze with valid payload returns structured analysis result', async () => {
    const res = await fetch(`${baseUrl}/api/screen/analyze`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        packageName: 'com.android.settings',
        screenWidth: 1080,
        screenHeight: 2400,
        accessibilityElements: [
          { text: 'Wi-Fi', viewId: 'wifi_row', clickable: true },
          { text: 'Bluetooth', viewId: 'bt_row', clickable: true }
        ],
        image: 'data:image/jpeg;base64,ZmFrZWltYWdlZGF0YQ=='
      })
    });

    assert.equal(res.status, 200);
    const data = await res.json();
    assert.equal(data.success, true);
    assert.ok(data.summary.includes('com.android.settings'));
    assert.equal(data.detectedElements.length, 2);
    assert.equal(data.confidence, 0.9);
  });

  test('POST /api/screen/analyze with focus returns relevant target element', async () => {
    const res = await fetch(`${baseUrl}/api/screen/analyze`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        packageName: 'com.android.settings',
        screenWidth: 1080,
        screenHeight: 2400,
        accessibilityElements: [{ text: 'Settings' }],
        focus: 'Settings'
      })
    });

    assert.equal(res.status, 200);
    const data = await res.json();
    assert.equal(data.success, true);
    assert.ok(data.relevantElement);
    assert.equal(data.relevantElement.label, 'Settings');
    assert.equal(data.suggestedAction, 'click');
  });

  test('POST /api/screen/analyze rejects missing required fields with 400', async () => {
    const res = await fetch(`${baseUrl}/api/screen/analyze`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        packageName: 'com.android.settings'
      })
    });

    assert.equal(res.status, 400);
    const data = await res.json();
    assert.ok(data.error.includes('screenWidth'));
  });

  test('POST /api/screen/analyze rejects oversized image payload (> 5MB)', async () => {
    const oversizedBase64 = 'A'.repeat(5 * 1024 * 1024 + 10);
    const res = await fetch(`${baseUrl}/api/screen/analyze`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        screenWidth: 1080,
        screenHeight: 2400,
        accessibilityElements: [],
        image: oversizedBase64
      })
    });

    assert.equal(res.status, 400);
    const data = await res.json();
    assert.ok(data.error.includes('exceeds maximum limit'));
  });

  test('POST /api/screen/analyze handles provider failure gracefully with 500', async () => {
    mockProvider.shouldThrow = true;
    const res = await fetch(`${baseUrl}/api/screen/analyze`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        screenWidth: 1080,
        screenHeight: 2400,
        accessibilityElements: []
      })
    });
    mockProvider.shouldThrow = false;

    assert.equal(res.status, 500);
    const data = await res.json();
    assert.equal(data.error, 'Failed to analyze screen.');
  });
});

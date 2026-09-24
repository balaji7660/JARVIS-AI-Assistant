import express from 'express';
import cors from 'cors';
import { createChatRouter } from './routes/chatRoutes.js';
import { createPlanRouter } from './routes/planRoutes.js';
import { createScreenRouter } from './routes/screenRoutes.js';
import { createOllamaRouter } from './routes/ollamaRoutes.js';
import { getAIProvider, getActiveProviderName } from './services/ai/providerFactory.js';
import { PlanService } from './services/ai/PlanService.js';
import { ConversationManager } from './services/conversationManager.js';
import { OllamaProvider } from './services/ai/OllamaProvider.js';
import { PuterProvider } from './services/ai/PuterProvider.js';

export function createApp({
  aiProvider = null,
  planService = null,
  conversationManager = new ConversationManager(10)
} = {}) {
  const app = express();

  const resolvedAiProvider = aiProvider || getAIProvider();
  const resolvedPlanService = planService || new PlanService({ aiProvider: resolvedAiProvider });
  const activeProviderName = resolvedAiProvider instanceof PuterProvider
    ? 'puter'
    : (resolvedAiProvider instanceof OllamaProvider ? 'ollama' : getActiveProviderName());

  app.use(cors());
  app.use(express.json({ limit: '10mb' }));

  // Root route
  app.get('/', (req, res) => {
    if (req.headers.accept && req.headers.accept.includes('text/html')) {
      res.setHeader('Content-Type', 'text/html');
      return res.send(`<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>JARVIS AI Assistant Backend</title>
  <style>
    body {
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      background: #0d1117;
      color: #c9d1d9;
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      padding: 24px;
      box-sizing: border-box;
    }
    .card {
      background: #161b22;
      border: 1px solid #30363d;
      border-radius: 16px;
      padding: 32px;
      max-width: 520px;
      width: 100%;
      box-shadow: 0 12px 32px rgba(0, 0, 0, 0.5);
    }
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      background: rgba(46, 160, 67, 0.15);
      color: #3fb950;
      padding: 6px 14px;
      border-radius: 999px;
      font-size: 13px;
      font-weight: 600;
      margin-bottom: 20px;
      border: 1px solid rgba(46, 160, 67, 0.3);
    }
    .badge.local {
      background: rgba(88, 166, 255, 0.15);
      color: #58a6ff;
      border: 1px solid rgba(88, 166, 255, 0.3);
    }
    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #3fb950;
      box-shadow: 0 0 10px #3fb950;
    }
    .dot.local {
      background: #58a6ff;
      box-shadow: 0 0 10px #58a6ff;
    }
    h1 {
      margin: 0 0 8px;
      font-size: 26px;
      font-weight: 700;
      color: #f0f6fc;
      letter-spacing: -0.5px;
    }
    p {
      margin: 0 0 24px;
      color: #8b949e;
      line-height: 1.6;
      font-size: 15px;
    }
    .endpoints {
      background: #0d1117;
      border: 1px solid #21262d;
      border-radius: 10px;
      padding: 16px;
    }
    .endpoints-title {
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      color: #8b949e;
      margin-bottom: 12px;
    }
    .endpoint {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 10px 0;
      border-bottom: 1px solid #21262d;
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 13px;
    }
    .endpoint:last-child {
      border-bottom: none;
    }
    .method {
      display: inline-block;
      padding: 2px 6px;
      border-radius: 4px;
      font-size: 11px;
      font-weight: bold;
      margin-right: 8px;
    }
    .method.get {
      background: rgba(56, 139, 253, 0.15);
      color: #58a6ff;
    }
    .method.post {
      background: rgba(46, 160, 67, 0.15);
      color: #3fb950;
    }
    a {
      color: #58a6ff;
      text-decoration: none;
    }
    a:hover {
      text-decoration: underline;
    }
    .desc {
      color: #8b949e;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      font-size: 12px;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="badge ${activeProviderName === 'ollama' ? 'local' : (activeProviderName === 'puter' ? 'local' : '')}">
      <span class="dot ${activeProviderName === 'ollama' ? 'local' : (activeProviderName === 'puter' ? 'local' : '')}"></span>
      Active Engine: ${activeProviderName === 'ollama' ? 'Ollama Free Local AI' : (activeProviderName === 'puter' ? 'Puter AI Platform' : 'OpenAI Cloud')}
    </div>
    <h1>JARVIS AI Backend</h1>
    <p>Connected and ready to bridge requests from your JARVIS mobile assistant.</p>
    <div class="endpoints">
      <div class="endpoints-title">Available Endpoints</div>
      <div class="endpoint">
        <span><span class="method get">GET</span><a href="/health">/health</a></span>
        <span class="desc">Health Check</span>
      </div>
      <div class="endpoint">
        <span><span class="method get">GET</span><a href="/api/ollama/status">/api/ollama/status</a></span>
        <span class="desc">Ollama Local Status</span>
      </div>
      <div class="endpoint">
        <span><span class="method post">POST</span>/api/chat</span>
        <span class="desc">Chat &amp; Tools</span>
      </div>
      <div class="endpoint">
        <span><span class="method post">POST</span>/api/plan</span>
        <span class="desc">Task Planner</span>
      </div>
      <div class="endpoint">
        <span><span class="method post">POST</span>/api/screen/analyze</span>
        <span class="desc">Screen Vision</span>
      </div>
    </div>
  </div>
</body>
</html>`);
    }

    res.status(200).json({
      status: 'ok',
      service: 'JARVIS AI Assistant Backend',
      activeProvider: activeProviderName,
      message: 'JARVIS backend server is running and ready to receive requests.',
      endpoints: {
        health: '/health',
        aiStatus: '/api/ai/status',
        ollamaStatus: '/api/ollama/status',
        chat: '/api/chat',
        plan: '/api/plan',
        screen: '/api/screen/analyze'
      }
    });
  });

  // Health check
  app.get('/health', (req, res) => {
    res.status(200).json({
      status: 'ok',
      service: 'jarvis-backend',
      provider: activeProviderName
    });
  });

  // Unified AI status endpoint
  app.get('/api/ai/status', async (req, res) => {
    try {
      const health = await resolvedAiProvider.checkHealth();
      return res.status(200).json({
        provider: activeProviderName,
        configured: health.configured !== undefined ? health.configured : Boolean(health.available),
        available: Boolean(health.available),
        model: health.model || undefined,
        ...(health.error ? { error: health.error } : {})
      });
    } catch (err) {
      return res.status(200).json({
        provider: activeProviderName,
        configured: false,
        available: false,
        error: err.message
      });
    }
  });

  // Chat API router
  app.use('/api', createChatRouter(resolvedAiProvider, conversationManager));

  // Plan API router (Milestone 9)
  app.use('/api', createPlanRouter(resolvedPlanService));

  // Screen Analysis API router
  app.use('/api/screen', createScreenRouter(resolvedAiProvider));

  // Ollama status router
  app.use('/api/ollama', createOllamaRouter(resolvedAiProvider instanceof OllamaProvider ? resolvedAiProvider : undefined));

  return app;
}

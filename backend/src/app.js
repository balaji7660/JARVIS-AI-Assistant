import express from 'express';
import cors from 'cors';
import { createChatRouter } from './routes/chatRoutes.js';
import { createPlanRouter } from './routes/planRoutes.js';
import { createScreenRouter } from './routes/screenRoutes.js';
import { OpenAIProvider } from './services/ai/OpenAIProvider.js';
import { PlanService } from './services/ai/PlanService.js';
import { ConversationManager } from './services/conversationManager.js';

export function createApp({
  aiProvider = new OpenAIProvider(),
  planService = new PlanService(),
  conversationManager = new ConversationManager(10)
} = {}) {
  const app = express();

  app.use(cors());
  app.use(express.json({ limit: '10mb' }));

  // Health check
  app.get('/health', (req, res) => {
    res.status(200).json({ status: 'ok', service: 'jarvis-backend' });
  });

  // Chat API router
  app.use('/api', createChatRouter(aiProvider, conversationManager));

  // Plan API router (Milestone 9)
  app.use('/api', createPlanRouter(planService));

  // Screen Analysis API router
  app.use('/api/screen', createScreenRouter(aiProvider));

  return app;
}

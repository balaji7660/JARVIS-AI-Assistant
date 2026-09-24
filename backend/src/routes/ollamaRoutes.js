import { Router } from 'express';
import { OllamaProvider } from '../services/ai/OllamaProvider.js';

/**
 * Creates Ollama health and management routes
 * @param {OllamaProvider} [ollamaProvider]
 */
export function createOllamaRouter(ollamaProvider = null) {
  const router = Router();
  const provider = ollamaProvider || new OllamaProvider();

  /**
   * GET /api/ollama/status
   * Safe public status check for local Ollama daemon.
   * Does NOT expose environment variables, tokens, or filesystem paths.
   */
  router.get('/status', async (req, res) => {
    try {
      const health = await provider.checkHealth();
      return res.status(200).json(health);
    } catch (err) {
      return res.status(200).json({
        available: false,
        provider: 'ollama',
        model: provider.model,
        error: 'Local AI is unavailable. Start Ollama and try again.'
      });
    }
  });

  return router;
}

import { Router } from 'express';

const MAX_IMAGE_BASE64_BYTES = 5 * 1024 * 1024; // 5 MB max payload

/**
 * Creates screen analysis routes with injected AIProvider
 * @param {import('../services/ai/AIProvider.js').AIProvider} aiProvider
 */
export function createScreenRouter(aiProvider) {
  const router = Router();

  router.post('/analyze', async (req, res) => {
    try {
      const {
        packageName = null,
        screenWidth,
        screenHeight,
        accessibilityElements = [],
        image = null,
        focus = null
      } = req.body || {};

      // 1. Validate required fields
      if (typeof screenWidth !== 'number' || screenWidth <= 0) {
        return res.status(400).json({ error: 'screenWidth must be a positive number.' });
      }
      if (typeof screenHeight !== 'number' || screenHeight <= 0) {
        return res.status(400).json({ error: 'screenHeight must be a positive number.' });
      }
      if (!Array.isArray(accessibilityElements)) {
        return res.status(400).json({ error: 'accessibilityElements must be an array.' });
      }

      // 2. Validate image payload limit (ephemeral, in-memory)
      if (image && typeof image === 'string') {
        if (image.length > MAX_IMAGE_BASE64_BYTES) {
          return res.status(400).json({ error: 'Image payload exceeds maximum limit of 5MB.' });
        }
      }

      // 3. Delegate to AI provider (zero disk persistence)
      const result = await aiProvider.analyzeScreen({
        packageName,
        screenWidth,
        screenHeight,
        accessibilityElements,
        image,
        focus
      });

      return res.status(200).json(result);
    } catch (err) {
      console.error('[Screen Analysis API Error]:', err.message);
      return res.status(500).json({
        error: 'Failed to analyze screen.',
        details: err.message
      });
    }
  });

  return router;
}

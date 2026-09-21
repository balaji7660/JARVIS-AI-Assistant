import { Router } from 'express';
import { PlanService } from '../services/ai/PlanService.js';

const MAX_CONTEXT_TEXT_LENGTH = 4000;
const MAX_RECENT_ENTITIES = 20;

function sanitizeSensitiveText(text) {
  if (typeof text !== 'string') return text;
  return text
    .replace(/\b(?:\d[ -]*?){13,16}\b/g, '[REDACTED_CARD]')
    .replace(/\b(otp|one[- ]time password|verification code|security code|passcode)\b.*?(?:is|:)?\s*([0-9]{4,8})/gi, '[REDACTED_OTP]')
    .replace(/\b(?:otp|passcode)\s*[:=]?\s*([0-9]{4,8})\b/gi, '[REDACTED_OTP]')
    .replace(/\b(enter pin|atm pin|secret pin|pin)\s*[:=]?\s*([0-9]{4,8})\b/gi, '[REDACTED_PIN]')
    .replace(/\b(password|passwd|pwd)\s*(?:is|[:=])\s*\S+/gi, '[REDACTED_PASSWORD]')
    .replace(/\b(bearer\s+[A-Za-z0-9\-_=.]+)|(ghp_[A-Za-z0-9]{20,})|(eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,})/gi, '[REDACTED_TOKEN]');
}

export function createPlanRouter(planService = new PlanService()) {
  const router = Router();

  /**
   * POST /api/plan
   * Generates a structured task plan for multi-step device automation.
   */
  router.post('/plan', async (req, res) => {
    try {
      const { prompt, memoryContext, currentPackage, visibleScreenText, context } = req.body || {};

      if (!prompt || typeof prompt !== 'string' || prompt.trim().length === 0) {
        return res.status(400).json({
          error: 'Bad Request',
          message: 'Field "prompt" is required and must be a non-empty string.'
        });
      }

      // Strict context validation
      let validatedContext = null;
      if (context !== undefined && context !== null) {
        if (typeof context !== 'object' || Array.isArray(context)) {
          return res.status(400).json({
            error: 'Bad Request',
            message: 'Field "context" must be an object if provided.'
          });
        }

        // Drop unknown fields & enforce types
        const allowedKeys = new Set(['currentApp', 'currentPackage', 'screenSummary', 'currentTarget', 'lastAction', 'recentEntities']);
        const sanitized = {};
        let totalTextLength = 0;

        for (const [key, value] of Object.entries(context)) {
          if (!allowedKeys.has(key) || value === null || value === undefined) {
            continue;
          }

          if (key === 'recentEntities') {
            if (!Array.isArray(value)) {
              return res.status(400).json({
                error: 'Bad Request',
                message: 'Field "context.recentEntities" must be an array of strings.'
              });
            }
            const boundedEntities = value
              .slice(0, MAX_RECENT_ENTITIES)
              .filter(e => typeof e === 'string')
              .map(e => {
                const s = sanitizeSensitiveText(e.trim());
                totalTextLength += s.length;
                return s;
              });
            sanitized.recentEntities = boundedEntities;
          } else {
            if (typeof value !== 'string') {
              return res.status(400).json({
                error: 'Bad Request',
                message: `Field "context.${key}" must be a string.`
              });
            }
            const s = sanitizeSensitiveText(value.trim());
            totalTextLength += s.length;
            sanitized[key] = s;
          }
        }

        if (totalTextLength > MAX_CONTEXT_TEXT_LENGTH) {
          return res.status(400).json({
            error: 'Bad Request',
            message: `Context text length exceeds maximum limit of ${MAX_CONTEXT_TEXT_LENGTH} characters.`
          });
        }

        validatedContext = sanitized;
      }

      const plan = await planService.generatePlan({
        prompt,
        memoryContext,
        currentPackage,
        visibleScreenText,
        context: validatedContext
      });

      return res.status(200).json(plan);
    } catch (error) {
      console.error('[Plan API Error]:', error.message);

      if (error.message.includes('OPENAI_API_KEY is not configured')) {
        return res.status(503).json({
          error: 'Service Unavailable',
          message: 'AI Task Planner is not configured on this server.'
        });
      }

      return res.status(500).json({
        error: 'Internal Server Error',
        message: 'Failed to generate task plan.',
        details: error.message
      });
    }
  });

  return router;
}

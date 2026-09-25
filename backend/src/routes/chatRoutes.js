import { Router } from 'express';

/**
 * Creates chat routes with injected AIProvider and ConversationManager
 * @param {import('../services/ai/AIProvider.js').AIProvider} aiProvider
 * @param {import('../services/conversationManager.js').ConversationManager} conversationManager
 */
export function createChatRouter(aiProvider, conversationManager) {
  const router = Router();

  router.post('/chat', async (req, res) => {
    const t2 = Date.now();
    try {
      // Safe diagnostic logging (TASK 3)
      console.log('[Chat API Diagnostic]', {
        bodyType: typeof req.body,
        bodyKeys: req.body && typeof req.body === 'object' ? Object.keys(req.body) : [],
        messageType: typeof req.body?.message,
        sessionIdType: typeof req.body?.sessionId,
        clientTimestampType: typeof req.body?.clientTimestamp
      });

      if (!req.body || typeof req.body !== 'object' || Array.isArray(req.body)) {
        return res.status(400).json({
          error: 'Invalid request: Request body must be a JSON object.',
          field: 'body',
          message: 'Request body must be a valid JSON object.'
        });
      }

      const {
        message,
        sessionId = 'default',
        toolResult,
        toolCallId,
        toolName,
        clientTimestamp
      } = req.body;

      // Validate optional fields
      if (sessionId !== undefined && typeof sessionId !== 'string') {
        return res.status(400).json({
          error: 'Invalid request: sessionId must be a string.',
          field: 'sessionId',
          message: 'sessionId must be a string if provided.'
        });
      }

      if (clientTimestamp !== undefined && clientTimestamp !== null && typeof clientTimestamp !== 'number') {
        return res.status(400).json({
          error: 'Invalid request: clientTimestamp must be a number.',
          field: 'clientTimestamp',
          message: 'clientTimestamp must be a number if provided.'
        });
      }

      // 1. Tool execution result follow-up
      if (toolResult !== undefined) {
        const history = conversationManager.getHistory(sessionId);

        // Build tool execution feedback message
        const toolContent = typeof toolResult === 'string' ? toolResult : JSON.stringify(toolResult);
        const messages = [
          ...history,
          {
            role: 'tool',
            tool_call_id: toolCallId || 'call_default',
            content: toolContent
          }
        ];

        const t3 = Date.now();
        const aiResult = await aiProvider.generateResponse(messages);
        const t4 = Date.now();
        const reply = typeof aiResult === 'string' ? aiResult : (aiResult.reply || 'Action completed, boss.');

        conversationManager.addMessage(sessionId, 'tool', toolContent, {
          tool_call_id: toolCallId || 'call_default'
        });
        conversationManager.addMessage(sessionId, 'assistant', reply);

        console.log(`[JARVIS Pipeline] Follow-up Puter Latency: ${t4 - t3}ms | Total Backend: ${Date.now() - t2}ms`);

        return res.status(200).json({
          reply,
          sessionId,
          timing: {
            t2_backendReceivedMs: t2,
            t3_puterStartedMs: t3,
            t4_puterReceivedMs: t4,
            puterLatencyMs: t4 - t3,
            backendProcessingMs: Date.now() - t2
          }
        });
      }

      // 2. Standard user prompt
      if (!message || typeof message !== 'string' || !message.trim()) {
        return res.status(400).json({
          error: 'Message is required and cannot be empty.',
          field: 'message',
          message: 'Message is required and cannot be empty.'
        });
      }

      const trimmedMessage = message.trim();
      const history = conversationManager.getHistory(sessionId);

      const messages = [
        ...history,
        { role: 'user', content: trimmedMessage }
      ];

      const t3 = Date.now();
      const aiResult = await aiProvider.generateResponse(messages);
      const t4 = Date.now();

      let reply = null;
      let toolCall = null;

      if (typeof aiResult === 'string') {
        reply = aiResult;
      } else {
        reply = aiResult.reply;
        toolCall = aiResult.toolCall || null;
      }

      const t5 = toolCall ? Date.now() : null;

      // Record in conversation memory
      conversationManager.addMessage(sessionId, 'user', trimmedMessage);
      if (toolCall) {
        conversationManager.addMessage(sessionId, 'assistant', reply || '', {
          tool_calls: [
            {
              id: toolCall.id,
              type: 'function',
              function: {
                name: toolCall.name,
                arguments: typeof toolCall.arguments === 'object'
                  ? JSON.stringify(toolCall.arguments)
                  : String(toolCall.arguments || '{}')
              }
            }
          ]
        });
      } else if (reply) {
        conversationManager.addMessage(sessionId, 'assistant', reply);
      }

      console.log(`[JARVIS Pipeline] Puter Latency: ${t4 - t3}ms | Total Backend: ${Date.now() - t2}ms${toolCall ? ` | Tool: ${toolCall.name}` : ''}`);

      return res.status(200).json({
        reply,
        toolCall,
        sessionId,
        timing: {
          t2_backendReceivedMs: t2,
          t3_puterStartedMs: t3,
          t4_puterReceivedMs: t4,
          t5_toolDetectedMs: t5,
          puterLatencyMs: t4 - t3,
          backendProcessingMs: Date.now() - t2
        }
      });
    } catch (error) {
      console.error('[Chat API Error]:', error.message);
      if (error.stack) {
        console.error('[Chat API Error Stack]:', error.stack);
      }

      // Puter AI error handling (NEVER silently fall back to OpenAI or Ollama)
      if (error.message?.includes('PUTER_AUTH_TOKEN is not configured')) {
        return res.status(503).json({
          success: false,
          error: {
            code: 'PUTER_AUTH_MISSING',
            message: 'Puter authentication token is not configured on the server.',
            retryable: false
          }
        });
      }

      if (error.code === 'EAUTH' || error.message?.includes('Puter authentication failed')) {
        return res.status(401).json({
          success: false,
          error: {
            code: 'HTTP_401',
            message: 'Puter authentication failed. Please verify PUTER_AUTH_TOKEN.',
            retryable: false
          }
        });
      }

      if (
        error.message?.includes('Puter AI is currently unavailable') ||
        error.message?.includes('Puter connection')
      ) {
        return res.status(503).json({
          success: false,
          error: {
            code: 'PUTER_ERROR',
            message: 'Puter AI is currently unavailable. Please check the Puter connection.',
            retryable: true
          }
        });
      }

      if (error.message?.includes('Puter AI request timed out')) {
        return res.status(504).json({
          success: false,
          error: {
            code: 'PUTER_TIMEOUT',
            message: 'Puter request timed out',
            retryable: true
          }
        });
      }

      // Explicit local AI unavailable handling (NEVER silently fall back to OpenAI)
      if (
        error.message?.includes('Local AI is unavailable') ||
        error.code === 'ECONNREFUSED' ||
        error.message?.includes('ECONNREFUSED')
      ) {
        return res.status(503).json({
          error: 'Local AI is unavailable. Start Ollama and try again.'
        });
      }

      if (error.code === 'ETIMEDOUT' || error.message?.includes('timed out')) {
        return res.status(504).json({
          error: 'AI request timed out. Please try again.'
        });
      }

      if (error.message?.includes('OPENAI_API_KEY')) {
        return res.status(503).json({
          error: 'Boss, my AI credentials are not yet configured on the server.'
        });
      }

      return res.status(500).json({
        error: "Boss, I'm having trouble connecting to my AI service right now."
      });

    }
  });

  return router;
}

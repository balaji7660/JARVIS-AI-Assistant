import { Router } from 'express';

/**
 * Creates chat routes with injected AIProvider and ConversationManager
 * @param {import('../services/ai/AIProvider.js').AIProvider} aiProvider
 * @param {import('../services/conversationManager.js').ConversationManager} conversationManager
 */
export function createChatRouter(aiProvider, conversationManager) {
  const router = Router();

  router.post('/chat', async (req, res) => {
    try {
      const {
        message,
        sessionId = 'default',
        toolResult,
        toolCallId,
        toolName
      } = req.body || {};

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

        const aiResult = await aiProvider.generateResponse(messages);
        const reply = typeof aiResult === 'string' ? aiResult : (aiResult.reply || 'Action completed, boss.');

        conversationManager.addMessage(sessionId, 'tool', toolContent);
        conversationManager.addMessage(sessionId, 'assistant', reply);

        return res.status(200).json({
          reply,
          sessionId
        });
      }

      // 2. Standard user prompt
      if (!message || typeof message !== 'string' || !message.trim()) {
        return res.status(400).json({
          error: 'Message is required and cannot be empty.'
        });
      }

      const trimmedMessage = message.trim();
      const history = conversationManager.getHistory(sessionId);

      const messages = [
        ...history,
        { role: 'user', content: trimmedMessage }
      ];

      const aiResult = await aiProvider.generateResponse(messages);

      let reply = null;
      let toolCall = null;

      if (typeof aiResult === 'string') {
        reply = aiResult;
      } else {
        reply = aiResult.reply;
        toolCall = aiResult.toolCall || null;
      }

      // Record in conversation memory
      conversationManager.addMessage(sessionId, 'user', trimmedMessage);
      if (reply) {
        conversationManager.addMessage(sessionId, 'assistant', reply);
      }

      return res.status(200).json({
        reply,
        toolCall,
        sessionId
      });
    } catch (error) {
      console.error('[Chat API Error]:', error.message);

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

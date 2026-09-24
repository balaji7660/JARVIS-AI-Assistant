import { AIProviderCapabilities } from './AIProviderCapabilities.js';

/**
 * Abstract Base Class for AI Providers (OpenAI, Ollama, etc.)
 */
export class AIProvider {
  /**
   * Returns provider capabilities
   * @returns {AIProviderCapabilities}
   */
  get capabilities() {
    return new AIProviderCapabilities();
  }

  /**
   * Generates an AI response given a conversation message array
   * @param {Array<{role: string, content: string}>} messages
   * @returns {Promise<{reply: string, toolCall?: {id: string, name: string, arguments: Object}}>}
   */
  async generateResponse(messages) {
    throw new Error('generateResponse(messages) must be implemented by subclass.');
  }

  /**
   * Screen analysis interface
   * @param {Object} options
   * @returns {Promise<Object>}
   */
  async analyzeScreen(options) {
    throw new Error('analyzeScreen(options) must be implemented by subclass.');
  }

  /**
   * Health check interface
   * @returns {Promise<{available: boolean, model?: string, error?: string}>}
   */
  async checkHealth() {
    throw new Error('checkHealth() must be implemented by subclass.');
  }
}

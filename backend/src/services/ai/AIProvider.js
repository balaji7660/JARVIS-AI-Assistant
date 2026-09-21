/**
 * Abstract AI Provider Interface
 */
export class AIProvider {
  /**
   * Generates an AI response given a conversation message array
   * @param {Array<{role: string, content: string}>} messages
   * @returns {Promise<string>}
   */
  async generateResponse(messages) {
    throw new Error('generateResponse(messages) must be implemented by subclass.');
  }
}

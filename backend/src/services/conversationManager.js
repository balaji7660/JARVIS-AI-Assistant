/**
 * In-Memory Conversation Session Manager with Sliding-Window History
 */
export class ConversationManager {
  /**
   * @param {number} maxHistory Maximum conversation turns (messages) to retain per session
   */
  constructor(maxHistory = 10) {
    this.maxHistory = maxHistory;
    this.sessions = new Map();
  }

  /**
   * Gets history array for a session
   * @param {string} sessionId
   * @returns {Array<{role: string, content: string}>}
   */
  getHistory(sessionId = 'default') {
    return this.sessions.get(sessionId) || [];
  }

  /**
   * Adds a message to session history and trims to sliding window
   * @param {string} sessionId
   * @param {'user'|'assistant'} role
   * @param {string} content
   */
  addMessage(sessionId = 'default', role, content) {
    if (!this.sessions.has(sessionId)) {
      this.sessions.set(sessionId, []);
    }

    const history = this.sessions.get(sessionId);
    history.push({ role, content });

    // Enforce sliding window limit
    if (history.length > this.maxHistory) {
      this.sessions.set(sessionId, history.slice(-this.maxHistory));
    }
  }

  /**
   * Clears session context
   * @param {string} sessionId
   */
  clearSession(sessionId = 'default') {
    this.sessions.delete(sessionId);
  }
}

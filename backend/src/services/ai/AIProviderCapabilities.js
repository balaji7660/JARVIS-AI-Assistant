/**
 * Describes the functional capabilities of an AI provider.
 * Used to guard against calling unsupported operations (e.g. vision or native tools)
 * on models that do not natively support them.
 */
export class AIProviderCapabilities {
  /**
   * @param {Object} [options]
   * @param {boolean} [options.supportsToolCalling=false]
   * @param {boolean} [options.supportsStructuredOutput=false]
   * @param {boolean} [options.supportsVision=false]
   * @param {boolean} [options.supportsStreaming=false]
   */
  constructor({
    supportsToolCalling = false,
    supportsStructuredOutput = false,
    supportsVision = false,
    supportsStreaming = false
  } = {}) {
    this.supportsToolCalling = Boolean(supportsToolCalling);
    this.supportsStructuredOutput = Boolean(supportsStructuredOutput);
    this.supportsVision = Boolean(supportsVision);
    this.supportsStreaming = Boolean(supportsStreaming);
    Object.freeze(this);
  }
}

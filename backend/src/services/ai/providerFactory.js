import { OpenAIProvider } from './OpenAIProvider.js';
import { OllamaProvider } from './OllamaProvider.js';
import { PuterProvider } from './PuterProvider.js';

export const SUPPORTED_PROVIDERS = ['openai', 'ollama', 'puter'];

/**
 * Returns the currently configured AI provider name in lowercase.
 * Defaults to 'openai' if not configured.
 * @returns {string}
 */
export function getActiveProviderName() {
  return (process.env.AI_PROVIDER || 'openai').trim().toLowerCase();
}

/**
 * Centralized factory to instantiate the configured AI Provider.
 * Guarantees that only the selected provider is instantiated.
 * When Ollama or Puter is selected, OpenAIProvider is NEVER instantiated or called as a fallback.
 *
 * @param {Object} [options]
 * @param {string} [options.providerName] Explicit override for testing
 * @param {Object} [options.openAiOptions]
 * @param {Object} [options.ollamaOptions]
 * @param {Object} [options.puterOptions]
 * @returns {import('./AIProvider.js').AIProvider}
 */
export function getAIProvider({
  providerName = getActiveProviderName(),
  openAiOptions = {},
  ollamaOptions = {},
  puterOptions = {}
} = {}) {
  const normalized = (providerName || '').trim().toLowerCase();

  switch (normalized) {
    case 'puter':
      return new PuterProvider(puterOptions);

    case 'ollama':
      return new OllamaProvider(ollamaOptions);

    case 'openai':
    case '':
      return new OpenAIProvider(openAiOptions);

    default:
      throw new Error(
        `Invalid AI_PROVIDER: "${providerName}". Supported providers are: ${SUPPORTED_PROVIDERS.join(', ')}.`
      );
  }
}


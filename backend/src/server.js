import fs from 'fs';
import path from 'path';
import http from 'http';
import https from 'https';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';
dotenv.config();

// Ensure production server defaults to Puter AI provider when AI_PROVIDER is not explicitly specified
if (!process.env.AI_PROVIDER) {
  if (process.env.PUTER_AUTH_TOKEN || !process.env.OPENAI_API_KEY) {
    process.env.AI_PROVIDER = 'puter';
  }
}
if (!process.env.PUTER_MODEL) {
  process.env.PUTER_MODEL = 'gpt-4o';
}

import { createApp } from './app.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = parseInt(process.env.PORT, 10) || 3000;
const HTTPS_PORT = process.env.HTTPS_PORT ? parseInt(process.env.HTTPS_PORT, 10) : null;
const USE_HTTPS = process.env.USE_HTTPS === 'true' || process.env.ENABLE_HTTPS === 'true';

const app = createApp();

function getSslCredentials() {
  const defaultKeyPath = path.resolve(__dirname, '../certs/key.pem');
  const defaultCertPath = path.resolve(__dirname, '../certs/cert.pem');

  const keyPath = process.env.SSL_KEY_PATH 
    ? path.resolve(process.cwd(), process.env.SSL_KEY_PATH) 
    : defaultKeyPath;
  const certPath = process.env.SSL_CERT_PATH 
    ? path.resolve(process.cwd(), process.env.SSL_CERT_PATH) 
    : defaultCertPath;

  if (fs.existsSync(keyPath) && fs.existsSync(certPath)) {
    try {
      return {
        key: fs.readFileSync(keyPath),
        cert: fs.readFileSync(certPath)
      };
    } catch (err) {
      console.warn(`[JARVIS Backend Warning] Could not read SSL certs: ${err.message}`);
      return null;
    }
  }
  return null;
}

const sslCredentials = getSslCredentials();

if (USE_HTTPS) {
  if (!sslCredentials) {
    console.error('\n[JARVIS Backend Error] USE_HTTPS is enabled, but SSL certificates were not found!');
    console.error('Please generate certificates by running:');
    console.error('  npm run generate-cert');
    console.error('Or set SSL_KEY_PATH and SSL_CERT_PATH in your .env file.\n');
    process.exit(1);
  }

  const httpsServer = https.createServer(sslCredentials, app);
  httpsServer.listen(PORT, '0.0.0.0', () => {
    console.log(`[JARVIS Backend] HTTPS Server listening on https://0.0.0.0:${PORT}`);
    console.log(`[JARVIS Backend] Health endpoint: https://localhost:${PORT}/health`);
    console.log(`[JARVIS Backend] Chat endpoint: https://localhost:${PORT}/api/chat`);
  });
} else if (HTTPS_PORT && sslCredentials) {
  // Dual mode: HTTP on PORT and HTTPS on HTTPS_PORT
  http.createServer(app).listen(PORT, '0.0.0.0', () => {
    console.log(`[JARVIS Backend] HTTP Server listening on http://0.0.0.0:${PORT}`);
    console.log(`[JARVIS Backend] Health endpoint: http://localhost:${PORT}/health`);
  });

  https.createServer(sslCredentials, app).listen(HTTPS_PORT, '0.0.0.0', () => {
    console.log(`[JARVIS Backend] HTTPS Server listening on https://0.0.0.0:${HTTPS_PORT}`);
    console.log(`[JARVIS Backend] Health endpoint: https://localhost:${HTTPS_PORT}/health`);
  });
} else {
  // Standard HTTP mode
  app.listen(PORT, '0.0.0.0', () => {
    console.log(`[JARVIS Backend] Server listening on http://0.0.0.0:${PORT}`);
    console.log(`[JARVIS Backend] Health endpoint: http://localhost:${PORT}/health`);
    console.log(`[JARVIS Backend] Chat endpoint: http://localhost:${PORT}/api/chat`);
  });
}

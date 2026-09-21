import dotenv from 'dotenv';
dotenv.config();

import { createApp } from './app.js';

const PORT = process.env.PORT || 3000;
const app = createApp();

app.listen(PORT, '0.0.0.0', () => {
  console.log(`[JARVIS Backend] Server listening on http://0.0.0.0:${PORT}`);
  console.log(`[JARVIS Backend] Health endpoint: http://localhost:${PORT}/health`);
  console.log(`[JARVIS Backend] Chat endpoint: http://localhost:${PORT}/api/chat`);
});

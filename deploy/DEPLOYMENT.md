# JARVIS AI Assistant - Backend Deployment Guide

This guide provides three options to deploy and host the JARVIS AI Assistant backend so you can access it remotely from your mobile phone and laptop anywhere in the world.

---

## Prerequisites

- **OpenAI API Key**: Get your API key from [platform.openai.com/api-keys](https://platform.openai.com/api-keys).
- **GitHub Repository**: [https://github.com/balaji7660/JARVIS-AI-Assistant.git](https://github.com/balaji7660/JARVIS-AI-Assistant.git) (already set up and synchronized).

---

## Method 1: Free 24/7 Cloud Hosting on Render (Recommended)

Render provides free cloud hosting for Node.js web services directly integrated with your GitHub repository.

### Steps:
1. Go to [dashboard.render.com](https://dashboard.render.com/) and sign in with your GitHub account (`balaji7660`).
2. Click **New +** → **Web Service**.
3. Select your repository: `JARVIS-AI-Assistant`.
4. Configure the service:
   - **Name**: `jarvis-ai-backend`
   - **Region**: Choose closest to you (e.g., Singapore, Frankfurt, Oregon)
   - **Root Directory**: `backend`
   - **Runtime**: `Node`
   - **Build Command**: `npm install`
   - **Start Command**: `npm start`
   - **Instance Type**: `Free`
5. Under **Environment Variables**, add:
   - `OPENAI_API_KEY`: `sk-proj-...` (your OpenAI key)
   - `PORT`: `10000` (Render automatically sets this)
6. Click **Deploy Web Service**.
7. In ~2 minutes, Render will provide your public HTTPS URL, for example:
   ```
   https://jarvis-ai-backend.onrender.com
   ```
8. Verify it by visiting:
   ```
   https://jarvis-ai-backend.onrender.com/health
   ```
   It will return: `{"status":"ok","timestamp":...}`.

---

## Method 2: Instant Public Tunnel (Zero Setup / Immediate Testing)

If you have the backend running locally on your laptop and want to immediately connect your physical mobile phone without signing up for any cloud accounts:

1. In your project backend folder:
   ```powershell
   cd backend
   npm start
   ```
2. In a second terminal window, run:
   ```powershell
   cd backend
   npm run tunnel
   ```
3. This creates a secure, publicly accessible HTTPS tunnel, such as:
   ```
   your url is: https://gentle-cats-fly.loca.lt
   ```
4. Anyone on the internet (including your physical mobile phone on 4G/5G or separate Wi-Fi) can now communicate with your backend securely!

---

## Method 3: Container Deployment (Docker / Railway / Fly.io)

A production-ready `Dockerfile` and `Procfile` are included in `backend/`.

### Docker:
```bash
cd backend
docker build -t jarvis-backend .
docker run -d -p 3000:3000 -e OPENAI_API_KEY="your-key-here" jarvis-backend
```

### Railway:
1. Visit [railway.app](https://railway.app/) and sign in with GitHub.
2. Click **New Project** → **Deploy from GitHub repo** → select `JARVIS-AI-Assistant`.
3. Set root directory to `/backend`.
4. Add environment variable `OPENAI_API_KEY`.
5. Railway will automatically build and publish your public domain.

---

## Connecting the JARVIS Mobile App to Your Deployed Backend

Once your backend is deployed and you have your HTTPS URL:

1. Open the **JARVIS Assistant** app on your phone.
2. Tap the **Settings** gear icon in the top right.
3. Scroll down to the **AI Backend Server** card.
4. Enter your deployed URL:
   ```
   https://jarvis-ai-backend.onrender.com
   ```
5. Tap **Save & Connect**.
6. The app confirms connection and persists this URL automatically across all future sessions!

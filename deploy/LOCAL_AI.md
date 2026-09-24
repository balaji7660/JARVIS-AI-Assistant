# JARVIS Free Local AI Mode — Ollama Integration Guide

This guide provides step-by-step instructions to run JARVIS in **100% Free Local AI Mode** using [Ollama](https://ollama.com) running locally on your computer without spending any API credits or exposing API keys.

---

## 🏗️ Architecture Overview

```
                      ┌──────────────────────────────────────┐
                      │    JARVIS Android Mobile Assistant   │
                      │  (Accessibility, Room Memory, Safety)│
                      └──────────────────┬───────────────────┘
                                         │
                        Local Wi-Fi / LAN (HTTP port 3000)
                                         │
                                         ▼
                      ┌──────────────────────────────────────┐
                      │      JARVIS Node.js Local Backend    │
                      │      (Running on your PC / Laptop)   │
                      └──────────────────┬───────────────────┘
                                         │
                             Localhost (127.0.0.1:11434)
                                         │
                                         ▼
                      ┌──────────────────────────────────────┐
                      │       Ollama Local AI Engine         │
                      │  (llama3.2, mistral, or any model)   │
                      └──────────────────────────────────────┘
```

> [!NOTE]
> **Cloud vs. Local Separation:**
> - **Cloud Mode (Production):** Android connects to `https://jarvis-ai-assistant-qvzy.onrender.com` (uses OpenAI).
> - **Local Free Mode (Development):** Android connects to your computer's LAN IP (e.g. `http://192.168.1.100:3000`) where the backend talks to Ollama at `http://127.0.0.1:11434`.
> - Ollama running on your PC's `127.0.0.1` cannot be reached from the public Render cloud. For local Ollama testing, run the backend on your PC.

---

## 📥 Step 1: Install Ollama on your PC

1. Download and install Ollama from the official website:
   - **Windows:** Download [Ollama for Windows](https://ollama.com/download/windows)
   - **macOS:** Download [Ollama for Mac](https://ollama.com/download/mac)
   - **Linux:** `curl -fsSL https://ollama.com/install.sh | sh`
2. Once installed, ensure the Ollama application is running (you should see the Ollama llama icon in your Windows System Tray or taskbar).
3. Open a terminal (PowerShell or Command Prompt) and verify:
   ```bash
   ollama --version
   ```

---

## 🦙 Step 2: Pull your Preferred Model

Download your desired model. We recommend **`llama3.2`** (3B parameters) for an optimal balance of fast on-device inference and high tool-calling intelligence:

```bash
# Recommended lightweight model (fast, low RAM usage)
ollama pull llama3.2

# Alternatively, for higher reasoning capacity:
ollama pull llama3.1
# or:
ollama pull mistral
# or for local vision support:
ollama pull llama3.2-vision
```

Verify the downloaded model:
```bash
ollama list
```

Test that the model responds locally:
```bash
ollama run llama3.2 "Say hello, JARVIS!"
```
*(Type `/bye` to exit the interactive prompt).*

---

## ⚙️ Step 3: Configure the JARVIS Backend for Ollama

1. Navigate to the `backend` directory:
   ```bash
   cd backend
   ```
2. Create or edit `.env` (copy from `.env.example`):
   ```bash
   # Select Ollama as the active provider
   AI_PROVIDER=ollama

   # Ollama connection settings (default port 11434)
   OLLAMA_BASE_URL=http://127.0.0.1:11434
   OLLAMA_MODEL=llama3.2

   # Backend listening port
   PORT=3000
   ```

> [!IMPORTANT]
> **Strict No-Fallback Guarantee:** When `AI_PROVIDER=ollama`, if Ollama is stopped or unreachable, JARVIS will return `HTTP 503` with `"Local AI is unavailable. Start Ollama and try again."` It will **never** consume OpenAI credits or call OpenAI behind your back.

---

## 🚀 Step 4: Start the Local JARVIS Backend

```bash
cd backend
npm install
npm start
```

You should see:
```text
[JARVIS Backend] Server listening on http://0.0.0.0:3000
[JARVIS Backend] Health endpoint: http://localhost:3000/health
[JARVIS Backend] Chat endpoint: http://localhost:3000/api/chat
```

### Verify Ollama Integration Status:
Open your browser or run:
```bash
curl http://localhost:3000/api/ollama/status
```
Expected response when Ollama is active:
```json
{
  "available": true,
  "provider": "ollama",
  "model": "llama3.2",
  "modelInstalled": true,
  "availableModels": ["llama3.2:latest"]
}
```

---

## 📱 Step 5: Connect your Android Device to the Local Backend

### 1. Find your Computer's Local Wi-Fi / LAN IP Address
In PowerShell on Windows:
```powershell
ipconfig
```
Look for `IPv4 Address` under your active Wi-Fi or Ethernet adapter, for example: `192.168.1.105`.

### 2. Allow Inbound Traffic on Port 3000 (Windows Firewall)
If testing from a physical phone connected to the same Wi-Fi network, allow port 3000:
```powershell
# Run once in Administrator PowerShell:
New-NetFirewallRule -DisplayName "JARVIS Backend Port 3000" -Direction Inbound -LocalPort 3000 -Protocol TCP -Action Allow
```

### 3. Configure the URL in JARVIS Android App
1. Open the **JARVIS** app on your phone or emulator.
2. Tap the **Settings** gear icon in the top right.
3. Scroll down to the **Backend Server Configuration** card.
4. Enter your PC's IP address and port:
   ```text
   http://192.168.1.105:3000/
   ```
   *(For Android Studio emulator on the same PC, you can use `http://10.0.2.2:3000/`)*.
5. Tap **Save & Connect**. You will see the confirmation badge `Connected!`.

---

## 🧪 Step 6: Test Voice and Text Commands in Free Mode

Test commands with your local Ollama model:
- **Time Check:** `"Hey JARVIS, what time is it?"` -> Executes `get_time`.
- **Memory Storage:** `"Hey JARVIS, remember that my favorite language is Kotlin."` -> Saves to Room SQLite.
- **Memory Recall:** `"What do you remember about me?"` -> Recalls saved facts from Room.
- **App Navigation:** `"Hey JARVIS, open YouTube."` -> Launches YouTube application.
- **System Navigation:** `"Hey JARVIS, go home."` -> Navigates to home screen.
- **Multi-Step Task:** `"Open Settings and find Wi-Fi."` -> Generates bounded task plan.

---

## 🔄 Step 7: Switching Back to OpenAI Cloud Mode

Whenever you want to switch back to the cloud-hosted backend:
1. In the Android App Settings, tap **Reset** to restore the default cloud URL:
   `https://jarvis-ai-assistant-qvzy.onrender.com/`
2. Or in your backend `.env`, set `AI_PROVIDER=openai`.

---

## 🔒 Security & Privacy Guarantees in Local Mode

1. **Zero Data Leaves your PC:** All prompts and responses stay on your local machine and home network.
2. **Local Memory Isolation:** User preferences and facts are stored strictly in Android's local Room database.
3. **Deterministic Safety Enforcement:** The local Ollama model is **never trusted blindly**. Every requested tool and plan step must be approved by the Android `SafetyManager`, `TaskPlanValidator`, and `ActionVerifier`.
4. **Credential Masking:** Passwords, OTPs, PINs, and payment card numbers are automatically redacted before sending to any model.

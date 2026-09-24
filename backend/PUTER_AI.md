# JARVIS Puter AI Provider Integration Guide

This guide details the **Puter AI (`PuterProvider`)** integration for JARVIS, enabling the assistant to route intelligence, structured tool calling, screen analysis, and task planning through the [Puter.js](https://docs.puter.com) platform SDK.

---

## 1. What Puter Integration Does

Puter AI serves as an alternative cloud-hosted AI engine alongside `OpenAIProvider` and `OllamaProvider`. 

Key capabilities:
- **Natural Conversational Intelligence:** Full multi-turn dialogues with JARVIS personality preservation ("Yes, boss?", respectful, concise).
- **Controlled Structured Tool Calling:** Puter proposes actions (e.g. `get_time`, `open_app`, `type_text`, `click_text`), which are verified by the backend allowlist and authorized by Android's `SafetyManager`.
- **Autonomous Task Planning:** Integrates directly with `PlanService` to generate structured, step-bounded task execution plans for multi-step automation.
- **Screen Understanding:** Multimodal screen analysis when a vision-capable model is selected, or sanitized accessibility tree analysis fallback.

---

## 2. How to Configure Puter

1. Create or sign in to your Puter account at [puter.com](https://puter.com).
2. Generate an **Auth Token** in your [Puter Developer Dashboard](https://puter.com/dashboard).
3. Set your backend `.env` variables:
   ```env
   # Select Puter as the active AI provider
   AI_PROVIDER=puter

   # Puter authentication token (required for backend server execution)
   PUTER_AUTH_TOKEN=your_puter_auth_token_here

   # Desired model tag (default: gpt-4o-mini)
   PUTER_MODEL=gpt-4o-mini
   ```
4. Start or restart the backend:
   ```bash
   cd backend
   npm start
   ```

---

## 3. Required Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `AI_PROVIDER` | Yes | `openai` | Set to `puter` to activate Puter AI. |
| `PUTER_AUTH_TOKEN` | Yes (when `AI_PROVIDER=puter`) | *None* | Secret authentication token generated from Puter Dashboard. |
| `PUTER_MODEL` | No | `gpt-4o-mini` | The model identifier to use (e.g. `gpt-4o-mini`, `gpt-4o`, `claude-3-5-sonnet`). |
| `PORT` | No | `3000` | Port on which the JARVIS backend listens. |

> [!CAUTION]
> **Credential Security:**
> - `PUTER_AUTH_TOKEN` is strictly a **server-side credential**.
> - It is **never** embedded in Android code, APK assets, or sent over HTTP to mobile clients.
> - The mobile application remains completely provider-agnostic and interacts solely with the backend API.

---

## 4. How Provider Selection Works & Zero-Fallback Guarantees

Provider selection is governed deterministically by `providerFactory.js`:
- `AI_PROVIDER=openai` → Instantiates `OpenAIProvider`.
- `AI_PROVIDER=ollama` → Instantiates `OllamaProvider`.
- `AI_PROVIDER=puter` → Instantiates `PuterProvider`.
- `AI_PROVIDER` missing or empty → Defaults to `OpenAIProvider`.
- Any invalid/unknown value → Throws an explicit configuration error listing valid options.

### Strict Zero-Fallback Policy
When `AI_PROVIDER=puter`:
- Neither `OpenAIProvider` nor `OllamaProvider` is ever instantiated as a fallback.
- If Puter authentication fails, the server returns `HTTP 401`.
- If Puter is unreachable or times out, the server returns `HTTP 503` or `504` with a descriptive message (`"Puter AI is currently unavailable"`).
- It will **never** consume OpenAI credits or contact Ollama behind your back.

---

## 5. Tool Calling Architecture & Strict Allowlist

```
Put AI (Model)
      │
      ▼  Structured tool proposal
[PuterProvider]
      │
      ▼  Check against APPROVED_TOOL_NAMES
[Backend Allowlist Validation]
      │
      ├── Unknown tool, shell command, or eval? ──► REJECTED IMMEDIATELY
      │
      ▼  Valid tool call dispatch
[ToolRouter]
      │
      ▼  Risk evaluation & user confirmation
[SafetyManager]
      │
      ▼  Local Android OS execution
[Android Device Execution]
      │
      ▼  ToolResult (success/data)
[PuterProvider Follow-up Turn]
      │
      ▼
Final Voice Response (TTS)
```

### Authoritative Approved Tools:
- `get_time`, `get_date`
- `go_home`, `press_back`
- `open_url` (Strictly validated `http://` or `https://`)
- `open_app` (Launches installed package/app name)
- `read_visible_screen`, `wait_for_screen`
- `click_text`, `click_view`, `scroll`
- `type_text` (Requires mandatory user confirmation)
- `analyze_current_screen`

> [!IMPORTANT]
> The AI provider is **never authoritative**. It only *proposes* structured tool invocations. The server and Android `SafetyManager` remain the ultimate gatekeepers.

---

## 6. Safety Architecture & Risk Classification

JARVIS enforces deterministic safety rules:
- **LOW Risk (`get_time`, `go_home`, `read_visible_screen`):** Automatically permitted within bounded rate limits.
- **MEDIUM Risk (`type_text`, opening external URLs):** Requires explicit on-screen user confirmation. The model cannot self-confirm or bypass user approval.
- **HIGH Risk (System settings modification, sensitive permissions):** Strictly blocked or gated behind explicit multi-step biometric/PIN authorization.

---

## 7. Authentication

Because Node.js runs headless on a server (unlike a web browser where Puter shows an interactive login popup), Puter Node.js SDK requires programmatic authentication using an API token:
1. The token is read from `process.env.PUTER_AUTH_TOKEN`.
2. Passed directly to `init(authToken)` from `@heyputer/puter.js/src/init.cjs`.
3. If the token is absent or invalid, requests fail fast with a descriptive HTTP 503/401 error.

---

## 8. Model Configuration & Capabilities

Puter supports multiple upstream model engines. Configure `PUTER_MODEL` according to your needs:
- `gpt-4o-mini` *(Recommended Default)*: Fast, economical, supports structured output, tools, and vision.
- `gpt-4o`: Higher reasoning capacity with full multimodal vision.
- `claude-3-5-sonnet`: Advanced coding and reasoning capabilities.

`PuterProvider.capabilities` automatically reflects:
- `supportsToolCalling`: `true`
- `supportsStructuredOutput`: `true`
- `supportsVision`: `true` (for `gpt-4o`, `gpt-4o-mini`, `claude-3-5-sonnet`, `gemini-1.5-flash`), `false` for text-only models.

---

## 9. Platform Service Terms & Limitations

> [!WARNING]
> **Important Distinction on Puter Service Terms:**
> - Puter is **not** an "unlimited free OpenAI API loophole".
> - Puter provides AI capabilities under its own platform terms and user-pays model.
> - Free tiers, model allowances, rate limits, and credit quotas are subject to Puter's current policies and terms of service.
> - For high-volume production deployments, ensure your Puter account has appropriate quota and billing configured.

---

## 10. Troubleshooting

### Problem: `Puter authentication token is not configured on the server.` (HTTP 503)
- **Cause:** `PUTER_AUTH_TOKEN` is not set or is empty in `backend/.env`.
- **Solution:** Add `PUTER_AUTH_TOKEN=your_token` to `backend/.env` and restart the backend.

### Problem: `Puter authentication failed. Please verify PUTER_AUTH_TOKEN.` (HTTP 401)
- **Cause:** The provided token is invalid, expired, or revoked on [puter.com](https://puter.com).
- **Solution:** Generate a new auth token from the Puter dashboard.

### Problem: `Puter AI request timed out after 30000ms.` (HTTP 504)
- **Cause:** Upstream model latency or network connectivity problems.
- **Solution:** Check your internet connection. JARVIS automatically cleans up timed-out requests without blocking the client.

### Problem: `Puter AI is currently unavailable.` (HTTP 503)
- **Cause:** Network interruption or Puter cloud service outage.
- **Solution:** Check [status.puter.com](https://status.puter.com) or temporarily switch `AI_PROVIDER=openai` or `AI_PROVIDER=ollama`.

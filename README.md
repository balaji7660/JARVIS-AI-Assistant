# JARVIS — Futuristic Android AI Assistant & Automation Engine

![Android](https://img.shields.io/badge/Android-36-blue?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple?logo=kotlin)
![Jetpack%20Compose](https://img.shields.io/badge/Jetpack%20Compose-Ready-brightgreen?logo=jetpackcompose)
![Architecture](https://img.shields.io/badge/Architecture-Clean%20%2F%20MVVM-orange)
![Safety](https://img.shields.io/badge/SafetyManager-Deterministic-red)
![Node.js](https://img.shields.io/badge/Backend-Node.js%2020-green?logo=node.js)

**JARVIS** is an advanced on-device and edge-connected AI Assistant for Android, featuring an iron-clad safety architecture, multi-step task planning, screen understanding, long-term Room-based memory, short-term conversational context, and a persistent floating Arc-Reactor HUD overlay.

---

## 🌟 Key Capabilities (Milestones 1–11)

- 🎙️ **Voice Engine & Wake Word (Milestones 1–2, 7):** On-device wake-word detection (`"Hey JARVIS"`) paired with speech transcription and dynamic audio visualizer states.
- 🧠 **AI Model & Structured Tool Calling (Milestone 3):** Context-aware backend engine returning structured plans and function calls.
- ⚙️ **On-Device Automation & Accessibility (Milestones 4–5):** Non-intrusive UI automation using Android `AccessibilityService` and an authoritative `ActionVerifier`.
- 🛡️ **Deterministic Safety Engine (Milestone 6):** Three-tier risk classification (`LOW`, `MEDIUM`, `HIGH`). Critical and sensitive operations (passwords, OTPs, biometric/payment screens) are strictly blocked from AI execution.
- 👁️ **Screen Understanding & Vision Inspection (Milestone 8):** Visual screen analysis with UI hierarchy extraction to locate target interactive elements.
- 💾 **Long-Term Persistent Memory (Milestone 9):** Local SQLite Room database bounded to 500 explicit user facts and preferences with secret-redaction filters.
- 💬 **Contextual Intelligence & Conversational Memory (Milestone 10):** In-memory ephemeral task context (10 turns, 5-minute inactivity TTL) with disambiguated reference resolution (`"it"`, `"the first result"`).
- 🛸 **Persistent Floating Arc-Reactor Overlay (Milestone 11):** High-tech floating HUD orb powered by standard Android `SYSTEM_ALERT_WINDOW`. Supports left/right edge-snapping, draggable positioning, quick actions menu, and live task progression without leaving third-party apps.

---

## 🏛️ System Architecture

```
User Voice / Tap / Floating Overlay
              ↓
        HomeViewModel
              ↓
  ┌───────────┴───────────┐
  ↓                       ↓
TaskPlanner          SafetyManager (Deterministic Authority)
  ↓                       ↓
TaskExecutor ─────────────┘
  ↓
AccessibilityAutomationProvider / AccessibilityService
  ↓
ActionVerifier (Pre- & Post-Execution Verification)
```

### Pure UI Overlay Architecture
```
JarvisOverlayService (WindowManager / ComposeView)
              ↓
   JarvisOverlayController
              ↓
      AssistantBridge
              ↓
        HomeViewModel (Authoritative Engine)
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Koala or Android SDK Platform Tools (API 26 to 36)
- Java Development Kit (JDK 17)
- Node.js 18+ (for local backend daemon)

### 1. Start the Backend Daemon
```bash
cd backend
npm install
npm start
# Health check available at: http://localhost:3000/health
```

### 2. Build and Install Android App
```bash
# Run all unit tests (179 tests)
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug

# Install on connected device or emulator
./gradlew installDebug
```

---

## 🧪 Testing Suite

- **Android Unit Tests:** 179 passing tests covering `SafetyManager`, `TaskPlanner`, `TaskExecutor`, `ReferenceResolver`, `ConversationContextManager`, `JarvisOrbPositionHelper`, and `JarvisOverlayController`.
- **Backend Tests:** 23 passing tests covering `/api/chat`, `/api/plan`, and `/api/screen/analyze`.

```bash
# Android tests
./gradlew testDebugUnitTest

# Backend tests
cd backend && npm test
```

---

## 🔒 Security & Privacy Guarantees

1. **Deterministic Authority:** AI suggestions are never blindly executed. `SafetyManager` validates every step locally.
2. **Credential Protection:** Passwords, OTPs, 2FA tokens, and banking apps are strictly excluded from automated interaction and context memory.
3. **No Root / No Shizuku:** Operates strictly within standard Android public APIs (`AccessibilityService`, `SYSTEM_ALERT_WINDOW`, Room SQLite).
4. **Ephemerality:** Short-term conversational context lazily expires after 5 minutes and is completely isolated from long-term memory.

---

## 📄 License
MIT License

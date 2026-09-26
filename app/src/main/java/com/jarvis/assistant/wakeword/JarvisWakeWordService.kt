package com.jarvis.assistant.wakeword

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.R
import com.jarvis.assistant.ai.LocalResponseEngine
import com.jarvis.assistant.ai.OpenAIResponseEngine
import com.jarvis.assistant.ai.ResponseEngine
import com.jarvis.assistant.automation.AccessibilityAutomationProvider
import com.jarvis.assistant.context.ContinuousConversationManager
import com.jarvis.assistant.data.remote.ApiClient
import com.jarvis.assistant.memory.JarvisDatabase
import com.jarvis.assistant.memory.NoteRepository
import com.jarvis.assistant.personality.JarvisPersonalityEngine
import com.jarvis.assistant.reminders.TimerReminderManager
import com.jarvis.assistant.calendar.CalendarManager
import com.jarvis.assistant.tools.SafetyManager
import com.jarvis.assistant.tools.ToolRegistry
import com.jarvis.assistant.tools.ToolRouter
import com.jarvis.assistant.tools.impl.*
import com.jarvis.assistant.voice.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Authoritative, persistent Foreground Service that owns and executes background voice assistant lifecycle.
 * Operates independently from MainActivity and Compose UI lifecycle.
 *
 * Architecture:
 * Android System -> JarvisWakeWordService -> MicrophoneSessionManager ->
 * [LocalWakeWordDetector <-> SpeechRecognizerManager <-> JarvisTTSManager <-> ToolRouter / ResponseEngine]
 */
class JarvisWakeWordService : Service() {

    companion object {
        private const val TAG = "JarvisWakeService"
        const val CHANNEL_ID = "jarvis_wake_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.jarvis.assistant.action.START_WAKE_SERVICE"
        const val ACTION_STOP = "com.jarvis.assistant.action.STOP_WAKE_SERVICE"

        @Volatile
        private var instance: JarvisWakeWordService? = null

        fun getInstance(): JarvisWakeWordService? = instance

        fun startService(context: Context) {
            WakeWordPreferences.setBackgroundWakeEnabled(context, true)
            val intent = Intent(context, JarvisWakeWordService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            WakeWordPreferences.setBackgroundWakeEnabled(context, false)
            val intent = Intent(context, JarvisWakeWordService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    inner class WakeWordServiceBinder : Binder() {
        fun getService(): JarvisWakeWordService = this@JarvisWakeWordService
    }

    private val binder = WakeWordServiceBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Authoritative Microphone Session Manager
    lateinit var micSessionManager: MicrophoneSessionManager
        private set

    // Audio & Voice components
    private var speechRecognizerManager: SpeechRecognizerManager? = null
    private var ttsManager: JarvisTTSManager? = null
    private var responseEngine: ResponseEngine? = null
    private var localResponseEngine: LocalResponseEngine? = null
    private var toolRouter: ToolRouter? = null

    // Continuous conversation manager (45-second timeout)
    val continuousConversationManager = ContinuousConversationManager(
        sessionTimeoutMs = 45_000L,
        onSessionExpired = {
            Log.i(TAG, "Continuous conversation session expired. Returning to passive wake detection.")
            updateNotification("Listening for \"Hey Jarvis\"...")
            serviceScope.launch {
                micSessionManager.requestWakeDetection("session_expired")
            }
        }
    )

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _lastRecognizedCommand = MutableStateFlow("")
    val lastRecognizedCommand: StateFlow<String> = _lastRecognizedCommand.asStateFlow()

    private val _lastAssistantReply = MutableStateFlow("")
    val lastAssistantReply: StateFlow<String> = _lastAssistantReply.asStateFlow()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "JarvisWakeWordService onCreate.")
        JarvisLogger.log(JarvisLogger.Event.WAKE_SERVICE_STARTED, "Service initializing")
        WakeWordPreferences.incrementServiceStartCount(this)

        createNotificationChannel()
        initializeMicrophoneSessionManager()
        initializeAssistantComponents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "onStartCommand: action=$action")

        if (action == ACTION_STOP) {
            Log.i(TAG, "Received ACTION_STOP. Halting wake-word service.")
            stopServiceInternal()
            stopSelf()
            return START_NOT_STICKY
        }

        // Start Foreground Service with MICROPHONE type
        val notification = buildForegroundNotification("Listening for \"Hey Jarvis\"...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startWakeDetectionInternal()
        _isServiceRunning.value = true
        WakeWordManager.setServiceActive(true)

        return START_STICKY
    }

    private fun initializeMicrophoneSessionManager() {
        micSessionManager = MicrophoneSessionManager(applicationContext)

        val detector = WakeWordManager.getInstance(applicationContext)

        micSessionManager.onStartWakeDetection = {
            detector.suppressDuringSpeech(false)
            detector.start()
        }

        micSessionManager.onStopWakeDetection = {
            detector.stop()
        }

        micSessionManager.onStartCommandListening = {
            detector.suppressDuringSpeech(true)
            detector.notifyCommandListeningStarted()
            speechRecognizerManager?.startListening()
        }

        micSessionManager.onStopCommandListening = {
            speechRecognizerManager?.stopListening()
        }

        micSessionManager.onStopTtsPlayback = {
            detector.suppressDuringSpeech(true)
            ttsManager?.stop()
        }
    }

    private fun initializeAssistantComponents() {
        // 1. Text-To-Speech
        ttsManager = JarvisTTSManager(
            context = applicationContext,
            listener = object : TTSListener {
                override fun onSpeechStarted(utteranceId: String) {
                    Log.d(TAG, "TTS playback started: $utteranceId")
                }

                override fun onSpeechCompleted(utteranceId: String) {
                    Log.d(TAG, "TTS playback completed: $utteranceId")
                    handleTtsCompletion(utteranceId)
                }

                override fun onSpeechError(utteranceId: String, errorMessage: String) {
                    Log.w(TAG, "TTS error on $utteranceId: $errorMessage")
                    handleTtsCompletion(utteranceId)
                }
            }
        )

        // 2. SpeechRecognizer
        speechRecognizerManager = SpeechRecognizerManager(
            context = applicationContext,
            listener = object : SpeechRecognitionListener {
                override fun onReady() {
                    Log.d(TAG, "SpeechRecognizer is ready for speech.")
                    updateNotification("Listening for command...")
                }

                override fun onFinalResult(text: String) {
                    micSessionManager.onSpeechRecognized(text)
                    handleUserVoiceCommand(text)
                }

                override fun onError(errorCode: Int, errorMessage: String) {
                    Log.w(TAG, "SpeechRecognizer error: $errorMessage (code: $errorCode)")
                    updateNotification("Listening for \"Hey Jarvis\"...")
                    micSessionManager.onSpeechError(errorCode, errorMessage) {
                        resumeWakeWordDetection()
                    }
                }
            }
        )

        // 3. ToolRouter and ResponseEngine
        val automationProvider = AccessibilityAutomationProvider()
        val registry = ToolRegistry().apply {
            register(GetTimeTool())
            register(GetDateTool())
            register(GoHomeTool(applicationContext))
            register(PressBackTool())
            register(OpenUrlTool(applicationContext))
            register(OpenAppTool(applicationContext))
            register(CallContactTool(applicationContext))

            // Device control tools (Phase 1)
            register(GetBatteryStatusTool(applicationContext))
            register(SetVolumeTool(applicationContext))
            register(GetVolumeTool(applicationContext))
            register(SetBrightnessTool(applicationContext))
            register(GetBrightnessTool(applicationContext))
            register(ToggleFlashlightTool(applicationContext))
            register(OpenCameraTool(applicationContext))
            register(GetDeviceInfoTool(applicationContext))
            register(GetNetworkStatusTool(applicationContext))
            register(OpenWifiSettingsTool(applicationContext))
            register(OpenBluetoothSettingsTool(applicationContext))
            register(LockScreenTool())

            // Media control tools (Phase 2)
            register(PlayMediaTool(applicationContext))
            register(PauseMediaTool(applicationContext))
            register(ResumeMediaTool(applicationContext))
            register(NextTrackTool(applicationContext))
            register(PreviousTrackTool(applicationContext))
            register(GetMediaStateTool(applicationContext))

            // SMS & WhatsApp tools (Phase 3)
            register(SendSmsTool(applicationContext))
            register(SendWhatsAppMessageTool(context = applicationContext, automationProvider = automationProvider))

            // Accessibility tools
            register(ReadVisibleScreenTool(automationProvider))
            register(ClickTextTool(automationProvider))
            register(ClickViewTool(automationProvider))
            register(ScrollTool(automationProvider))
            register(TypeTextTool(automationProvider))
            register(WaitForScreenTool(automationProvider))

            // Screen understanding tools (Phase 6)
            register(FindScreenElementTool(automationProvider))
            register(ReadCurrentScreenTool(automationProvider))
            register(DiagnoseScreenErrorTool(automationProvider))
            register(ClickScreenElementTool(automationProvider))
            register(ScrollScreenTool(automationProvider))

            // Web Assistant tools (Phase 7)
            register(SearchWebTool(context = applicationContext, automationProvider = automationProvider))
            register(SearchYouTubeTool(context = applicationContext, automationProvider = automationProvider))
            val readWebpageTool = ReadCurrentWebpageTool(automationProvider)
            register(readWebpageTool)
            register(SummarizeWebpageTool(readWebpageTool))

            // Local Notes, Reminders, Calendar (Phases 8-10)
            try {
                val db = JarvisDatabase.getInstance(applicationContext)
                val noteRepo = NoteRepository(db.noteDao())
                val timerReminderMgr = TimerReminderManager(applicationContext, db.reminderDao())
                val calMgr = CalendarManager(applicationContext)

                register(CreateNoteTool(noteRepo))
                register(SearchNotesTool(noteRepo))
                register(ListNotesTool(noteRepo))
                register(UpdateNoteTool(noteRepo))
                register(DeleteNoteTool(noteRepo))

                register(CreateTimerTool(timerReminderMgr))
                register(CancelTimerTool(timerReminderMgr))
                register(ListTimersTool(timerReminderMgr))
                register(CreateReminderTool(timerReminderMgr))
                register(CancelReminderTool(timerReminderMgr))
                register(ListRemindersTool(timerReminderMgr))

                register(CreateCalendarEventTool(calMgr))
                register(ListCalendarEventsTool(calMgr))
                register(DeleteCalendarEventTool(calMgr))
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to initialize Room DB tools in background service", e)
            }
        }

        toolRouter = ToolRouter(registry = registry, safetyManager = SafetyManager())
        localResponseEngine = LocalResponseEngine(toolRouter)
        responseEngine = OpenAIResponseEngine(
            toolRouter = toolRouter,
            chatApiService = ApiClient.getChatApiService(),
            fallbackEngine = localResponseEngine
        )
    }

    private val backgroundWakeListener = object : WakeWordListener {
        override fun onWakeWordDetected() {
            Log.i(TAG, "⚡ Wake word detected in background service!")
            JarvisLogger.log(JarvisLogger.Event.WAKE_DETECTED, "Hey Jarvis detected")
            WakeWordPreferences.recordWakeDetected(this@JarvisWakeWordService)

            serviceScope.launch {
                handleWakeWordDetected()
            }
        }

        override fun onWakeWordError(error: String) {
            Log.e(TAG, "Wake word detector error: $error")
            serviceScope.launch {
                micSessionManager.requestWakeDetection("wake_detector_error_retry")
            }
        }
    }

    private fun startWakeDetectionInternal() {
        val detector = WakeWordManager.getInstance(applicationContext)
        detector.addListener(backgroundWakeListener)
        micSessionManager.requestWakeDetection("service_start")
        Log.i(TAG, "Persistent background wake-word listening active.")
    }

    private fun stopServiceInternal() {
        _isServiceRunning.value = false
        WakeWordManager.setServiceActive(false)
        val detector = WakeWordManager.getInstance(applicationContext)
        detector.removeListener(backgroundWakeListener)
        micSessionManager.releaseAll()
        continuousConversationManager.endSession()
        speechRecognizerManager?.destroy()
        ttsManager?.shutdown()
        JarvisLogger.log(JarvisLogger.Event.WAKE_SERVICE_STOPPED, "Service stopped")
        Log.i(TAG, "Persistent background wake-word listening stopped.")
    }

    private fun handleWakeWordDetected() {
        continuousConversationManager.startOrExtendSession()
        updateNotification("JARVIS — Processing")

        val greeting = JarvisPersonalityEngine.getWakeGreeting()
        _lastAssistantReply.value = greeting

        // Speak greeting ("Yes, boss?") with mic ownership
        micSessionManager.requestTtsSpeaking("wake_greeting") {
            ttsManager?.speak(greeting, "wake_prompt")
        }
    }

    private fun handleTtsCompletion(utteranceId: String) {
        if (utteranceId == "wake_prompt") {
            // After speaking "Yes, boss?", transition to command listening
            micSessionManager.requestCommandListening("after_wake_prompt") {
                // Command listening started
            }
        } else if (utteranceId == "stop_reply") {
            // User requested stop -> return to passive wake
            continuousConversationManager.endSession()
            updateNotification("Listening for \"Hey Jarvis\"...")
            micSessionManager.onTtsCompleted(
                utteranceId = utteranceId,
                isContinuousSession = false,
                onListenAgain = {},
                onResumeWake = {
                    resumeWakeWordDetection()
                }
            )
        } else {
            // After speaking reply, check continuous conversation status
            val isContinuous = continuousConversationManager.isSessionActive()
            if (isContinuous) {
                updateNotification("Listening for follow-up...")
            } else {
                updateNotification("Listening for \"Hey Jarvis\"...")
            }

            micSessionManager.onTtsCompleted(
                utteranceId = utteranceId,
                isContinuousSession = isContinuous,
                onListenAgain = {
                    // SpeechRecognizer will automatically start via onStartCommandListening
                },
                onResumeWake = {
                    resumeWakeWordDetection()
                }
            )
        }
    }

    private fun handleUserVoiceCommand(command: String) {
        val trimmed = command.trim()
        Log.i(TAG, "Received voice command: \"$trimmed\"")
        _lastRecognizedCommand.value = trimmed

        if (trimmed.isBlank()) {
            resumeWakeWordDetection()
            return
        }

        // Extend continuous conversation session
        continuousConversationManager.startOrExtendSession()

        // Immediate stop/cancel commands
        if (trimmed.equals("stop", ignoreCase = true) ||
            trimmed.equals("cancel", ignoreCase = true) ||
            trimmed.equals("stop task", ignoreCase = true)
        ) {
            Log.i(TAG, "User requested STOP. Halting background action.")
            continuousConversationManager.endSession()
            micSessionManager.requestTtsSpeaking("stop_command") {
                ttsManager?.speak("Stopped, boss.", "stop_reply")
            }
            return
        }

        serviceScope.launch {
            try {
                updateNotification("JARVIS — Processing: \"$trimmed\"")
                val engine = responseEngine ?: localResponseEngine ?: LocalResponseEngine(toolRouter)
                val response = engine.generateResponse(trimmed)
                Log.i(TAG, "Generated assistant reply: \"$response\"")
                _lastAssistantReply.value = response

                micSessionManager.requestTtsSpeaking("command_reply") {
                    ttsManager?.speak(response, "final_reply")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing voice command: ${e.message}", e)
                val fallbackReply = "I encountered an error executing that command, boss."
                _lastAssistantReply.value = fallbackReply
                micSessionManager.requestTtsSpeaking("error_reply") {
                    ttsManager?.speak(fallbackReply, "final_reply")
                }
            }
        }
    }

    fun triggerManualMic() {
        continuousConversationManager.startOrExtendSession()
        micSessionManager.requestCommandListening("manual_mic_tap") {}
    }

    fun triggerStop() {
        continuousConversationManager.endSession()
        micSessionManager.releaseAll()
        resumeWakeWordDetection()
    }

    private fun resumeWakeWordDetection() {
        val detector = WakeWordManager.getInstance(applicationContext)
        detector.suppressDuringSpeech(false)
        micSessionManager.requestWakeDetection("resume_detection")
        updateNotification("Listening for \"Hey Jarvis\"...")
        Log.i(TAG, "Wake-word detection resumed in background service.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Assistant Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps JARVIS listening for 'Hey Jarvis' in the background with microphone"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(status: String) {
        val notification = buildForegroundNotification(status)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, JarvisWakeWordService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS — Background Assistant Active")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Disable", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServiceInternal()
        serviceScope.cancel()
        instance = null
        Log.i(TAG, "JarvisWakeWordService destroyed.")
    }
}

package com.jarvis.assistant.wakeword

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import com.jarvis.assistant.data.remote.ApiClient
import com.jarvis.assistant.tools.SafetyManager
import com.jarvis.assistant.tools.ToolRegistry
import com.jarvis.assistant.tools.ToolRouter
import com.jarvis.assistant.tools.impl.*
import com.jarvis.assistant.memory.JarvisDatabase
import com.jarvis.assistant.memory.NoteRepository
import com.jarvis.assistant.reminders.TimerReminderManager
import com.jarvis.assistant.calendar.CalendarManager
import com.jarvis.assistant.voice.JarvisTTSManager
import com.jarvis.assistant.voice.SpeechRecognitionListener
import com.jarvis.assistant.voice.SpeechRecognizerManager
import com.jarvis.assistant.voice.TTSListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Persistent Foreground Service that owns and executes background wake-word listening.
 * Runs with microphone foreground service type, allowing "Hey Jarvis" detection
 * while the app is backgrounded, user is in other apps (Chrome, YouTube), or screen off.
 */
class JarvisWakeWordService : Service() {

    companion object {
        private const val TAG = "JarvisWakeService"
        const val CHANNEL_ID = "jarvis_wake_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.jarvis.assistant.action.START_WAKE_SERVICE"
        const val ACTION_STOP = "com.jarvis.assistant.action.STOP_WAKE_SERVICE"

        fun startService(context: Context) {
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
            val intent = Intent(context, JarvisWakeWordService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isListening = false
    private var isProcessingCommand = false

    private var speechRecognizerManager: SpeechRecognizerManager? = null
    private var ttsManager: JarvisTTSManager? = null
    private var responseEngine: ResponseEngine? = null
    private var toolRouter: ToolRouter? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "JarvisWakeWordService created.")
        createNotificationChannel()
        initializeAssistantComponents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            Log.i(TAG, "Received ACTION_STOP. Halting wake-word service.")
            stopListeningInternal()
            stopSelf()
            return START_NOT_STICKY
        }

        // Start foreground with microphone type
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startListeningInternal()
        return START_STICKY
    }

    private fun initializeAssistantComponents() {
        // Initialize Text-To-Speech for background voice feedback
        ttsManager = JarvisTTSManager(
            context = applicationContext,
            listener = object : TTSListener {
                override fun onSpeechCompleted(utteranceId: String) {
                    if (utteranceId == "wake_prompt") {
                        // After speaking "Yes, boss?", start listening for user command
                        serviceScope.launch {
                            delay(150)
                            startSpeechRecognition()
                        }
                    } else if (utteranceId == "final_reply" || utteranceId == "stop_reply") {
                        // After speaking the reply, resume wake-word detector
                        resumeWakeWordDetection()
                    }
                }

                override fun onSpeechError(utteranceId: String, error: String) {
                    Log.w(TAG, "TTS error on $utteranceId: $error")
                    if (utteranceId == "wake_prompt") {
                        startSpeechRecognition()
                    } else {
                        resumeWakeWordDetection()
                    }
                }
            }
        )

        // Initialize SpeechRecognizer
        speechRecognizerManager = SpeechRecognizerManager(
            context = applicationContext,
            listener = object : SpeechRecognitionListener {
                override fun onFinalResult(text: String) {
                    handleUserVoiceCommand(text)
                }

                override fun onError(errorCode: Int, errorMessage: String) {
                    Log.w(TAG, "SpeechRecognizer error: $errorMessage (code: $errorCode)")
                    resumeWakeWordDetection()
                }
            }
        )

        // Initialize ToolRouter and ResponseEngine
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

            // SMS tool (Phase 3)
            register(SendSmsTool(applicationContext))

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
        val localFallback = LocalResponseEngine(toolRouter)
        responseEngine = OpenAIResponseEngine(
            toolRouter = toolRouter,
            chatApiService = ApiClient.getChatApiService(),
            fallbackEngine = localFallback
        )
    }

    private val backgroundWakeListener = object : WakeWordListener {
        override fun onWakeWordDetected() {
            Log.i(TAG, "⚡ Wake word detected in background service!")
            serviceScope.launch {
                handleWakeWordDetected()
            }
        }

        override fun onWakeWordError(error: String) {
            Log.e(TAG, "Wake word detector error: $error")
        }
    }

    private fun startListeningInternal() {
        if (isListening) return
        val detector = WakeWordManager.getInstance(this)
        detector.addListener(backgroundWakeListener)
        detector.start()
        isListening = true
        WakeWordManager.setServiceActive(true)
        Log.i(TAG, "Persistent background wake-word listening started.")
    }

    private fun stopListeningInternal() {
        isListening = false
        val detector = WakeWordManager.getInstance(this)
        detector.removeListener(backgroundWakeListener)
        WakeWordManager.stop()
        WakeWordManager.setServiceActive(false)
        speechRecognizerManager?.destroy()
        ttsManager?.stop()
        Log.i(TAG, "Persistent background wake-word listening stopped.")
    }

    private fun handleWakeWordDetected() {
        if (isProcessingCommand) return
        isProcessingCommand = true

        // 1. If MainActivity is visible in the foreground, it handles UI-driven interaction
        if (WakeWordManager.isActivityVisible) {
            Log.d(TAG, "MainActivity is in foreground; delegating wake event to Activity.")
            isProcessingCommand = false
            return
        }

        // 2. Background Assistant Voice Interaction (user is in Chrome, YouTube, or Home)
        Log.i(TAG, "Executing background voice interaction (MainActivity is backgrounded)...")
        WakeWordManager.suppressDuringSpeech(true)

        // Greet user
        ttsManager?.speak("Yes, boss?", "wake_prompt")
    }

    private fun startSpeechRecognition() {
        Log.i(TAG, "Starting SpeechRecognizer for user command...")
        speechRecognizerManager?.startListening()
    }

    private fun handleUserVoiceCommand(command: String) {
        val trimmed = command.trim()
        Log.i(TAG, "Received background voice command: \"$trimmed\"")

        if (trimmed.isBlank()) {
            resumeWakeWordDetection()
            return
        }

        // Task 12: Say "Stop" immediately stops current task and resumes listening
        if (trimmed.equals("stop", ignoreCase = true) ||
            trimmed.equals("cancel", ignoreCase = true) ||
            trimmed.equals("stop task", ignoreCase = true)
        ) {
            Log.i(TAG, "User requested STOP. Halting background action.")
            ttsManager?.stop()
            speechRecognizerManager?.stopListening()
            ttsManager?.speak("Stopped, boss.", "stop_reply")
            return
        }

        serviceScope.launch {
            try {
                val engine = responseEngine ?: LocalResponseEngine(toolRouter)
                val response = engine.generateResponse(trimmed)
                Log.i(TAG, "Generated assistant reply: \"$response\"")

                // Speak response with wake detector suppressed
                WakeWordManager.suppressDuringSpeech(true)
                ttsManager?.speak(response, "final_reply")
            } catch (e: Exception) {
                Log.e(TAG, "Error executing background voice command: ${e.message}", e)
                ttsManager?.speak("I had trouble with that command, boss.", "final_reply")
            }
        }
    }

    private fun resumeWakeWordDetection() {
        isProcessingCommand = false
        WakeWordManager.suppressDuringSpeech(false)
        if (isListening) {
            val detector = WakeWordManager.getInstance(this)
            detector.start()
            Log.i(TAG, "Wake-word detection resumed in background service.")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Assistant Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps JARVIS listening for 'Hey Jarvis' in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
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
            .setContentTitle("JARVIS AI Assistant")
            .setContentText("Listening for \"Hey Jarvis\"...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListeningInternal()
        serviceScope.cancel()
        Log.i(TAG, "JarvisWakeWordService destroyed.")
    }
}

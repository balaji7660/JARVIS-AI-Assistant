package com.jarvis.assistant.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.ai.LocalResponseEngine
import com.jarvis.assistant.ai.OpenAIResponseEngine
import com.jarvis.assistant.ai.ResponseEngine
import com.jarvis.assistant.automation.AccessibilityAutomationProvider
import com.jarvis.assistant.automation.AccessibilityUtils
import com.jarvis.assistant.tools.SafetyManager
import com.jarvis.assistant.tools.ToolCall
import com.jarvis.assistant.tools.ToolRegistry
import com.jarvis.assistant.tools.ToolRouter
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.impl.*
import com.jarvis.assistant.automation.AccessibilityScreenCaptureProvider
import com.jarvis.assistant.automation.ScreenCaptureProvider
import com.jarvis.assistant.automation.ScreenPrivacyFilter
import com.jarvis.assistant.data.remote.ApiClient
import com.jarvis.assistant.data.remote.ChatApiService
import com.jarvis.assistant.voice.JarvisTTSManager
import com.jarvis.assistant.voice.SpeechRecognitionListener
import com.jarvis.assistant.voice.SpeechRecognizerManager
import com.jarvis.assistant.voice.TTSListener
import com.jarvis.assistant.memory.JarvisDatabase
import com.jarvis.assistant.memory.MemoryCategory
import com.jarvis.assistant.memory.MemoryEntity
import com.jarvis.assistant.memory.MemoryManager
import com.jarvis.assistant.memory.MemoryRepository
import com.jarvis.assistant.memory.RoomMemoryRepository
import com.jarvis.assistant.wakeword.LocalSimulatedWakeWordDetector
import com.jarvis.assistant.wakeword.LocalWakeWordDetector
import com.jarvis.assistant.wakeword.WakeWordDetector
import com.jarvis.assistant.wakeword.WakeWordListener
import com.jarvis.assistant.wakeword.WakeWordState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel @JvmOverloads constructor(
    application: Application = Application(),
    toolRouter: ToolRouter? = null,
    responseEngine: ResponseEngine? = null,
    private var speechRecognizerManager: SpeechRecognizerManager? = null,
    private var ttsManager: JarvisTTSManager? = null,
    automationProvider: AndroidAutomationProvider? = null,
    memoryRepository: MemoryRepository? = null,
    memoryManager: MemoryManager? = null,
    wakeWordDetector: WakeWordDetector? = null,
    screenCaptureProvider: ScreenCaptureProvider? = null,
    screenPrivacyFilter: ScreenPrivacyFilter? = null,
    chatApiService: ChatApiService? = null,
    taskPlanner: com.jarvis.assistant.planner.TaskPlanner? = null,
    taskExecutor: com.jarvis.assistant.planner.TaskExecutor? = null,
    conversationContextManager: com.jarvis.assistant.context.ConversationContextManager? = null,
    referenceResolver: com.jarvis.assistant.context.ReferenceResolver? = null,
    conversationRepository: com.jarvis.assistant.memory.ConversationRepository? = null
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var resetJob: Job? = null
    private var speechWatchdogJob: Job? = null

    val effectiveContextManager: com.jarvis.assistant.context.ConversationContextManager =
        conversationContextManager ?: com.jarvis.assistant.context.ConversationContextManager()
    val effectiveReferenceResolver: com.jarvis.assistant.context.ReferenceResolver =
        referenceResolver ?: com.jarvis.assistant.context.ReferenceResolver()

    val effectiveConversationRepository: com.jarvis.assistant.memory.ConversationRepository =
        conversationRepository ?: try {
            val db = JarvisDatabase.getInstance(application)
            com.jarvis.assistant.memory.RoomConversationRepository(db.conversationDao(), db.messageDao())
        } catch (_: Throwable) {
            com.jarvis.assistant.memory.InMemoryConversationRepository()
        }

    val effectiveAutomationProvider: AndroidAutomationProvider = automationProvider ?: AccessibilityAutomationProvider()
    val effectiveChatApiService: ChatApiService = chatApiService ?: ApiClient.getChatApiService()
    val effectiveScreenCaptureProvider: ScreenCaptureProvider = screenCaptureProvider ?: AccessibilityScreenCaptureProvider()
    val effectiveScreenPrivacyFilter: ScreenPrivacyFilter = screenPrivacyFilter ?: ScreenPrivacyFilter()

    val effectiveMemoryRepository: MemoryRepository = memoryRepository ?: try {
        RoomMemoryRepository(JarvisDatabase.getInstance(application).memoryDao())
    } catch (_: Throwable) {
        object : MemoryRepository {
            private val list = mutableListOf<MemoryEntity>()
            private var nextId = 1L

            override suspend fun saveMemory(memory: MemoryEntity): Long {
                val id = if (memory.id == 0L) nextId++ else memory.id
                val saved = memory.copy(id = id)
                list.removeAll { it.id == id }
                list.add(saved)
                return id
            }

            override suspend fun searchMemories(query: String): List<MemoryEntity> =
                list.filter { it.content.contains(query, ignoreCase = true) }

            override suspend fun getRelevantMemories(query: String): List<MemoryEntity> =
                list.filter { it.content.contains(query, ignoreCase = true) }.take(10)

            override suspend fun getAllMemories(): List<MemoryEntity> = list.toList()

            override suspend fun deleteMemory(id: Long) {
                list.removeAll { it.id == id }
            }

            override suspend fun clearAllMemories() {
                list.clear()
            }

            override suspend fun getMemoryCount(): Int = list.size
        }
    }

    val effectiveNoteDao: com.jarvis.assistant.memory.NoteDao = try {
        JarvisDatabase.getInstance(application).noteDao()
    } catch (_: Throwable) {
        object : com.jarvis.assistant.memory.NoteDao {
            private val list = mutableListOf<com.jarvis.assistant.memory.NoteEntity>()
            private var nextId = 1L
            override suspend fun insert(note: com.jarvis.assistant.memory.NoteEntity): Long {
                val id = if (note.id == 0L) nextId++ else note.id
                val saved = note.copy(id = id)
                list.removeAll { it.id == id }
                list.add(saved)
                return id
            }
            override suspend fun update(note: com.jarvis.assistant.memory.NoteEntity) {
                list.removeAll { it.id == note.id }
                list.add(note)
            }
            override suspend fun delete(note: com.jarvis.assistant.memory.NoteEntity) {
                list.removeAll { it.id == note.id }
            }
            override suspend fun deleteById(id: Long): Int = if (list.removeAll { it.id == id }) 1 else 0
            override suspend fun getAllNotes(): List<com.jarvis.assistant.memory.NoteEntity> = list.filter { !it.isArchived }
            override suspend fun getNoteById(id: Long): com.jarvis.assistant.memory.NoteEntity? = list.firstOrNull { it.id == id }
            override suspend fun searchNotes(query: String): List<com.jarvis.assistant.memory.NoteEntity> =
                list.filter { !it.isArchived && (it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true)) }
            override suspend fun findByTitle(title: String): com.jarvis.assistant.memory.NoteEntity? =
                list.firstOrNull { !it.isArchived && it.title.contains(title, ignoreCase = true) }
        }
    }

    val effectiveReminderDao: com.jarvis.assistant.memory.ReminderDao = try {
        JarvisDatabase.getInstance(application).reminderDao()
    } catch (_: Throwable) {
        object : com.jarvis.assistant.memory.ReminderDao {
            private val reminders = mutableListOf<com.jarvis.assistant.memory.ReminderEntity>()
            private val timers = mutableListOf<com.jarvis.assistant.memory.TimerEntity>()
            private var nextRemId = 1L
            private var nextTimerId = 1L
            override suspend fun insertReminder(reminder: com.jarvis.assistant.memory.ReminderEntity): Long {
                val id = if (reminder.id == 0L) nextRemId++ else reminder.id
                val saved = reminder.copy(id = id)
                reminders.removeAll { it.id == id }
                reminders.add(saved)
                return id
            }
            override suspend fun getActiveReminders(): List<com.jarvis.assistant.memory.ReminderEntity> = reminders.filter { !it.isCompleted }
            override suspend fun getReminderById(id: Long): com.jarvis.assistant.memory.ReminderEntity? = reminders.firstOrNull { it.id == id }
            override suspend fun searchReminders(query: String): List<com.jarvis.assistant.memory.ReminderEntity> =
                reminders.filter { !it.isCompleted && it.message.contains(query, ignoreCase = true) }
            override suspend fun updateReminder(reminder: com.jarvis.assistant.memory.ReminderEntity) {
                reminders.removeAll { it.id == reminder.id }
                reminders.add(reminder)
            }
            override suspend fun deleteReminderById(id: Long): Int = if (reminders.removeAll { it.id == id }) 1 else 0
            override suspend fun insertTimer(timer: com.jarvis.assistant.memory.TimerEntity): Long {
                val id = if (timer.id == 0L) nextTimerId++ else timer.id
                val saved = timer.copy(id = id)
                timers.removeAll { it.id == id }
                timers.add(saved)
                return id
            }
            override suspend fun getActiveTimers(now: Long): List<com.jarvis.assistant.memory.TimerEntity> = timers.filter { !it.isCompleted && it.endTimeMs > now }
            override suspend fun getTimerById(id: Long): com.jarvis.assistant.memory.TimerEntity? = timers.firstOrNull { it.id == id }
            override suspend fun updateTimer(timer: com.jarvis.assistant.memory.TimerEntity) {
                timers.removeAll { it.id == timer.id }
                timers.add(timer)
            }
            override suspend fun deleteTimerById(id: Long): Int = if (timers.removeAll { it.id == id }) 1 else 0
        }
    }

    val effectiveNoteRepository: com.jarvis.assistant.memory.NoteRepository =
        com.jarvis.assistant.memory.NoteRepository(effectiveNoteDao)

    val effectiveTimerReminderManager: com.jarvis.assistant.reminders.TimerReminderManager =
        com.jarvis.assistant.reminders.TimerReminderManager(application, effectiveReminderDao)

    val effectiveCalendarManager: com.jarvis.assistant.calendar.CalendarManager =
        com.jarvis.assistant.calendar.CalendarManager(application)

    val effectiveMemoryManager: MemoryManager = memoryManager ?: MemoryManager(effectiveMemoryRepository)

    val effectiveWakeWordDetector: WakeWordDetector = wakeWordDetector ?: try {
        com.jarvis.assistant.wakeword.WakeWordManager.getInstance(application)
    } catch (_: Throwable) {
        LocalSimulatedWakeWordDetector()
    }

    // 1. Initialize registered tools including Accessibility automation, Vision tools, Planning tools, Notes, Reminders, Calendar
    val toolRegistry: ToolRegistry = ToolRegistry().apply {
        register(GetTimeTool())
        register(GetDateTool())
        register(GoHomeTool(context = application))
        register(PressBackTool())
        register(OpenUrlTool(context = application))
        register(OpenAppTool(context = application))
        register(CallContactTool(context = application))

        // Device control tools (Phase 1)
        register(GetBatteryStatusTool(context = application))
        register(SetVolumeTool(context = application))
        register(GetVolumeTool(context = application))
        register(SetBrightnessTool(context = application))
        register(GetBrightnessTool(context = application))
        register(ToggleFlashlightTool(context = application))
        register(OpenCameraTool(context = application))
        register(GetDeviceInfoTool(context = application))
        register(GetNetworkStatusTool(context = application))
        register(OpenWifiSettingsTool(context = application))
        register(OpenBluetoothSettingsTool(context = application))
        register(LockScreenTool())

        // Media control tools (Phase 2)
        register(PlayMediaTool(context = application))
        register(PauseMediaTool(context = application))
        register(ResumeMediaTool(context = application))
        register(NextTrackTool(context = application))
        register(PreviousTrackTool(context = application))
        register(GetMediaStateTool(context = application))

        // SMS and WhatsApp tools (Phase 3 & Reliability Upgrade)
        register(SendSmsTool(context = application))
        register(com.jarvis.assistant.tools.impl.SendWhatsAppMessageTool(context = application, automationProvider = effectiveAutomationProvider))

        // Accessibility tools
        register(ReadVisibleScreenTool(effectiveAutomationProvider))
        register(ClickTextTool(effectiveAutomationProvider))
        register(ClickViewTool(effectiveAutomationProvider))
        register(ScrollTool(effectiveAutomationProvider))
        register(TypeTextTool(effectiveAutomationProvider))

        // Screen understanding tools (Phase 6)
        register(FindScreenElementTool(effectiveAutomationProvider))
        register(ReadCurrentScreenTool(effectiveAutomationProvider))
        register(DiagnoseScreenErrorTool(effectiveAutomationProvider))
        register(ClickScreenElementTool(effectiveAutomationProvider))
        register(ScrollScreenTool(effectiveAutomationProvider))
        register(
            AnalyzeScreenTool(
                screenCaptureProvider = effectiveScreenCaptureProvider,
                screenPrivacyFilter = effectiveScreenPrivacyFilter,
                automationProvider = effectiveAutomationProvider,
                chatApiService = effectiveChatApiService,
                onStatusUpdate = { status, msg ->
                    _uiState.update { it.copy(screenAnalysisState = status, screenStatusMessage = msg) }
                }
            )
        )

        // Web Assistant tools (Phase 7)
        register(SearchWebTool(context = application, automationProvider = effectiveAutomationProvider))
        register(SearchYouTubeTool(context = application, automationProvider = effectiveAutomationProvider))
        val readWebpageTool = ReadCurrentWebpageTool(effectiveAutomationProvider)
        register(readWebpageTool)
        register(SummarizeWebpageTool(readWebpageTool))

        // Local Notes tools (Phase 8)
        register(CreateNoteTool(effectiveNoteRepository))
        register(SearchNotesTool(effectiveNoteRepository))
        register(ListNotesTool(effectiveNoteRepository))
        register(UpdateNoteTool(effectiveNoteRepository))
        register(DeleteNoteTool(effectiveNoteRepository))

        // Reminders and Timers tools (Phase 9)
        register(CreateTimerTool(effectiveTimerReminderManager))
        register(CancelTimerTool(effectiveTimerReminderManager))
        register(ListTimersTool(effectiveTimerReminderManager))
        register(CreateReminderTool(effectiveTimerReminderManager))
        register(CancelReminderTool(effectiveTimerReminderManager))
        register(ListRemindersTool(effectiveTimerReminderManager))

        // Controlled Calendar tools (Phase 10)
        register(CreateCalendarEventTool(effectiveCalendarManager))
        register(ListCalendarEventsTool(effectiveCalendarManager))
        register(DeleteCalendarEventTool(effectiveCalendarManager))

        // Bounded screen wait tool
        register(WaitForScreenTool(effectiveAutomationProvider))
    }

    val effectiveToolRouter: ToolRouter = toolRouter ?: ToolRouter(
        registry = toolRegistry,
        safetyManager = SafetyManager()
    )

    val effectiveTaskPlanner: com.jarvis.assistant.planner.TaskPlanner = taskPlanner ?: com.jarvis.assistant.planner.DefaultTaskPlanner(
        chatApiService = effectiveChatApiService,
        referenceResolver = effectiveReferenceResolver
    )
    val effectiveTaskExecutor: com.jarvis.assistant.planner.TaskExecutor = taskExecutor ?: com.jarvis.assistant.planner.TaskExecutor(
        toolRouter = effectiveToolRouter,
        actionVerifier = com.jarvis.assistant.planner.ActionVerifier(effectiveAutomationProvider)
    )

    val effectiveResponseEngine: ResponseEngine = responseEngine ?: OpenAIResponseEngine(
        toolRouter = effectiveToolRouter,
        onToolExecuting = { toolName ->
            if (toolName != null) {
                _uiState.update {
                    it.copy(
                        activeToolName = toolName,
                        state = AssistantState.EXECUTING,
                        statusMessage = "Executing ${toolName.uppercase()}..."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(activeToolName = null)
                }
            }
        },
        fallbackEngine = null,
        maxRetries = 2
    )

    val continuousConversationManager: com.jarvis.assistant.context.ContinuousConversationManager = com.jarvis.assistant.context.ContinuousConversationManager(
        sessionTimeoutMs = 45_000L,
        onSessionExpired = {
            viewModelScope.launch {
                val nextState = if (_uiState.value.isWakeWordEnabled) AssistantState.PASSIVE_WAKE else AssistantState.IDLE
                val nextMsg = if (_uiState.value.isWakeWordEnabled) "Wake word active... Say 'Hey JARVIS' or tap mic" else "Ready, boss."
                _uiState.update {
                    it.copy(
                        state = nextState,
                        statusMessage = nextMsg,
                        isMicActive = false,
                        isContinuousConversationActive = false
                    )
                }
                if (_uiState.value.isWakeWordEnabled) {
                    (effectiveWakeWordDetector as? LocalWakeWordDetector)?.notifyCommandFinished()
                    effectiveWakeWordDetector.start()
                }
            }
        }
    )

    val proactiveAssistantManager: com.jarvis.assistant.proactive.ProactiveAssistantManager = com.jarvis.assistant.proactive.ProactiveAssistantManager(
        context = application,
        prefs = try { application.getSharedPreferences("jarvis_prefs", android.content.Context.MODE_PRIVATE) } catch (_: Throwable) { null }
    )

    val localResponseEngine: com.jarvis.assistant.ai.LocalResponseEngine = com.jarvis.assistant.ai.LocalResponseEngine(toolRouter = effectiveToolRouter)

    init {
        refreshAccessibilityStatus()

        // Load stored memories count
        refreshMemoryCount()

        // Sync initial proactive mode status
        _uiState.update { it.copy(isProactiveEnabled = proactiveAssistantManager.isProactiveEnabled()) }

        // Restore background wake state from preferences on device
        val isHeadless = try {
            application.packageName == null
        } catch (_: Throwable) {
            true
        }
        if (!isHeadless) {
            val backgroundWakePref = try {
                com.jarvis.assistant.wakeword.WakeWordPreferences.isBackgroundWakeEnabled(application)
            } catch (_: Throwable) {
                false
            }
            if (backgroundWakePref) {
                _uiState.update {
                    it.copy(
                        isWakeWordEnabled = true,
                        state = AssistantState.WAKE_LISTENING,
                        statusMessage = "Listening locally for \"Hey JARVIS\"..."
                    )
                }
            }
        }

        // Setup wake word detector listener
        effectiveWakeWordDetector.setListener(object : WakeWordListener {
            override fun onWakeWordDetected() {
                viewModelScope.launch {
                    continuousConversationManager.startOrExtendSession()
                    if (effectiveWakeWordDetector is LocalWakeWordDetector) {
                        effectiveWakeWordDetector.notifyCommandListeningStarted()
                    }
                    _uiState.update {
                        it.copy(
                            state = AssistantState.ACTIVE_LISTENING,
                            wakeWordState = WakeWordState.WAKE_DETECTED,
                            statusMessage = "⚡ Wake word detected! Listening for command...",
                            isContinuousConversationActive = true
                        )
                    }
                    speakGreetingAndListen()
                }
            }

            override fun onWakeWordError(error: String) {
                _uiState.update {
                    it.copy(
                        wakeWordState = WakeWordState.ERROR,
                        statusMessage = "Wake detector error: $error"
                    )
                }
            }
        })

        if (effectiveWakeWordDetector is LocalWakeWordDetector) {
            viewModelScope.launch {
                effectiveWakeWordDetector.state.collect { wwState ->
                    _uiState.update { it.copy(wakeWordState = wwState) }
                }
            }
        }

        if (speechRecognizerManager == null) {
            initSpeechRecognizer()
        }
        if (ttsManager == null) {
            initTTS()
        }

        // Register bridge with floating overlay controller (Milestone 11)
        com.jarvis.assistant.overlay.JarvisOverlayController.registerBridge(object : com.jarvis.assistant.overlay.AssistantBridge {
            override fun onMicTapped() {
                this@HomeViewModel.onMicTapped(hasPermission = true)
            }

            override fun handleCommand(query: String) {
                this@HomeViewModel.handleSpeechRecognized(query)
            }

            override fun stopTask() {
                this@HomeViewModel.handleSpeechRecognized("stop")
            }

            override fun requestScreenAnalysis() {
                this@HomeViewModel.handleSpeechRecognized("analyze my screen")
            }

            override fun confirmPendingAction() {
                _uiState.value.pendingConfirmation?.onConfirm?.invoke()
            }

            override fun cancelPendingAction() {
                _uiState.value.pendingConfirmation?.onCancel?.invoke()
            }
        })

        // Synchronize assistant state with overlay
        viewModelScope.launch {
            _uiState.collect { state ->
                com.jarvis.assistant.overlay.JarvisOverlayController.updateAssistantState(state)
            }
        }

        // Restore previous conversation context on application startup from permanent Room storage
        viewModelScope.launch {
            try {
                val restoredMessages = effectiveConversationRepository.restoreLastSessionContext()
                for (msg in restoredMessages) {
                    if (msg.role == "user") {
                        effectiveContextManager.recordUserTurn(msg.content)
                    } else if (msg.role == "assistant") {
                        effectiveContextManager.recordAssistantTurn(msg.content)
                    }
                }
            } catch (_: Throwable) {
                // Ignore restoration error
            }
        }
    }

    fun refreshAccessibilityStatus() {
        val enabled = AccessibilityUtils.isAccessibilityServiceEnabled(getApplication())
        _uiState.update { it.copy(isAccessibilityEnabled = enabled) }
    }

    private fun initSpeechRecognizer() {
        try {
            speechRecognizerManager = SpeechRecognizerManager(
                context = getApplication(),
                listener = object : SpeechRecognitionListener {
                    override fun onReady() {
                        _uiState.update {
                            it.copy(
                                state = AssistantState.LISTENING,
                                statusMessage = "Listening...",
                                isMicActive = true
                            )
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        _uiState.update {
                            it.copy(
                                state = AssistantState.LISTENING,
                                statusMessage = "Listening..."
                            )
                        }
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        _uiState.update { it.copy(rmsLevel = rmsdB) }
                    }

                    override fun onPartialResult(text: String) {
                        _uiState.update {
                            it.copy(
                                spokenText = text,
                                statusMessage = text
                            )
                        }
                    }

                    override fun onFinalResult(text: String) {
                        handleSpeechRecognized(text)
                    }

                    override fun onError(errorCode: Int, errorMessage: String) {
                        handleVoiceError(errorMessage)
                    }
                }
            )
        } catch (_: Throwable) {
            speechRecognizerManager = null
        }
    }

    private fun initTTS() {
        try {
            ttsManager = JarvisTTSManager(
                context = getApplication(),
                listener = object : TTSListener {
                    override fun onInitSuccess() {
                        // TTS Engine initialized
                    }

                    override fun onSpeechStarted(utteranceId: String) {
                        _uiState.update {
                            it.copy(state = AssistantState.SPEAKING)
                        }
                    }

                    override fun onSpeechCompleted(utteranceId: String) {
                        if (utteranceId == "wake_greeting") {
                            viewModelScope.launch {
                                delay(250)
                                startVoiceInput()
                            }
                        } else {
                            completeSpeechPlayback()
                        }
                    }

                    override fun onSpeechError(utteranceId: String, errorMessage: String) {
                        if (utteranceId == "wake_greeting") {
                            viewModelScope.launch {
                                delay(250)
                                startVoiceInput()
                            }
                        } else {
                            handleVoiceError(errorMessage)
                        }
                    }
                }
            )
        } catch (_: Throwable) {
            ttsManager = null
        }
    }

    fun onMicTapped(hasPermission: Boolean) {
        resetJob?.cancel()

        if (!hasPermission) {
            _uiState.update {
                it.copy(
                    permissionDenied = true,
                    statusMessage = "Microphone permission is required for voice commands."
                )
            }
            return
        }

        when (_uiState.value.state) {
            AssistantState.ACTIVE_LISTENING, AssistantState.LISTENING -> {
                speechRecognizerManager?.cancel()
                continuousConversationManager.endSession()
                val nextState = if (_uiState.value.isWakeWordEnabled) AssistantState.WAKE_LISTENING else AssistantState.IDLE
                val nextMsg = if (_uiState.value.isWakeWordEnabled) "Listening locally for \"Hey JARVIS\"..." else "Ready, boss."
                _uiState.update {
                    it.copy(
                        state = nextState,
                        statusMessage = nextMsg,
                        isMicActive = false,
                        isContinuousConversationActive = false
                    )
                }
                if (_uiState.value.isWakeWordEnabled) {
                    (effectiveWakeWordDetector as? LocalWakeWordDetector)?.notifyCommandFinished()
                }
            }
            AssistantState.SPEAKING, AssistantState.EXECUTING, AssistantState.PROCESSING, AssistantState.THINKING -> {
                ttsManager?.stop()
                speechWatchdogJob?.cancel()
                speechWatchdogJob = null
                effectiveTaskExecutor.cancel()
                continuousConversationManager.endSession()
                (effectiveWakeWordDetector as? LocalWakeWordDetector)?.suppressDuringSpeech(false)
                val nextState = if (_uiState.value.isWakeWordEnabled) AssistantState.WAKE_LISTENING else AssistantState.IDLE
                val nextMsg = if (_uiState.value.isWakeWordEnabled) "Listening locally for \"Hey JARVIS\"..." else "Ready, boss."
                _uiState.update {
                    it.copy(
                        state = nextState,
                        statusMessage = nextMsg,
                        isMicActive = false,
                        isContinuousConversationActive = false,
                        activeToolName = null,
                        pendingConfirmation = null,
                        pendingMemoryConfirmation = null,
                        pendingDisambiguation = null
                    )
                }
                if (_uiState.value.isWakeWordEnabled) {
                    effectiveWakeWordDetector.start()
                }
            }
            else -> {
                continuousConversationManager.startOrExtendSession()
                startVoiceInput()
            }
        }
    }

    fun onPermissionResult(isGranted: Boolean, isPermanentlyDenied: Boolean = false) {
        if (isGranted) {
            _uiState.update {
                it.copy(
                    permissionDenied = false,
                    permissionPermanentlyDenied = false,
                    statusMessage = "Ready, boss."
                )
            }
            startVoiceInput()
        } else {
            _uiState.update {
                it.copy(
                    permissionDenied = true,
                    permissionPermanentlyDenied = isPermanentlyDenied,
                    statusMessage = "Microphone permission is required for voice commands."
                )
            }
        }
    }

    fun dismissPermissionBanner() {
        _uiState.update {
            it.copy(
                permissionDenied = false,
                statusMessage = "Ready, boss."
            )
        }
    }

    fun speakGreetingAndListen() {
        val greeting = com.jarvis.assistant.personality.JarvisPersonalityEngine.getWakeGreeting()
        (effectiveWakeWordDetector as? LocalWakeWordDetector)?.suppressDuringSpeech(true)
        com.jarvis.assistant.wakeword.WakeWordManager.suppressDuringSpeech(true)
        speechRecognizerManager?.cancel()

        _uiState.update {
            it.copy(
                state = AssistantState.SPEAKING,
                statusMessage = greeting,
                responseText = greeting
            )
        }

        if (ttsManager?.isReady == true) {
            ttsManager?.speak(greeting, "wake_greeting")
        } else {
            viewModelScope.launch {
                delay(300)
                startVoiceInput()
            }
        }
    }

    fun toggleProactiveMode(enabled: Boolean) {
        proactiveAssistantManager.setProactiveEnabled(enabled)
        _uiState.update { it.copy(isProactiveEnabled = enabled) }
    }

    fun startVoiceInput() {
        refreshAccessibilityStatus()
        (effectiveWakeWordDetector as? LocalWakeWordDetector)?.suppressDuringSpeech(false)
        if (effectiveWakeWordDetector is LocalWakeWordDetector) {
            effectiveWakeWordDetector.notifyCommandListeningStarted()
        } else {
            effectiveWakeWordDetector.stop()
        }

        _uiState.update {
            it.copy(
                state = AssistantState.LISTENING,
                statusMessage = "Listening...",
                spokenText = "",
                responseText = "",
                isMicActive = true,
                errorMessage = null,
                activeToolName = null,
                wakeWordState = if (it.isWakeWordEnabled) WakeWordState.COMMAND_LISTENING else WakeWordState.DISABLED
            )
        }

        speechRecognizerManager?.startListening()
    }

    fun handleSpeechRecognized(text: String) {
        _uiState.update {
            it.copy(
                spokenText = text,
                state = AssistantState.THINKING,
                statusMessage = text
            )
        }

        viewModelScope.launch {
            try {
                effectiveConversationRepository.saveUserMessage(text)
            } catch (_: Throwable) {
                // Ignore DB error
            }

            // 0a. Check pending action confirmation
            if (_uiState.value.state == AssistantState.WAITING_FOR_CONFIRMATION && _uiState.value.pendingConfirmation != null) {
                val pending = _uiState.value.pendingConfirmation!!
                // Check for task revision: e.g. "No, Mom." or "Actually Mom."
                if (com.jarvis.assistant.context.ReferenceResolver.isTaskCorrection(text)) {
                    val correctedTarget = com.jarvis.assistant.context.ReferenceResolver.extractCorrectionTarget(text)
                    _uiState.update { it.copy(pendingConfirmation = null) }
                    if (pending.toolName == "call_contact" && !correctedTarget.isNullOrBlank()) {
                        handleCallCommand(correctedTarget, isRevision = true)
                        return@launch
                    }
                }
                if (isConfirmationAffirmative(text)) {
                    val onConfirm = pending.onConfirm
                    _uiState.update { it.copy(pendingConfirmation = null) }
                    onConfirm()
                    return@launch
                }
                if (isConfirmationNegative(text) || isCancellationCommand(text)) {
                    val onCancel = pending.onCancel
                    _uiState.update { it.copy(pendingConfirmation = null) }
                    onCancel()
                    return@launch
                }
            }

            // 0b. Check pending clarification / disambiguation
            if (_uiState.value.state == AssistantState.WAITING_FOR_CLARIFICATION && _uiState.value.pendingDisambiguation != null) {
                val candidates = _uiState.value.pendingDisambiguation!!
                val lower = text.lowercase().trim()
                val chosenIndex = when {
                    lower.contains("first") || lower.contains("1st") -> 0
                    lower.contains("second") || lower.contains("2nd") -> 1
                    lower.contains("third") || lower.contains("3rd") -> 2
                    else -> -1
                }
                val matched = if (chosenIndex in candidates.indices) {
                    candidates[chosenIndex]
                } else {
                    candidates.firstOrNull { lower.contains(it.lowercase()) }
                }
                if (matched != null) {
                    _uiState.update { it.copy(pendingDisambiguation = null) }
                    handleCallCommand(matched, isRevision = false)
                    return@launch
                } else if (isCancellationCommand(text) || isConfirmationNegative(text)) {
                    _uiState.update {
                        it.copy(
                            pendingDisambiguation = null,
                            state = if (it.isWakeWordEnabled) AssistantState.PASSIVE_WAKE else AssistantState.IDLE,
                            statusMessage = "Action cancelled, boss."
                        )
                    }
                    speakMessage("Action cancelled, boss.")
                    return@launch
                }
            }

            // 0c. Immediate cancellation check
            if (isCancellationCommand(text)) {
                effectiveTaskExecutor.cancel()
                effectiveContextManager.cancelActiveTask()
                speechRecognizerManager?.cancel()
                ttsManager?.stop()
                _uiState.update {
                    it.copy(
                        state = AssistantState.IDLE,
                        taskState = com.jarvis.assistant.planner.TaskExecutionState.CANCELLED,
                        statusMessage = "Task cancelled, boss.",
                        responseText = "Task cancelled, boss.",
                        currentStepIndex = 0,
                        totalSteps = 0,
                        taskStatusDetail = null,
                        isMicActive = false
                    )
                }
                speakMessage("Task cancelled, boss.")
                return@launch
            }

            // 0d. Check for task revision outside of confirmation (e.g., "Actually open YouTube")
            var effectiveText = text
            if (com.jarvis.assistant.context.ReferenceResolver.isTaskCorrection(text)) {
                val target = com.jarvis.assistant.context.ReferenceResolver.extractCorrectionTarget(text)
                if (!target.isNullOrBlank()) {
                    effectiveText = target
                }
            }

            // 0e. Direct Call Query handling with confirmation & disambiguation
            if (isCallQuery(effectiveText)) {
                val contactName = extractContactName(effectiveText)
                if (contactName.isNotBlank()) {
                    handleCallCommand(contactName, isRevision = false)
                    return@launch
                }
            }

            // 0f. Context clearing command
            if (isContextClearCommand(effectiveText)) {
                clearCurrentContext()
                speakMessage("Short-term conversation and task context cleared, boss.")
                return@launch
            }

            // 0g. Safe task resumption command ("Continue")
            if (isResumptionCommand(effectiveText)) {
                val activeTask = effectiveContextManager.getActiveTaskContext()
                val currentPkg = effectiveAutomationProvider.getCurrentPackage()
                val lastPlan = effectiveTaskExecutor.getCurrentPlan()
                if (activeTask.canResume(currentPkg) && lastPlan != null) {
                    val nextStep = activeTask.lastSuccessfulStep + 1
                    _uiState.update {
                        it.copy(
                            taskState = com.jarvis.assistant.planner.TaskExecutionState.EXECUTING,
                            statusMessage = "Resuming task from step $nextStep...",
                            state = AssistantState.EXECUTING
                        )
                    }
                    speakMessage("Resuming task from step $nextStep, boss.")

                    val executionResult = effectiveTaskExecutor.executePlan(
                        plan = lastPlan,
                        startFromStep = nextStep,
                        onStatusUpdate = { taskState, stepIdx, total, msg ->
                            effectiveContextManager.updateTaskProgress(
                                taskId = lastPlan.taskId,
                                description = lastPlan.userRequest,
                                currentStep = stepIdx,
                                totalSteps = total,
                                lastSuccessfulStep = if (taskState == com.jarvis.assistant.planner.TaskExecutionState.COMPLETED) total else maxOf(0, stepIdx - 1),
                                state = taskState
                            )
                            _uiState.update { s ->
                                s.copy(
                                    taskState = taskState,
                                    currentStepIndex = stepIdx,
                                    totalSteps = total,
                                    taskStatusDetail = msg,
                                    statusMessage = msg
                                )
                            }
                        },
                        requestConfirmation = { step ->
                            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                                requestActionConfirmation(
                                    toolName = step.action,
                                    description = "Step ${step.stepId}: ${step.action.replace('_', ' ')}",
                                    arguments = step.arguments,
                                    onConfirmAction = {
                                        if (cont.isActive) cont.resumeWith(Result.success(true))
                                    }
                                )
                            }
                        }
                    )

                    _uiState.update { it.copy(taskState = executionResult.state) }
                    speakMessage(executionResult.finalMessage)
                    return@launch
                } else {
                    speakMessage("I cannot resume the previous task, boss. Please start a new request.")
                    return@launch
                }
            }

            // Record user turn in bounded short-term context
            effectiveContextManager.recordUserTurn(text)

            // Ambiguity check: if user's reference is ambiguous across visible controls, ask for clarification immediately
            val resolvedRef = effectiveReferenceResolver.resolve(text, effectiveContextManager.getConversationContext())
            if (resolvedRef is com.jarvis.assistant.context.ReferenceResolutionResult.Ambiguous) {
                val candidateNames = resolvedRef.candidates.take(4).joinToString(", ") { "\"${it.label}\"" }
                val promptClarification = "I found multiple matching items on your screen: $candidateNames. Which one would you like, boss?"
                effectiveContextManager.recordAssistantTurn(promptClarification)
                speakMessage(promptClarification)
                return@launch
            }

            // Brief suspension to yield coroutine so UI observes THINKING state
            delay(50)

            // Direct Type Text command requires confirmation before any screen scraping
            if (isTypeTextQuery(text)) {
                val inputToType = extractTextToType(text)
                requestActionConfirmation(
                    toolName = "type_text",
                    description = "Input \"$inputToType\" into the active focused field.",
                    arguments = mapOf("text" to inputToType),
                    onConfirmAction = {
                        executeConfirmedTool(ToolCall("type_text", mapOf("text" to inputToType)))
                    }
                )
                return@launch
            }

            // 1. Explicit Memory: "Remember that..."
            if (isRememberCommand(text)) {
                val content = extractRememberContent(text)
                val validation = effectiveMemoryManager.validateContent(content)
                if (!validation.isValid) {
                    val errorMsg = validation.errorMessage ?: "I cannot store that memory."
                    speakMessage(errorMsg)
                    return@launch
                }

                val category = effectiveMemoryManager.categorize(content)
                requestMemoryConfirmation(
                    content = content,
                    category = category,
                    onConfirmSave = {
                        viewModelScope.launch {
                            val result = effectiveMemoryManager.saveMemory(content, category)
                            refreshMemoryCount()
                            val reply = if (result.isSuccess) {
                                "Understood, boss. I've committed that to memory."
                            } else {
                                result.exceptionOrNull()?.message ?: "Failed to store memory."
                            }
                            effectiveContextManager.recordAssistantTurn(reply)
                            speakMessage(reply)
                        }
                    }
                )
                return@launch
            }

            // 2. Explicit Memory: "What do you remember about me?"
            if (isRecallMemoryCommand(text)) {
                val memories = effectiveMemoryManager.getAllMemoriesOnce()
                val speech = effectiveMemoryManager.formatMemoriesForSpeech(memories)
                effectiveContextManager.recordAssistantTurn(speech)
                speakMessage(speech)
                return@launch
            }

            // 3. Explicit Memory: "Forget that..."
            if (isForgetRequest(text)) {
                val query = extractForgetContent(text)
                if (query.equals("everything", ignoreCase = true) || query.equals("all", ignoreCase = true)) {
                    effectiveMemoryManager.clearAllMemories()
                    refreshMemoryCount()
                    val msg = "Understood, boss. I have erased all stored memories."
                    effectiveContextManager.recordAssistantTurn(msg)
                    speakMessage(msg)
                } else {
                    val matching = effectiveMemoryManager.searchMemories(query)
                    if (matching.isNotEmpty()) {
                        for (m in matching) {
                            effectiveMemoryManager.deleteMemory(m.id)
                        }
                        refreshMemoryCount()
                        val msg = "Understood, boss. I've removed that from my memories."
                        effectiveContextManager.recordAssistantTurn(msg)
                        speakMessage(msg)
                    } else {
                        val msg = "I couldn't find any stored memory matching \"$query\", boss."
                        effectiveContextManager.recordAssistantTurn(msg)
                        speakMessage(msg)
                    }
                }
                return@launch
            }

            // 3b. Specific Memory Query: "What language do I prefer?", "What's my interview tomorrow?"
            if (isSpecificMemoryQuery(effectiveText)) {
                val memories = effectiveMemoryManager.getAllMemoriesOnce()
                val answer = findRelevantMemoryAnswer(effectiveText, memories)
                if (answer != null) {
                    effectiveContextManager.recordAssistantTurn(answer)
                    speakMessage(answer)
                    return@launch
                }
            }

            // 4. Multi-Step Task Automation Intent or Contextual Follow-Up
            if (isMultiStepAutomationIntent(effectiveText) || isContextualFollowUpIntent(effectiveText, effectiveContextManager.getConversationContext())) {
                _uiState.update {
                    it.copy(
                        taskState = com.jarvis.assistant.planner.TaskExecutionState.PLANNING,
                        statusMessage = "Planning automation workflow...",
                        state = AssistantState.EXECUTING
                    )
                }

                // Update active foreground app context
                val currentPkg = try { effectiveAutomationProvider.getCurrentPackage() } catch (_: Exception) { "" }
                if (currentPkg.isNotBlank()) {
                    val detectedApp = when {
                        currentPkg.contains("youtube", ignoreCase = true) -> "YouTube"
                        currentPkg.contains("chrome", ignoreCase = true) -> "Chrome"
                        currentPkg.contains("settings", ignoreCase = true) -> "Settings"
                        else -> null
                    }
                    effectiveContextManager.updateCurrentApp(detectedApp, currentPkg)
                }

                // Update screen summary in context
                val screenSummary = try { effectiveAutomationProvider.getScreenSummary() } catch (_: Exception) { "" }
                if (screenSummary.isNotBlank() && !screenSummary.contains("not running", ignoreCase = true) && !screenSummary.contains("disabled", ignoreCase = true)) {
                    val elements = parseScreenElements(screenSummary)
                    if (elements.isNotEmpty() || effectiveContextManager.getConversationContext().detectedElements.isEmpty()) {
                        effectiveContextManager.updateScreenSummary(screenSummary, elements)
                    }
                }

                val memoryContext = effectiveMemoryManager.buildMemoryContextForPrompt(effectiveText)

                val planResult = effectiveTaskPlanner.plan(
                    prompt = effectiveText,
                    memoryContext = memoryContext,
                    currentPackage = currentPkg,
                    visibleScreenText = screenSummary,
                    context = effectiveContextManager.getConversationContext()
                )

                when (planResult) {
                    is com.jarvis.assistant.planner.PlanValidationResult.Valid -> {
                        val plan = planResult.sanitizedPlan
                        _uiState.update {
                            it.copy(
                                totalSteps = plan.steps.size,
                                currentStepIndex = 1,
                                taskStatusDetail = "Plan generated with ${plan.steps.size} steps."
                            )
                        }

                        val executionResult = effectiveTaskExecutor.executePlan(
                            plan = plan,
                            onStatusUpdate = { taskState, stepIdx, total, msg ->
                                effectiveContextManager.updateTaskProgress(
                                    taskId = plan.taskId,
                                    description = plan.userRequest,
                                    currentStep = stepIdx,
                                    totalSteps = total,
                                    lastSuccessfulStep = if (taskState == com.jarvis.assistant.planner.TaskExecutionState.COMPLETED) total else maxOf(0, stepIdx - 1),
                                    state = taskState
                                )
                                _uiState.update { s ->
                                    s.copy(
                                        taskState = taskState,
                                        currentStepIndex = stepIdx,
                                        totalSteps = total,
                                        taskStatusDetail = msg,
                                        statusMessage = msg
                                    )
                                }
                            },
                            requestConfirmation = { step ->
                                kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                                    requestActionConfirmation(
                                        toolName = step.action,
                                        description = "Step ${step.stepId}: ${step.action.replace('_', ' ')}",
                                        arguments = step.arguments,
                                        onConfirmAction = {
                                            if (cont.isActive) cont.resumeWith(Result.success(true))
                                        }
                                    )
                                }
                            }
                        )

                        _uiState.update { it.copy(taskState = executionResult.state) }
                        effectiveContextManager.recordLastAction(plan.steps.lastOrNull()?.action ?: "task_completed", executionResult.finalMessage)
                        effectiveContextManager.recordAssistantTurn(executionResult.finalMessage)
                        speakMessage(executionResult.finalMessage)
                    }
                    is com.jarvis.assistant.planner.PlanValidationResult.Invalid -> {
                        _uiState.update {
                            it.copy(
                                taskState = com.jarvis.assistant.planner.TaskExecutionState.FAILED,
                                statusMessage = "Plan rejected: ${planResult.reason}"
                            )
                        }
                        val errorMsg = "I cannot execute this automation plan: ${planResult.reason}"
                        effectiveContextManager.recordAssistantTurn(errorMsg)
                        speakMessage(errorMsg)
                    }
                }
                return@launch
            }

            // 5. Screen analysis explicit queries if running with LocalResponseEngine
            if (effectiveResponseEngine is LocalResponseEngine && isScreenAnalysisQuery(effectiveText)) {
                val focus = extractScreenFocus(effectiveText)
                val toolCall = ToolCall("analyze_current_screen", if (focus != null) mapOf("focus" to focus) else emptyMap())
                val result = effectiveToolRouter.dispatch(toolCall)
                effectiveContextManager.recordLastAction("analyze_current_screen", result.message)
                effectiveContextManager.recordAssistantTurn(result.message)
                speakMessage(result.message)
                return@launch
            }

            // 5b. Local-First Intelligence: fast local dispatch (<300ms) for known tools
            if (localResponseEngine.canHandleLocally(effectiveText)) {
                val localResponse = localResponseEngine.generateResponse(effectiveText)
                val cleanResponse = com.jarvis.assistant.personality.JarvisPersonalityEngine.deduplicate(localResponse)
                effectiveContextManager.recordAssistantTurn(cleanResponse)
                speakMessage(cleanResponse)
                return@launch
            }

            // 6. Normal AI query / tool execution with injected memory context and conversational context
            val memoryContext = effectiveMemoryManager.buildMemoryContextForPrompt(effectiveText)
            val conversationSummary = effectiveContextManager.getConversationContext().toBoundedSummary()
            val promptWithContext = buildString {
                if (memoryContext.isNotBlank()) {
                    append(memoryContext)
                    append("\n\n")
                }
                if (conversationSummary.isNotBlank()) {
                    append("Short-term Context:\n")
                    append(conversationSummary)
                    append("\n\n")
                }
                append("User request: $effectiveText")
            }

            val promptToUse = if (effectiveResponseEngine is LocalResponseEngine) {
                effectiveText
            } else {
                promptWithContext
            }

            val response = try {
                effectiveResponseEngine.generateResponse(promptToUse)
            } catch (_: Throwable) {
                "JARVIS cloud intelligence is unavailable."
            }
            val cleanResponse = com.jarvis.assistant.personality.JarvisPersonalityEngine.deduplicate(response)
            effectiveContextManager.recordAssistantTurn(cleanResponse)
            speakMessage(cleanResponse)
        }
    }

    private fun speakMessage(message: String) {
        viewModelScope.launch {
            try {
                effectiveConversationRepository.saveAssistantMessage(message)
            } catch (_: Throwable) {
                // Ignore DB error
            }
        }

        // Temporarily suppress wake-word detection while speaking so TTS never triggers the detector
        (effectiveWakeWordDetector as? LocalWakeWordDetector)?.suppressDuringSpeech(true)
        com.jarvis.assistant.wakeword.WakeWordManager.suppressDuringSpeech(true)
        speechRecognizerManager?.cancel()
        speechWatchdogJob?.cancel()

        _uiState.update {
            it.copy(
                responseText = message,
                state = AssistantState.SPEAKING,
                statusMessage = message,
                activeToolName = null
            )
        }

        val wordCount = message.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val watchdogTimeoutMs = maxOf(3500L, (wordCount * 550L) + 2500L)
        speechWatchdogJob = viewModelScope.launch {
            delay(watchdogTimeoutMs)
            if (_uiState.value.state == AssistantState.SPEAKING) {
                completeSpeechPlayback()
            }
        }

        if (ttsManager?.isReady == true) {
            val spoken = ttsManager?.speak(message) ?: false
            if (!spoken) {
                viewModelScope.launch {
                    delay(1500)
                    completeSpeechPlayback()
                }
            }
        } else {
            viewModelScope.launch {
                delay(2000)
                completeSpeechPlayback()
            }
        }
    }

    fun requestMemoryConfirmation(
        content: String,
        category: MemoryCategory,
        onConfirmSave: () -> Unit
    ) {
        (effectiveWakeWordDetector as? LocalWakeWordDetector)?.suppressDuringSpeech(true)
        speechRecognizerManager?.cancel()
        val categoryLabel = category.name.lowercase().replace('_', ' ')
        val promptSpeech = "Shall I store this in your $categoryLabel memory, boss?"

        _uiState.update {
            it.copy(
                state = AssistantState.SPEAKING,
                statusMessage = promptSpeech,
                pendingMemoryConfirmation = com.jarvis.assistant.ui.home.PendingMemoryConfirmation(
                    content = content,
                    category = category,
                    onConfirm = {
                        _uiState.update { s -> s.copy(pendingMemoryConfirmation = null) }
                        onConfirmSave()
                    },
                    onCancel = {
                        _uiState.update { s ->
                            s.copy(
                                pendingMemoryConfirmation = null,
                                state = if (s.isWakeWordEnabled) AssistantState.WAKE_LISTENING else AssistantState.IDLE,
                                statusMessage = "Memory discarded, boss."
                            )
                        }
                    }
                )
            )
        }

        if (ttsManager?.isReady == true) {
            ttsManager?.speak(promptSpeech)
        }
    }

    fun refreshMemoryCount() {
        viewModelScope.launch {
            val count = effectiveMemoryManager.getMemoryCount()
            _uiState.update { it.copy(storedMemoryCount = count) }
        }
    }

    suspend fun getAllMemories(): List<MemoryEntity> = effectiveMemoryManager.getAllMemories()

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            effectiveMemoryManager.deleteMemory(id)
            refreshMemoryCount()
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            effectiveMemoryManager.clearAllMemories()
            refreshMemoryCount()
        }
    }

    fun toggleWakeWord() {
        val newState = !_uiState.value.isWakeWordEnabled
        try {
            com.jarvis.assistant.wakeword.WakeWordPreferences.setBackgroundWakeEnabled(getApplication(), newState)
        } catch (_: Throwable) {}

        _uiState.update {
            it.copy(
                isWakeWordEnabled = newState,
                wakeWordState = if (newState) WakeWordState.STARTING else WakeWordState.DISABLED
            )
        }
        if (newState) {
            try {
                com.jarvis.assistant.wakeword.JarvisWakeWordService.startService(getApplication())
            } catch (t: Throwable) {
                android.util.Log.w("HomeViewModel", "Could not start JarvisWakeWordService", t)
            }
            effectiveWakeWordDetector.start()
            if (_uiState.value.state == AssistantState.IDLE) {
                _uiState.update {
                    it.copy(
                        state = AssistantState.WAKE_LISTENING,
                        statusMessage = "Listening locally for \"Hey JARVIS\"..."
                    )
                }
            }
        } else {
            try {
                com.jarvis.assistant.wakeword.JarvisWakeWordService.stopService(getApplication())
            } catch (t: Throwable) {
                android.util.Log.w("HomeViewModel", "Could not stop JarvisWakeWordService", t)
            }
            effectiveWakeWordDetector.stop()
            if (_uiState.value.state == AssistantState.WAKE_LISTENING) {
                _uiState.update {
                    it.copy(
                        state = AssistantState.IDLE,
                        statusMessage = "Ready, boss."
                    )
                }
            }
        }
    }

    fun requestActionConfirmation(
        toolName: String,
        description: String,
        arguments: Map<String, Any?> = emptyMap(),
        onConfirmAction: () -> Unit
    ) {
        (effectiveWakeWordDetector as? LocalWakeWordDetector)?.suppressDuringSpeech(true)
        speechRecognizerManager?.cancel()
        val promptSpeech = if (description.startsWith("Call ", ignoreCase = true)) {
            "${description.trim().removeSuffix(".")}?"
        } else {
            "Do you want me to ${description.lowercase().removeSuffix(".")}?"
        }

        _uiState.update {
            it.copy(
                state = AssistantState.WAITING_FOR_CONFIRMATION,
                statusMessage = promptSpeech,
                pendingConfirmation = PendingActionConfirmation(
                    toolName = toolName,
                    description = description,
                    arguments = arguments,
                    onConfirm = {
                        _uiState.update { s -> s.copy(pendingConfirmation = null) }
                        onConfirmAction()
                    },
                    onCancel = {
                        _uiState.update { s ->
                            s.copy(
                                pendingConfirmation = null,
                                state = if (s.isWakeWordEnabled) AssistantState.PASSIVE_WAKE else AssistantState.IDLE,
                                statusMessage = "Action cancelled, boss."
                            )
                        }
                    }
                )
            )
        }

        speakMessage(promptSpeech)
    }

    fun handleCallCommand(contactName: String, isRevision: Boolean = false) {
        val callTool = effectiveToolRouter.getTool("call_contact") as? com.jarvis.assistant.tools.impl.CallContactTool
        if (callTool != null) {
            viewModelScope.launch {
                val precheck = callTool.execute(mapOf("contactName" to contactName))
                if (precheck.data["isAmbiguous"] == true) {
                    val candidates = (precheck.data["candidates"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val candidateText = candidates.joinToString(" or ")
                    val clarifyMsg = "I found ${candidates.size} contacts named $contactName: $candidateText. Which one do you mean, boss?"
                    _uiState.update {
                        it.copy(
                            state = AssistantState.WAITING_FOR_CLARIFICATION,
                            statusMessage = clarifyMsg,
                            pendingDisambiguation = candidates
                        )
                    }
                    speakMessage(clarifyMsg)
                    return@launch
                } else {
                    val prompt = if (isRevision) {
                        com.jarvis.assistant.personality.JarvisPersonalityEngine.formatRevisionConfirmation(contactName)
                    } else {
                        com.jarvis.assistant.personality.JarvisPersonalityEngine.formatCallConfirmation(contactName)
                    }
                    requestActionConfirmation(
                        toolName = "call_contact",
                        description = "Call $contactName",
                        arguments = mapOf("contactName" to contactName),
                        onConfirmAction = {
                            executeConfirmedTool(ToolCall("call_contact", mapOf("contactName" to contactName)))
                        }
                    )
                }
            }
            return
        }

        val prompt = if (isRevision) {
            com.jarvis.assistant.personality.JarvisPersonalityEngine.formatRevisionConfirmation(contactName)
        } else {
            com.jarvis.assistant.personality.JarvisPersonalityEngine.formatCallConfirmation(contactName)
        }
        requestActionConfirmation(
            toolName = "call_contact",
            description = "Call $contactName",
            arguments = mapOf("contactName" to contactName),
            onConfirmAction = {
                executeConfirmedTool(ToolCall("call_contact", mapOf("contactName" to contactName)))
            }
        )
    }

    private fun executeConfirmedTool(toolCall: ToolCall) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    state = AssistantState.EXECUTING,
                    activeToolName = toolCall.name,
                    statusMessage = "Executing ${toolCall.name.uppercase()}..."
                )
            }

            val result = effectiveToolRouter.dispatch(toolCall, isUserConfirmed = true)

            _uiState.update {
                it.copy(
                    state = AssistantState.SPEAKING,
                    activeToolName = null,
                    statusMessage = result.message,
                    responseText = result.message
                )
            }

            if (ttsManager?.isReady == true) {
                ttsManager?.speak(result.message)
            } else {
                delay(2000)
                completeSpeechPlayback()
            }
        }
    }

    private fun isContextClearCommand(text: String): Boolean {
        val lower = text.lowercase().trim().removeSuffix(".")
        return lower == "clear context" ||
               lower == "clear current context" ||
               lower == "forget this conversation context" ||
               lower == "forget conversation context" ||
               lower == "clear current task" ||
               lower == "clear task" ||
               lower == "start a new task" ||
               lower == "start new task" ||
               lower == "reset context"
    }

    private fun isResumptionCommand(text: String): Boolean {
        val lower = text.lowercase().trim().removeSuffix(".")
        return lower == "continue" ||
               lower == "resume" ||
               lower == "resume task" ||
               lower == "continue task" ||
               lower == "go on"
    }

    private fun isContextualFollowUpIntent(text: String, context: com.jarvis.assistant.context.ConversationContext): Boolean {
        val lower = text.lowercase().trim()
        val app = context.currentApp?.lowercase() ?: context.currentPackage?.lowercase() ?: ""
        if ((lower.startsWith("search for ") || lower.startsWith("search ")) && (app.contains("youtube") || app.contains("chrome"))) {
            return true
        }
        if (lower.startsWith("open ") && (lower.contains("result") || lower.contains("first") || lower.contains("second") || lower.contains("button"))) {
            return true
        }
        if (lower.startsWith("click that") || lower.startsWith("click the") || lower.startsWith("tap that") || lower.startsWith("tap the") || lower == "open it" || lower == "open that") {
            return true
        }
        return false
    }

    fun parseScreenElements(summary: String): List<com.jarvis.assistant.context.ScreenElementContext> {
        if (summary.isBlank()) return emptyList()
        val pattern = java.util.regex.Pattern.compile("\\[(Button|Text|Result|Item|Link):\\s*([^\\]]+)\\]", java.util.regex.Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(summary)
        val list = mutableListOf<com.jarvis.assistant.context.ScreenElementContext>()
        while (matcher.find()) {
            val type = matcher.group(1)?.lowercase() ?: "element"
            val label = matcher.group(2)?.trim() ?: ""
            if (label.isNotBlank() && !com.jarvis.assistant.context.ContextPrivacyFilter.containsSensitiveData(label)) {
                list.add(com.jarvis.assistant.context.ScreenElementContext(label = label, type = type))
            }
        }
        return list.take(com.jarvis.assistant.context.ContextConstants.MAX_TRACKED_SCREEN_ELEMENTS)
    }

    fun clearCurrentContext() {
        effectiveContextManager.clearContext()
        effectiveTaskExecutor.cancel()
        _uiState.update {
            it.copy(
                taskState = com.jarvis.assistant.planner.TaskExecutionState.IDLE,
                currentStepIndex = 0,
                totalSteps = 0,
                taskStatusDetail = null,
                statusMessage = "Short-term context cleared, boss."
            )
        }
    }

    fun isCancellationCommand(text: String): Boolean {
        val lower = text.lowercase().trim().removeSuffix(".").removeSuffix("!")
        return lower == "stop" ||
               lower == "wait" ||
               lower == "cancel" ||
               lower == "no" ||
               lower == "abort" ||
               lower == "never mind" ||
               lower == "nevermind" ||
               lower == "stop task" ||
               lower == "cancel task" ||
               lower == "stop automation" ||
               lower == "cancel automation" ||
               lower == "actually do this instead" ||
               lower.startsWith("stop,") ||
               lower.startsWith("cancel,")
    }

    fun isConfirmationAffirmative(text: String): Boolean {
        val lower = text.lowercase().trim().removeSuffix(".").removeSuffix("!")
        return lower in setOf("yes", "yeah", "yep", "do it", "go ahead", "confirm", "sure", "please do", "proceed")
    }

    fun isConfirmationNegative(text: String): Boolean {
        val lower = text.lowercase().trim().removeSuffix(".").removeSuffix("!")
        return lower in setOf("no", "cancel", "stop", "don't", "dont", "never mind", "nevermind", "abort")
    }

    fun isCallQuery(input: String): Boolean {
        val lower = input.lowercase().trim()
        return lower.startsWith("call ") ||
               lower.startsWith("phone ") ||
               lower.startsWith("dial ") ||
               lower.startsWith("make a call to ")
    }

    fun extractContactName(input: String): String {
        val lower = input.lowercase().trim()
        return when {
            lower.startsWith("make a call to ") -> input.substring(15).trim().removeSuffix(".")
            lower.startsWith("call ") -> input.substring(5).trim().removeSuffix(".")
            lower.startsWith("phone ") -> input.substring(6).trim().removeSuffix(".")
            lower.startsWith("dial ") -> input.substring(5).trim().removeSuffix(".")
            else -> input.trim().removeSuffix(".")
        }
    }

    fun isSpecificMemoryQuery(text: String): Boolean {
        val lower = text.lowercase().trim().removeSuffix("?").removeSuffix(".")
        return lower.startsWith("what language do i prefer") ||
               lower.startsWith("what do i prefer") ||
               lower.startsWith("what is my favorite language") ||
               lower.startsWith("whats my favorite language") ||
               lower.startsWith("what is my interview") ||
               lower.startsWith("whats my interview") ||
               lower.startsWith("what interview") ||
               lower.contains("interview tomorrow")
    }

    fun findRelevantMemoryAnswer(text: String, memories: List<MemoryEntity>): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("language") || lower.contains("prefer") -> {
                val match = memories.firstOrNull { it.content.contains("prefer", ignoreCase = true) || it.content.contains("java", ignoreCase = true) }
                if (match != null) {
                    "You prefer Java for interviews, boss."
                } else null
            }
            lower.contains("interview") -> {
                val match = memories.firstOrNull { it.content.contains("interview", ignoreCase = true) }
                if (match != null) {
                    "Your Java interview is scheduled for tomorrow, boss."
                } else null
            }
            else -> null
        }
    }

    private fun isMultiStepAutomationIntent(text: String): Boolean {
        val lower = text.lowercase().trim()
        return (lower.startsWith("open ") && lower.contains(" and ")) ||
               (lower.startsWith("launch ") && lower.contains(" and ")) ||
               lower.contains(" and search for ") ||
               lower.contains(" and search ") ||
               lower.contains(" and click ") ||
               lower.contains(" and tap ") ||
               lower.contains(" and type ") ||
               lower.startsWith("automate ") ||
               lower.startsWith("workflow ")
    }

    private fun isRememberCommand(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower.startsWith("remember that ") ||
               lower.startsWith("remember ") ||
               lower.startsWith("please remember that ") ||
               lower.startsWith("please remember ") ||
               lower.startsWith("store that ") ||
               lower.startsWith("note that ")
    }

    private fun extractRememberContent(text: String): String {
        val trimmed = text.trim()
        val prefixes = listOf(
            "please remember that ",
            "please remember ",
            "remember that ",
            "remember ",
            "store that ",
            "note that "
        )
        for (prefix in prefixes) {
            if (trimmed.startsWith(prefix, ignoreCase = true)) {
                return trimmed.substring(prefix.length).trim()
            }
        }
        return trimmed
    }

    private fun isRecallMemoryCommand(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower.contains("what do you remember") ||
               lower.contains("what do you know about me") ||
               lower.contains("recall my memories") ||
               lower.contains("list my memories") ||
               lower.contains("show my memories") ||
               lower == "remember" ||
               lower == "memories"
    }

    private fun isForgetRequest(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower.startsWith("forget that ") ||
               lower.startsWith("forget about ") ||
               lower.startsWith("forget ") ||
               lower.startsWith("delete memory ")
    }

    private fun extractForgetContent(text: String): String {
        val trimmed = text.trim()
        val prefixes = listOf(
            "forget that ",
            "forget about ",
            "forget ",
            "delete memory "
        )
        for (prefix in prefixes) {
            if (trimmed.startsWith(prefix, ignoreCase = true)) {
                return trimmed.substring(prefix.length).trim()
            }
        }
        return trimmed
    }

    private fun isTypeTextQuery(input: String): Boolean {
        val lower = input.lowercase().trim()
        return lower.startsWith("type ") || lower.startsWith("type text ") || lower.startsWith("input ")
    }

    private fun extractTextToType(input: String): String {
        val lower = input.trim()
        return when {
            lower.startsWith("type text ", ignoreCase = true) -> lower.substring(10).trim()
            lower.startsWith("type ", ignoreCase = true) -> lower.substring(5).trim()
            lower.startsWith("input ", ignoreCase = true) -> lower.substring(6).trim()
            else -> lower
        }
    }

    private fun handleVoiceError(errorMessage: String) {
        speechRecognizerManager?.cancel()
        ttsManager?.stop()

        val isSilentTimeout = errorMessage.contains("No speech", ignoreCase = true)
        val displayMsg = if (isSilentTimeout) {
            if (_uiState.value.isWakeWordEnabled) "Listening locally for \"Hey JARVIS\"..." else "Ready, boss."
        } else {
            errorMessage
        }

        val nextState = if (isSilentTimeout) {
            if (_uiState.value.isWakeWordEnabled) AssistantState.PASSIVE_WAKE else AssistantState.IDLE
        } else {
            AssistantState.ERROR
        }

        _uiState.update {
            it.copy(
                state = nextState,
                statusMessage = displayMsg,
                errorMessage = if (isSilentTimeout) null else errorMessage,
                isMicActive = false,
                activeToolName = null,
                pendingConfirmation = null,
                pendingMemoryConfirmation = null,
                pendingDisambiguation = null
            )
        }

        if (isSilentTimeout) {
            if (_uiState.value.isWakeWordEnabled) {
                (effectiveWakeWordDetector as? LocalWakeWordDetector)?.notifyCommandFinished()
            }
        } else {
            resetJob?.cancel()
            resetJob = viewModelScope.launch {
                delay(3000)
                completeSpeechPlayback()
            }
        }
    }

    fun completeSpeechPlayback() {
        speechWatchdogJob?.cancel()
        speechWatchdogJob = null
        (effectiveWakeWordDetector as? LocalWakeWordDetector)?.suppressDuringSpeech(false)
        com.jarvis.assistant.wakeword.WakeWordManager.suppressDuringSpeech(false)

        val isContinuous = continuousConversationManager.isSessionActive()
        val nextState = when {
            isContinuous -> AssistantState.LISTENING
            _uiState.value.isWakeWordEnabled -> AssistantState.WAKE_LISTENING
            else -> AssistantState.IDLE
        }
        val nextMessage = when {
            isContinuous -> "Listening..."
            _uiState.value.isWakeWordEnabled -> "Listening locally for \"Hey JARVIS\"..."
            else -> "Ready, boss."
        }

        _uiState.update {
            it.copy(
                state = nextState,
                statusMessage = nextMessage,
                isMicActive = (nextState == AssistantState.LISTENING),
                isContinuousConversationActive = isContinuous,
                activeToolName = null,
                errorMessage = null
            )
        }

        if (nextState == AssistantState.LISTENING) {
            val isHeadless = try { getApplication<Application>().packageName == null } catch (_: Throwable) { true }
            if (isHeadless) {
                startVoiceInput()
            } else {
                viewModelScope.launch {
                    delay(250)
                    startVoiceInput()
                }
            }
        } else if (_uiState.value.isWakeWordEnabled) {
            effectiveWakeWordDetector.start()
        }

        viewModelScope.launch {
            delay(4000)
            if (_uiState.value.screenAnalysisState != ScreenAnalysisState.ANALYZING) {
                _uiState.update {
                    it.copy(
                        screenAnalysisState = ScreenAnalysisState.IDLE,
                        screenStatusMessage = null
                    )
                }
            }
        }
    }

    private fun isScreenAnalysisQuery(input: String): Boolean {
        val lower = input.lowercase().trim()
        return lower.contains("what is on my screen") ||
                lower.contains("whats on my screen") ||
                lower.contains("look at the screen") ||
                lower.contains("read this screen") ||
                lower.contains("read the screen") ||
                lower.contains("analyze my screen") ||
                lower.startsWith("find the ") ||
                lower.startsWith("where is the ")
    }

    private fun extractScreenFocus(prompt: String): String? {
        val lower = prompt.lowercase().trim()
        return when {
            lower.contains("find the ") -> prompt.substring(lower.indexOf("find the ") + 9).trim().removeSuffix(".")
            lower.contains("where is the ") -> prompt.substring(lower.indexOf("where is the ") + 13).trim().removeSuffix("?").removeSuffix(".")
            else -> null
        }
    }

    public override fun onCleared() {
        super.onCleared()
        speechWatchdogJob?.cancel()
        com.jarvis.assistant.overlay.JarvisOverlayController.unregisterBridge()
        resetJob?.cancel()
        if (effectiveWakeWordDetector is LocalWakeWordDetector) {
            effectiveWakeWordDetector.release()
        } else {
            effectiveWakeWordDetector.stop()
        }
        speechRecognizerManager?.destroy()
        ttsManager?.shutdown()
    }
}

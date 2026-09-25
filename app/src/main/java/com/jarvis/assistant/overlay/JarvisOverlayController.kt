package com.jarvis.assistant.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.planner.TaskExecutionState
import com.jarvis.assistant.ui.home.AssistantState
import com.jarvis.assistant.ui.home.HomeUiState
import com.jarvis.assistant.ui.home.ScreenAnalysisState
import com.jarvis.assistant.wakeword.WakeWordState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Visual states supported by the floating JARVIS orb matching requirement 2.
 */
enum class OrbVisualState(val label: String) {
    PASSIVE("PASSIVE"),
    IDLE("JARVIS READY"),
    LISTENING("LISTENING"),
    PROCESSING("PROCESSING"),
    ANALYZING("ANALYZING SCREEN"),
    EXECUTING("EXECUTING"),
    SPEAKING("SPEAKING"),
    CONFIRMATION("CONFIRMATION"),
    SUCCESS("DONE"),
    ERROR("ERROR")
}

/**
 * Quick actions exposed on long-pressing the orb (Requirement 5).
 */
enum class QuickAction(val title: String) {
    ASK_JARVIS("Ask JARVIS"),
    ANALYZE_SCREEN("Analyze Screen"),
    GO_HOME("Go Home"),
    BACK("Back"),
    STOP_TASK("Stop Current Task"),
    OPEN_JARVIS_APP("Open JARVIS App")
}

/**
 * Bridge interface through which the overlay interacts with the authoritative JARVIS assistant engine.
 */
interface AssistantBridge {
    fun onMicTapped()
    fun handleCommand(query: String)
    fun stopTask()
    fun requestScreenAnalysis()
    fun confirmPendingAction()
    fun cancelPendingAction()
}

/**
 * Singleton controller managing overlay lifecycle, visibility, state synchronization,
 * and routing user interactions to the existing JARVIS architecture.
 */
object JarvisOverlayController {

    private val _isOverlayRunning = MutableStateFlow(false)
    val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

    private val _isPanelOpen = MutableStateFlow(false)
    val isPanelOpen: StateFlow<Boolean> = _isPanelOpen.asStateFlow()

    private val _isQuickActionsOpen = MutableStateFlow(false)
    val isQuickActionsOpen: StateFlow<Boolean> = _isQuickActionsOpen.asStateFlow()

    private val _assistantState = MutableStateFlow(HomeUiState())
    val assistantState: StateFlow<HomeUiState> = _assistantState.asStateFlow()

    private var bridge: AssistantBridge? = null

    /**
     * Checks if the app has been granted system alert window overlay permission.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Starts the floating overlay service if permission is available.
     * Returns true if service was started, false otherwise.
     */
    fun startOverlay(context: Context): Boolean {
        if (!canDrawOverlays(context)) {
            return false
        }
        if (_isOverlayRunning.value) {
            return true // Prevent duplicate starts
        }

        val intent = Intent(context, JarvisOverlayService::class.java)
        context.startService(intent)
        _isOverlayRunning.value = true
        return true
    }

    /**
     * Stops the floating overlay service and hides all overlay UI.
     */
    fun stopOverlay(context: Context) {
        val intent = Intent(context, JarvisOverlayService::class.java)
        context.stopService(intent)
        _isOverlayRunning.value = false
        _isPanelOpen.value = false
        _isQuickActionsOpen.value = false
    }

    /**
     * Internal callback for the service lifecycle.
     */
    fun notifyServiceStarted() {
        _isOverlayRunning.value = true
    }

    /**
     * Internal callback for the service lifecycle.
     */
    fun notifyServiceDestroyed() {
        _isOverlayRunning.value = false
        _isPanelOpen.value = false
        _isQuickActionsOpen.value = false
    }

    /**
     * Registers the authoritative assistant bridge from HomeViewModel.
     */
    fun registerBridge(assistantBridge: AssistantBridge) {
        this.bridge = assistantBridge
    }

    /**
     * Unregisters the assistant bridge upon ViewModel lifecycle cleanup.
     */
    fun unregisterBridge() {
        this.bridge = null
    }

    /**
     * Updates the assistant state observed by the overlay from HomeViewModel.
     */
    fun updateAssistantState(state: HomeUiState) {
        _assistantState.value = state
    }

    /**
     * Computes the visual state of the floating orb derived directly from the active assistant state.
     */
    fun computeOrbVisualState(uiState: HomeUiState): OrbVisualState {
        return when {
            uiState.taskState == TaskExecutionState.FAILED || uiState.state == AssistantState.ERROR -> OrbVisualState.ERROR
            uiState.pendingConfirmation != null || uiState.state == AssistantState.WAITING_FOR_CONFIRMATION -> OrbVisualState.CONFIRMATION
            uiState.screenAnalysisState == ScreenAnalysisState.ANALYZING -> OrbVisualState.ANALYZING
            uiState.state == AssistantState.SPEAKING -> OrbVisualState.SPEAKING
            uiState.taskState == TaskExecutionState.COMPLETED -> OrbVisualState.SUCCESS
            uiState.taskState == TaskExecutionState.EXECUTING ||
            uiState.taskState == TaskExecutionState.VERIFYING ||
            uiState.taskState == TaskExecutionState.RETRYING ||
            uiState.state == AssistantState.EXECUTING -> OrbVisualState.EXECUTING
            uiState.state == AssistantState.ACTIVE_LISTENING ||
            uiState.state == AssistantState.LISTENING ||
            uiState.wakeWordState == WakeWordState.COMMAND_LISTENING -> OrbVisualState.LISTENING
            uiState.state == AssistantState.PROCESSING ||
            uiState.state == AssistantState.THINKING ||
            uiState.taskState == TaskExecutionState.PLANNING -> OrbVisualState.PROCESSING
            uiState.state == AssistantState.PASSIVE_WAKE ||
            uiState.wakeWordState == WakeWordState.LISTENING -> OrbVisualState.PASSIVE
            else -> OrbVisualState.IDLE
        }
    }

    /**
     * Handles tapping on the floating orb (toggles the compact HUD panel).
     */
    fun onOrbTapped() {
        _isQuickActionsOpen.value = false
        _isPanelOpen.value = !_isPanelOpen.value
    }

    /**
     * Handles long pressing on the floating orb (stops current task/interruption per requirement 15).
     */
    fun onOrbLongPressed() {
        _isPanelOpen.value = false
        _isQuickActionsOpen.value = !_isQuickActionsOpen.value
        bridge?.stopTask()
    }

    /**
     * Closes any open panel or quick actions popover.
     */
    fun dismissPopups() {
        _isPanelOpen.value = false
        _isQuickActionsOpen.value = false
    }

    /**
     * Dispatches user quick actions through the authoritative JARVIS architecture.
     */
    fun onQuickAction(action: QuickAction, context: Context) {
        _isQuickActionsOpen.value = false
        when (action) {
            QuickAction.ASK_JARVIS -> {
                bridge?.onMicTapped()
            }
            QuickAction.ANALYZE_SCREEN -> {
                bridge?.requestScreenAnalysis()
            }
            QuickAction.GO_HOME -> {
                bridge?.handleCommand("go home")
            }
            QuickAction.BACK -> {
                bridge?.handleCommand("press back")
            }
            QuickAction.STOP_TASK -> {
                bridge?.stopTask()
            }
            QuickAction.OPEN_JARVIS_APP -> {
                openJarvisApp(context)
            }
        }
    }

    /**
     * Brings the main JARVIS application activity to the front.
     */
    fun openJarvisApp(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        try {
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.app.ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }.toBundle()
            } else {
                null
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            pendingIntent.send(context, 0, null, null, null, null, options)
        } catch (_: Exception) {
            context.startActivity(intent)
        }
    }

    /**
     * Stops the currently executing task via existing TaskExecutor cancellation.
     */
    fun onStopTask() {
        bridge?.stopTask()
    }

    /**
     * Confirms a medium/high-risk action triggered by the user in the overlay panel.
     */
    fun onConfirmSafetyAction() {
        bridge?.confirmPendingAction()
    }

    /**
     * Cancels a pending action confirmation.
     */
    fun onCancelSafetyAction() {
        bridge?.cancelPendingAction()
    }
}

package com.jarvis.assistant.overlay

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jarvis.assistant.ui.theme.JarvisTheme
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Android Service managing the persistent floating JARVIS overlay UI above third-party applications.
 *
 * Implements a pure UI overlay hosted via WindowManager (TYPE_APPLICATION_OVERLAY).
 * Strictly observes existing assistant state without implementing an AccessibilityService.
 */
class JarvisOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var overlayComposeView: ComposeView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var prefs: SharedPreferences

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val myViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = myViewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var touchSlop = 0
    private val orbPositionState = kotlinx.coroutines.flow.MutableStateFlow(OrbPosition(0, 0))

    companion object {
        private const val PREFS_NAME = "jarvis_overlay_prefs"
        private const val KEY_POS_X = "orb_pos_x"
        private const val KEY_POS_Y = "orb_pos_y"
        private const val DEFAULT_ORB_SIZE_DP = 76
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(Bundle())
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        initOverlayView()
        setupPopupPositionSync()
        JarvisOverlayController.notifyServiceStarted()
    }

    private fun setupPopupPositionSync() {
        serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                JarvisOverlayController.isPanelOpen,
                JarvisOverlayController.isQuickActionsOpen,
                orbPositionState
            ) { panelOpen, quickActionsOpen, pos ->
                Triple(panelOpen, quickActionsOpen, pos)
            }.collect { (panelOpen, quickActionsOpen, pos) ->
                if (isDragging) return@collect
                val bounds = getCurrentScreenBounds()
                val isSnappedRight = pos.x > (bounds.width / 2)
                val popupWidthPx = when {
                    panelOpen -> dpToPx(280 + 8)
                    quickActionsOpen -> dpToPx(220 + 8)
                    else -> 0
                }

                if (isSnappedRight && popupWidthPx > 0) {
                    layoutParams.x = maxOf(bounds.edgeMargin, pos.x - popupWidthPx)
                } else {
                    layoutParams.x = pos.x
                }
                layoutParams.y = pos.y
                overlayComposeView?.let {
                    try {
                        windowManager.updateViewLayout(it, layoutParams)
                    } catch (_: Exception) { }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initOverlayView() {
        val orbSizePx = dpToPx(DEFAULT_ORB_SIZE_DP)
        val bounds = getCurrentScreenBounds()

        val savedX = prefs.getInt(KEY_POS_X, bounds.width - orbSizePx - bounds.edgeMargin)
        val savedY = prefs.getInt(KEY_POS_Y, bounds.height / 3)
        val initialPos = JarvisOrbPositionHelper.clampPosition(savedX, savedY, orbSizePx, bounds)
        orbPositionState.value = initialPos

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialPos.x
            y = initialPos.y
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@JarvisOverlayService)
            setViewTreeViewModelStoreOwner(this@JarvisOverlayService)
            setViewTreeSavedStateRegistryOwner(this@JarvisOverlayService)

            setContent {
                JarvisTheme {
                    val isPanelOpen by JarvisOverlayController.isPanelOpen.collectAsState()
                    val isQuickActionsOpen by JarvisOverlayController.isQuickActionsOpen.collectAsState()
                    val assistantState by JarvisOverlayController.assistantState.collectAsState()
                    val visualState = JarvisOverlayController.computeOrbVisualState(assistantState)
                    val currentPos by orbPositionState.collectAsState()

                    val isSnappedToRight = currentPos.x > (getCurrentScreenBounds().width / 2)

                    Row(
                        modifier = Modifier.wrapContentSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // If snapped to right, show popups to the left of the orb
                        if (isSnappedToRight) {
                            AnimatedVisibility(
                                visible = isPanelOpen,
                                enter = fadeIn() + slideInVertically(),
                                exit = fadeOut() + slideOutVertically()
                            ) {
                                JarvisOverlayPanel(
                                    uiState = assistantState,
                                    onClose = { JarvisOverlayController.dismissPopups() },
                                    onMicTapped = { JarvisOverlayController.onQuickAction(QuickAction.ASK_JARVIS, this@JarvisOverlayService) },
                                    onStopTask = { JarvisOverlayController.onStopTask() },
                                    onConfirmSafety = { JarvisOverlayController.onConfirmSafetyAction() },
                                    onCancelSafety = { JarvisOverlayController.onCancelSafetyAction() },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }

                            AnimatedVisibility(
                                visible = isQuickActionsOpen,
                                enter = fadeIn() + slideInVertically(),
                                exit = fadeOut() + slideOutVertically()
                            ) {
                                JarvisQuickActionsMenu(
                                    onActionSelected = { action ->
                                        JarvisOverlayController.onQuickAction(action, this@JarvisOverlayService)
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }

                        // Floating Orb Core
                        JarvisFloatingOrb(
                            visualState = visualState,
                            onTap = {
                                JarvisOverlayController.onOrbTapped()
                            },
                            onLongPress = {
                                JarvisOverlayController.onOrbLongPressed()
                            }
                        )

                        // If snapped to left, show popups to the right of the orb
                        if (!isSnappedToRight) {
                            AnimatedVisibility(
                                visible = isPanelOpen,
                                enter = fadeIn() + slideInVertically(),
                                exit = fadeOut() + slideOutVertically()
                            ) {
                                JarvisOverlayPanel(
                                    uiState = assistantState,
                                    onClose = { JarvisOverlayController.dismissPopups() },
                                    onMicTapped = { JarvisOverlayController.onQuickAction(QuickAction.ASK_JARVIS, this@JarvisOverlayService) },
                                    onStopTask = { JarvisOverlayController.onStopTask() },
                                    onConfirmSafety = { JarvisOverlayController.onConfirmSafetyAction() },
                                    onCancelSafety = { JarvisOverlayController.onCancelSafetyAction() },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }

                            AnimatedVisibility(
                                visible = isQuickActionsOpen,
                                enter = fadeIn() + slideInVertically(),
                                exit = fadeOut() + slideOutVertically()
                            ) {
                                JarvisQuickActionsMenu(
                                    onActionSelected = { action ->
                                        JarvisOverlayController.onQuickAction(action, this@JarvisOverlayService)
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            setOnTouchListener { _, event ->
                handleTouchEvent(event)
            }
        }

        this.overlayComposeView = composeView
        windowManager.addView(composeView, layoutParams)
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        val orbSizePx = dpToPx(DEFAULT_ORB_SIZE_DP)
        val bounds = getCurrentScreenBounds()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = orbPositionState.value.x
                initialY = orbPositionState.value.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return false // Allow compose to register click/long click if no drag occurs
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                val distance = hypot(dx.toDouble(), dy.toDouble())

                if (distance > touchSlop) {
                    if (!isDragging) {
                        JarvisOverlayController.dismissPopups()
                    }
                    isDragging = true
                    val targetX = initialX + dx
                    val targetY = initialY + dy

                    val clamped = JarvisOrbPositionHelper.clampPosition(targetX, targetY, orbSizePx, bounds)
                    layoutParams.x = clamped.x
                    layoutParams.y = clamped.y
                    orbPositionState.value = clamped
                    overlayComposeView?.let { windowManager.updateViewLayout(it, layoutParams) }
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    val snapped = JarvisOrbPositionHelper.snapToEdge(
                        currentX = layoutParams.x,
                        currentY = layoutParams.y,
                        orbSize = orbSizePx,
                        bounds = bounds
                    )
                    layoutParams.x = snapped.x
                    layoutParams.y = snapped.y
                    orbPositionState.value = snapped
                    overlayComposeView?.let { windowManager.updateViewLayout(it, layoutParams) }

                    // Persist safe position
                    prefs.edit()
                        .putInt(KEY_POS_X, snapped.x)
                        .putInt(KEY_POS_Y, snapped.y)
                        .apply()

                    isDragging = false
                    return true
                }
                return false
            }
            else -> return false
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val orbSizePx = dpToPx(DEFAULT_ORB_SIZE_DP)
        val bounds = getCurrentScreenBounds()

        val adjusted = JarvisOrbPositionHelper.adjustForConfigurationChange(
            previousPosition = OrbPosition(layoutParams.x, layoutParams.y),
            orbSize = orbSizePx,
            newBounds = bounds
        )

        layoutParams.x = adjusted.x
        layoutParams.y = adjusted.y
        orbPositionState.value = adjusted
        overlayComposeView?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    private fun getCurrentScreenBounds(): ScreenBounds {
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        val topInset = dpToPx(32) // Status bar allowance
        val bottomInset = dpToPx(48) // Navigation bar allowance
        return ScreenBounds(
            width = displayMetrics.widthPixels,
            height = displayMetrics.heightPixels,
            topInset = topInset,
            bottomInset = bottomInset,
            edgeMargin = dpToPx(16)
        )
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        myViewModelStore.clear()

        overlayComposeView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) { }
        }
        overlayComposeView = null
        JarvisOverlayController.notifyServiceDestroyed()
    }
}

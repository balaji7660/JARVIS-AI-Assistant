package com.jarvis.assistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jarvis.assistant.navigation.JarvisNavHost
import com.jarvis.assistant.ui.theme.JarvisTheme
import com.jarvis.assistant.wakeword.JarvisWakeWordService
import com.jarvis.assistant.wakeword.WakeWordManager
import com.jarvis.assistant.wakeword.WakeWordPreferences

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var wakeServiceBinder: JarvisWakeWordService.WakeWordServiceBinder? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Connected to JarvisWakeWordService")
            wakeServiceBinder = service as? JarvisWakeWordService.WakeWordServiceBinder
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Disconnected from JarvisWakeWordService")
            wakeServiceBinder = null
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.jarvis.assistant.data.remote.ApiClient.init(applicationContext)

        // Restore background wake service if previously enabled by user
        if (WakeWordPreferences.isBackgroundWakeEnabled(this) && JarvisWakeWordService.getInstance() == null) {
            try {
                JarvisWakeWordService.startService(this)
            } catch (t: Throwable) {
                Log.w(TAG, "Could not start JarvisWakeWordService on activity launch", t)
            }
        }

        enableEdgeToEdge()
        setContent {
            JarvisTheme {
                JarvisNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to background wake service to monitor state without tying its lifecycle to the Activity
        val intent = Intent(this, JarvisWakeWordService::class.java)
        try {
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to bind to JarvisWakeWordService", t)
        }
    }

    override fun onResume() {
        super.onResume()
        WakeWordManager.isActivityVisible = true
    }

    override fun onPause() {
        super.onPause()
        WakeWordManager.isActivityVisible = false
    }

    override fun onStop() {
        super.onStop()
        if (isServiceBound) {
            try {
                unbindService(serviceConnection)
            } catch (t: Throwable) {
                Log.w(TAG, "Error unbinding from JarvisWakeWordService", t)
            }
            isServiceBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // CRITICAL: Do NOT call stopService here!
        // JarvisWakeWordService must continue operating independently in background if enabled.
        Log.d(TAG, "MainActivity destroyed. JarvisWakeWordService remains unaffected.")
    }
}

package com.jarvis.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jarvis.assistant.navigation.JarvisNavHost
import com.jarvis.assistant.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.jarvis.assistant.data.remote.ApiClient.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            JarvisTheme {
                JarvisNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.jarvis.assistant.wakeword.WakeWordManager.isActivityVisible = true
    }

    override fun onPause() {
        super.onPause()
        com.jarvis.assistant.wakeword.WakeWordManager.isActivityVisible = false
    }
}

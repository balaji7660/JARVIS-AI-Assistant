package com.jarvis.assistant.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Settings : Screen("settings")
    data object Memories : Screen("memories")
}

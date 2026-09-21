package com.jarvis.assistant.tools.automation

/**
 * Strictly typed automation action representation.
 * Only the actions explicitly declared in this sealed class may be executed.
 */
sealed class AutomationAction {

    data object ReadVisibleScreen : AutomationAction()

    data class ClickText(
        val text: String
    ) : AutomationAction()

    data class ClickView(
        val viewId: String
    ) : AutomationAction()

    data class TypeText(
        val text: String
    ) : AutomationAction()

    data object ScrollForward : AutomationAction()

    data object ScrollBackward : AutomationAction()

    data object PressBack : AutomationAction()
}

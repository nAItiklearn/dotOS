package com.dotos.launcher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Optional, user-enabled service used ONLY to lock the screen from a gesture
 * (GLOBAL_ACTION_LOCK_SCREEN). It observes no events and reads no content.
 */
class DotAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: DotAccessibilityService? = null

        fun lock(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
    }
}

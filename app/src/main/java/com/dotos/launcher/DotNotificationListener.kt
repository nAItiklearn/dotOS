package com.dotos.launcher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Opt-in, local-only notification listener. Used for badges (counts per package) and for
 * MediaSessionManager access (music widget). Nothing ever leaves the device.
 */
class DotNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        connected.value = true
        refresh()
    }

    override fun onListenerDisconnected() {
        connected.value = false
        _badges.value = emptyMap()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val list = runCatching { activeNotifications }.getOrNull() ?: return
        val counts = HashMap<String, Int>()
        for (n in list) {
            if (!n.isClearable || n.isOngoing) continue
            if (n.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) continue
            counts[n.packageName] = (counts[n.packageName] ?: 0) + 1
        }
        _badges.value = counts
    }

    companion object {
        private val _badges = MutableStateFlow<Map<String, Int>>(emptyMap())
        val badges: StateFlow<Map<String, Int>> = _badges.asStateFlow()
        val connected = MutableStateFlow(false)
    }
}

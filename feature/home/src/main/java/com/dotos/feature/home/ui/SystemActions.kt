package com.dotos.feature.home.ui

import android.annotation.SuppressLint
import android.content.Context

/** Status-bar shade helpers. Uses the long-standing StatusBarManager methods (EXPAND_STATUS_BAR). */
internal object SystemActions {
    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandNotifications(context: Context) = invoke(context, "expandNotificationsPanel")

    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandQuickSettings(context: Context) = invoke(context, "expandSettingsPanel")

    @SuppressLint("WrongConstant", "PrivateApi")
    private fun invoke(context: Context, method: String) {
        runCatching {
            val service = context.getSystemService("statusbar") ?: return
            Class.forName("android.app.StatusBarManager").getMethod(method).invoke(service)
        }
    }
}

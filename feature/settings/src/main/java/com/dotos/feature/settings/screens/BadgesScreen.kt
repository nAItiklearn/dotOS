package com.dotos.feature.settings.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.dotos.core.data.settings.BadgeStyle
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.feature.settings.nav.SettingsEnv
import com.dotos.feature.settings.ui.Caption
import com.dotos.feature.settings.ui.ChoiceRows
import com.dotos.feature.settings.ui.PillButton
import com.dotos.feature.settings.ui.SectionHeader
import com.dotos.feature.settings.ui.SettingsRow
import com.dotos.feature.settings.ui.SettingsScaffold

@Composable
internal fun BadgesScreen(env: SettingsEnv, s: LauncherSettings) {
    var hasAccess by remember { mutableStateOf(env.deps.hasNotificationAccess()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasAccess = env.deps.hasNotificationAccess() }

    SettingsScaffold(title = "Notifications & badges", onBack = env.back) {
        SectionHeader("Badge style")
        ChoiceRows(
            options = BadgeStyle.entries,
            selected = s.badgeStyle,
            title = {
                when (it) {
                    BadgeStyle.DOT -> "Dot"
                    BadgeStyle.COUNT -> "Count"
                    BadgeStyle.OFF -> "Off"
                }
            },
            description = {
                when (it) {
                    BadgeStyle.DOT -> "A small dot on apps with notifications"
                    BadgeStyle.COUNT -> "The number of notifications on each app"
                    BadgeStyle.OFF -> "No badges on icons"
                }
            },
            onSelect = { style -> env.update { it.copy(badgeStyle = style) } },
        )

        SectionHeader("Access")
        SettingsRow(
            title = "Notification access",
            subtitle = if (hasAccess) "Allowed. Badges and the music widget can work." else "Not allowed. Badges need this permission.",
            trailing = {
                PillButton(
                    text = if (hasAccess) "Manage" else "Allow",
                    onClick = { env.deps.openNotificationAccess() },
                    filled = !hasAccess,
                )
            },
        )
        Caption("DotOS only counts notifications on your phone. Nothing is read aloud, stored, or sent anywhere.")
    }
}

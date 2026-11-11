package com.dotos.core.data

import com.dotos.core.data.settings.IconStyle
import com.dotos.core.data.settings.LauncherSettings
import com.dotos.core.data.settings.SettingsCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsCodecTest {
    @Test
    fun roundTrip() {
        val s = LauncherSettings(gridColumns = 5, iconStyle = IconStyle.DOT, showLabels = true)
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun corruptFallsBackToDefaults() {
        assertEquals(LauncherSettings(), SettingsCodec.decode("{not json"))
        assertEquals(LauncherSettings(), SettingsCodec.decode(null))
    }

    @Test
    fun unknownKeysAndBadEnumsAreTolerated() {
        val decoded = SettingsCodec.decode("""{"gridColumns":5,"futureThing":1,"iconStyle":"HOLOGRAM"}""")
        assertEquals(5, decoded.gridColumns)
        assertEquals(LauncherSettings().iconStyle, decoded.iconStyle)
    }
}

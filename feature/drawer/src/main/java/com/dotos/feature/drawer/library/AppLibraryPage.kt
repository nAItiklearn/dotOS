package com.dotos.feature.drawer.library

import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dotos.core.launcher.AppInfo

@Composable
fun AppLibraryPage(
    apps: List<AppInfo>,
    recentlyUsed: List<AppInfo>,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onAppLongPress: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
) {
    Box(modifier)
}

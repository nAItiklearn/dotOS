package com.dotos.feature.settings.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.dotos.feature.settings.nav.SettingsEnv
import java.io.File

internal object PhotoWallpaper {
    /**
     * Copies the picked photo into app-private storage so the wallpaper keeps working after the
     * picker's temporary permission ends. A fresh file name per pick makes the renderer reload the
     * image (it caches by path) and old copies are deleted. Returns the absolute path, or null.
     */
    fun copyFromUri(context: Context, uri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
            val target = File(dir, "user-${System.currentTimeMillis()}.jpg")
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                null
            } else {
                input.use { source -> target.outputStream().use { out -> source.copyTo(out) } }
                dir.listFiles()?.forEach { f -> if (f.name.startsWith("user") && f != target) f.delete() }
                target.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Returns a function that opens the system Photo Picker (no storage permission) and, when a photo is
 * picked, makes it the wallpaper (id "image").
 */
@Composable
internal fun rememberPhotoPicker(env: SettingsEnv): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            env.launchIO {
                val path = PhotoWallpaper.copyFromUri(context, uri)
                if (path != null) {
                    env.updateWallpaper { it.copy(id = "image", imagePath = path) }
                }
            }
        }
    }
    return remember(launcher) {
        {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

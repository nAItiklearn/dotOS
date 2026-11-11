package com.dotos.feature.home.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.dotos.core.data.db.WidgetStateDao
import com.dotos.core.data.db.WidgetStateEntity
import com.dotos.feature.home.layout.LayoutRepository
import com.dotos.feature.widgets.api.WidgetHostServices
import com.dotos.feature.widgets.api.WidgetInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** The launcher side of [WidgetHostServices]. Activity-bound pieces are passed in as lambdas. */
internal class HomeWidgetServices(
    private val context: Context,
    private val stateDao: WidgetStateDao,
    private val layout: LayoutRepository,
    private val launchPermission: (String, (Boolean) -> Unit) -> Unit,
    private val launchPhotoPicker: ((Uri?) -> Unit) -> Unit,
    private val onOpenSearch: () -> Unit,
    private val onOpenOptions: (WidgetInstance) -> Unit,
    private val notificationAccess: () -> Boolean,
    private val onRequestNotificationAccess: () -> Unit,
    override val notificationListener: ComponentName?,
) : WidgetHostServices {
    override fun state(key: String): Flow<String?> = stateDao.observe(key)

    override suspend fun putState(key: String, value: String) = withContext(Dispatchers.IO) {
        stateDao.put(WidgetStateEntity(key, value))
    }

    override fun requestPermission(permission: String, onResult: (Boolean) -> Unit) {
        if (hasPermission(permission)) onResult(true) else launchPermission(permission, onResult)
    }

    override fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override fun startActivity(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun pickImage(onPicked: (Uri?) -> Unit) = launchPhotoPicker(onPicked)

    override fun openSearch() = onOpenSearch()

    override fun openOptions(instance: WidgetInstance) = onOpenOptions(instance)

    override suspend fun updateConfig(instance: WidgetInstance, config: Map<String, String>) {
        if (instance.itemId >= 0) layout.updateWidgetConfig(instance.itemId, config)
    }

    override fun hasNotificationAccess(): Boolean = notificationAccess()

    override fun requestNotificationAccess() = onRequestNotificationAccess()
}

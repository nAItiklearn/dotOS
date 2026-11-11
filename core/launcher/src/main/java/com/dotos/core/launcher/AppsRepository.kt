package com.dotos.core.launcher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

/** Live list of launchable apps across every profile the launcher can see. */
class AppsRepository(private val context: Context, private val scope: CoroutineScope) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    private val _apps = MutableStateFlow<List<AppInfo>?>(null)
    /** null until the first load finishes. Sorted A–Z by label. */
    val apps: StateFlow<List<AppInfo>?> = _apps.asStateFlow()

    private val _quietProfiles = MutableStateFlow<Set<Long>>(emptySet())
    /** Serials of profiles that are currently paused / locked (work off, private space locked). */
    val quietProfiles: StateFlow<Set<Long>> = _quietProfiles.asStateFlow()

    private var reloadJob: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = scheduleReload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = scheduleReload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = scheduleReload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = scheduleReload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = scheduleReload()
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = scheduleReload()
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = scheduleReload()
    }

    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = scheduleReload()
    }

    fun start() {
        launcherApps.registerCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            if (Build.VERSION.SDK_INT >= 31) {
                addAction(Intent.ACTION_PROFILE_ACCESSIBLE)
                addAction(Intent.ACTION_PROFILE_INACCESSIBLE)
            }
            if (Build.VERSION.SDK_INT >= 35) {
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
                addAction(Intent.ACTION_PROFILE_ADDED)
                addAction(Intent.ACTION_PROFILE_REMOVED)
            }
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        ContextCompat.registerReceiver(context, profileReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        scope.launch { reload() }
    }

    fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            delay(150) // coalesce bursts (installs fire several callbacks)
            reload()
        }
    }

    private suspend fun reload() = withContext(Dispatchers.IO) {
        val self = context.packageName
        val result = ArrayList<AppInfo>(256)
        val quiet = HashSet<Long>()
        for (user in launcherApps.profiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val kind = profileKind(user)
            val isQuiet = runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false)
            if (isQuiet) quiet += serial
            if (kind == ProfileKind.PRIVATE && isQuiet) continue
            val activities = runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
            for (info in activities) {
                val cn = info.componentName
                if (cn.packageName == self && user == Process.myUserHandle()) continue
                val appInfo = info.applicationInfo
                val versionCode = runCatching {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(cn.packageName, 0).longVersionCode
                }.getOrDefault(0L)
                result += AppInfo(
                    key = AppInfo.keyOf(cn, serial),
                    component = cn,
                    user = user,
                    userSerial = serial,
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { cn.packageName },
                    profile = kind,
                    installTime = info.firstInstallTime,
                    versionCode = versionCode,
                    category = AppCategory.of(appInfo),
                    activityInfo = info,
                )
            }
        }
        result.sortWith { a, b -> collator.compare(a.label, b.label) }
        _quietProfiles.value = quiet
        _apps.value = result
    }

    fun profileKind(user: UserHandle): ProfileKind {
        if (user == Process.myUserHandle()) return ProfileKind.MAIN
        if (Build.VERSION.SDK_INT >= 35) {
            val type = runCatching { launcherApps.getLauncherUserInfo(user)?.userType }.getOrNull()
            if (type == UserManager.USER_TYPE_PROFILE_PRIVATE) return ProfileKind.PRIVATE
            if (type == UserManager.USER_TYPE_PROFILE_MANAGED) return ProfileKind.WORK
        }
        return ProfileKind.WORK
    }

    fun userForSerial(serial: Long): UserHandle? = userManager.getUserForSerialNumber(serial)

    fun serialOf(user: UserHandle): Long = userManager.getSerialNumberForUser(user)

    val mainUserSerial: Long get() = userManager.getSerialNumberForUser(Process.myUserHandle())

    /** Private Space profile if one exists (Android 15+). */
    fun privateProfile(): UserHandle? {
        if (Build.VERSION.SDK_INT < 35) return null
        return launcherApps.profiles.firstOrNull { profileKind(it) == ProfileKind.PRIVATE }
    }

    /** Lock or unlock a paused profile (work or private space). Allowed for the default launcher. */
    fun setProfileQuiet(user: UserHandle, quiet: Boolean): Boolean = runCatching {
        userManager.requestQuietModeEnabled(quiet, user)
    }.getOrDefault(false).also { scheduleReload() }

    fun launch(app: AppInfo, bounds: Rect? = null, options: Bundle? = null) {
        runCatching { launcherApps.startMainActivity(app.component, app.user, bounds, options) }
    }

    fun launch(component: ComponentName, user: UserHandle, bounds: Rect? = null, options: Bundle? = null): Boolean =
        runCatching { launcherApps.startMainActivity(component, user, bounds, options) }.isSuccess

    fun openAppInfo(app: AppInfo) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun isSystemApp(app: AppInfo): Boolean =
        app.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0

    fun openPlayStoreOrSettings() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    val shortcuts: ShortcutsRepository by lazy { ShortcutsRepository(context, launcherApps) }
}

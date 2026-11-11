package com.dotos.core.launcher

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.os.UserHandle

enum class ProfileKind { MAIN, WORK, PRIVATE, OTHER }

/**
 * A launchable activity for one user profile. Equality is by [key] + [label] so lists diff cheaply.
 * [activityInfo] is kept for icon loading and is intentionally not part of equality.
 */
class AppInfo(
    val key: String,
    val component: ComponentName,
    val user: UserHandle,
    val userSerial: Long,
    val label: String,
    val profile: ProfileKind,
    val installTime: Long,
    val versionCode: Long,
    val category: AppCategory,
    val activityInfo: LauncherActivityInfo,
) {
    val packageName: String get() = component.packageName

    override fun equals(other: Any?): Boolean = other is AppInfo && other.key == key && other.label == label &&
        other.versionCode == versionCode
    override fun hashCode(): Int = key.hashCode()
    override fun toString(): String = "AppInfo($key, $label)"

    companion object {
        fun keyOf(component: ComponentName, userSerial: Long): String = component.flattenToShortString() + "#" + userSerial
    }
}

/** Buckets used by the App Library and drawer auto-categories. */
enum class AppCategory(val title: String) {
    SUGGESTIONS("Suggestions"),
    RECENT("Recently added"),
    SOCIAL("Social"),
    ENTERTAINMENT("Entertainment"),
    GAMES("Games"),
    PRODUCTIVITY("Productivity"),
    CREATIVITY("Creativity"),
    UTILITIES("Utilities"),
    SHOPPING("Shopping & Finance"),
    TRAVEL("Travel"),
    HEALTH("Health & Fitness"),
    NEWS("Information & Reading"),
    OTHER("Other"),
    ;

    companion object {
        /** Maps platform [ApplicationInfo.category] plus a curated package list into a bucket. */
        fun of(info: ApplicationInfo): AppCategory {
            CategoryHints.byPackage(info.packageName)?.let { return it }
            return when (info.category) {
                ApplicationInfo.CATEGORY_GAME -> GAMES
                ApplicationInfo.CATEGORY_SOCIAL -> SOCIAL
                ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> ENTERTAINMENT
                ApplicationInfo.CATEGORY_IMAGE -> CREATIVITY
                ApplicationInfo.CATEGORY_NEWS -> NEWS
                ApplicationInfo.CATEGORY_MAPS -> TRAVEL
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> PRODUCTIVITY
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> UTILITIES
                else -> if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) UTILITIES else OTHER
            }
        }
    }
}

internal object CategoryHints {
    private val prefixes: List<Pair<String, AppCategory>> = listOf(
        "com.whatsapp" to AppCategory.SOCIAL,
        "org.telegram" to AppCategory.SOCIAL,
        "com.instagram" to AppCategory.SOCIAL,
        "com.facebook" to AppCategory.SOCIAL,
        "com.snapchat" to AppCategory.SOCIAL,
        "com.twitter" to AppCategory.SOCIAL,
        "com.discord" to AppCategory.SOCIAL,
        "com.reddit" to AppCategory.SOCIAL,
        "org.thoughtcrime.securesms" to AppCategory.SOCIAL,
        "com.linkedin" to AppCategory.SOCIAL,
        "com.viber" to AppCategory.SOCIAL,
        "jp.naver.line" to AppCategory.SOCIAL,
        "com.zhiliaoapp.musically" to AppCategory.SOCIAL,
        "com.google.android.apps.messaging" to AppCategory.SOCIAL,
        "com.google.android.dialer" to AppCategory.SOCIAL,
        "com.google.android.contacts" to AppCategory.SOCIAL,
        "com.samsung.android.messaging" to AppCategory.SOCIAL,
        "com.spotify" to AppCategory.ENTERTAINMENT,
        "com.netflix" to AppCategory.ENTERTAINMENT,
        "com.google.android.youtube" to AppCategory.ENTERTAINMENT,
        "com.google.android.apps.youtube" to AppCategory.ENTERTAINMENT,
        "com.amazon.avod" to AppCategory.ENTERTAINMENT,
        "in.startv.hotstar" to AppCategory.ENTERTAINMENT,
        "com.jio" to AppCategory.ENTERTAINMENT,
        "com.soundcloud" to AppCategory.ENTERTAINMENT,
        "com.google.android.gm" to AppCategory.PRODUCTIVITY,
        "com.google.android.calendar" to AppCategory.PRODUCTIVITY,
        "com.google.android.apps.docs" to AppCategory.PRODUCTIVITY,
        "com.google.android.keep" to AppCategory.PRODUCTIVITY,
        "com.microsoft" to AppCategory.PRODUCTIVITY,
        "com.notion" to AppCategory.PRODUCTIVITY,
        "com.todoist" to AppCategory.PRODUCTIVITY,
        "com.slack" to AppCategory.PRODUCTIVITY,
        "us.zoom" to AppCategory.PRODUCTIVITY,
        "com.google.android.apps.photos" to AppCategory.CREATIVITY,
        "com.adobe" to AppCategory.CREATIVITY,
        "com.canva" to AppCategory.CREATIVITY,
        "com.picsart" to AppCategory.CREATIVITY,
        "com.motorola.camera" to AppCategory.CREATIVITY,
        "com.google.android.GoogleCamera" to AppCategory.CREATIVITY,
        "com.android.camera" to AppCategory.CREATIVITY,
        "com.amazon.mShop" to AppCategory.SHOPPING,
        "in.amazon.mShop" to AppCategory.SHOPPING,
        "com.flipkart" to AppCategory.SHOPPING,
        "com.myntra" to AppCategory.SHOPPING,
        "com.phonepe" to AppCategory.SHOPPING,
        "net.one97.paytm" to AppCategory.SHOPPING,
        "com.google.android.apps.nbu.paisa" to AppCategory.SHOPPING,
        "com.paypal" to AppCategory.SHOPPING,
        "com.google.android.apps.walletnfcrel" to AppCategory.SHOPPING,
        "com.ubercab" to AppCategory.TRAVEL,
        "com.olacabs" to AppCategory.TRAVEL,
        "com.google.android.apps.maps" to AppCategory.TRAVEL,
        "com.airbnb" to AppCategory.TRAVEL,
        "in.swiggy" to AppCategory.TRAVEL,
        "com.application.zomato" to AppCategory.TRAVEL,
        "com.google.android.apps.fitness" to AppCategory.HEALTH,
        "com.strava" to AppCategory.HEALTH,
        "com.motorola.moto" to AppCategory.UTILITIES,
        "com.android.settings" to AppCategory.UTILITIES,
        "com.google.android.deskclock" to AppCategory.UTILITIES,
        "com.google.android.calculator" to AppCategory.UTILITIES,
        "com.google.android.documentsui" to AppCategory.UTILITIES,
        "com.google.android.apps.nbu.files" to AppCategory.UTILITIES,
        "com.android.vending" to AppCategory.UTILITIES,
        "com.android.chrome" to AppCategory.NEWS,
        "com.google.android.googlequicksearchbox" to AppCategory.NEWS,
        "com.google.android.apps.magazines" to AppCategory.NEWS,
        "com.amazon.kindle" to AppCategory.NEWS,
        "org.mozilla" to AppCategory.NEWS,
    )

    fun byPackage(pkg: String): AppCategory? = prefixes.firstOrNull { pkg.startsWith(it.first) }?.second
}

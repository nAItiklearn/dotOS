package com.dotos.feature.icons.engine

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** An installed third-party icon pack (ADW / Nova / Lawnchair appfilter.xml format). */
data class IconPackInfo(val packageName: String, val label: String)

/** Minimal appfilter.xml reader: ComponentInfo{pkg/cls} → drawable name. */
class IconPack(private val context: Context, val packageName: String) {
    private val res: Resources? = runCatching { context.packageManager.getResourcesForApplication(packageName) }.getOrNull()
    private val map: Map<String, String> by lazy { parse() }

    fun drawableFor(component: ComponentName): Drawable? {
        val r = res ?: return null
        val name = map[component.flattenToString()] ?: map[component.packageName] ?: return null
        @SuppressLint("DiscouragedApi")
        val id = r.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return runCatching { r.getDrawableForDensity(id, context.resources.displayMetrics.densityDpi, null) }.getOrNull()
    }

    @SuppressLint("DiscouragedApi")
    private fun parse(): Map<String, String> {
        val r = res ?: return emptyMap()
        val out = HashMap<String, String>()
        val parser: XmlPullParser = runCatching {
            val xmlId = r.getIdentifier("appfilter", "xml", packageName)
            if (xmlId != 0) {
                r.getXml(xmlId)
            } else {
                XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(r.assets.open("appfilter.xml"), "UTF-8")
                }
            }
        }.getOrNull() ?: return emptyMap()
        runCatching {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (comp != null && drawable != null && comp.startsWith("ComponentInfo{")) {
                        val flat = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                        out.putIfAbsent(flat, drawable)
                        flat.substringBefore('/').let { out.putIfAbsent(it, drawable) }
                    }
                }
                event = parser.next()
            }
        }
        return out
    }

    companion object {
        private val THEME_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "com.fede.launcher.THEME_ICONPACK",
        )

        fun installed(context: Context): List<IconPackInfo> {
            val pm = context.packageManager
            val seen = LinkedHashMap<String, IconPackInfo>()
            for (action in THEME_ACTIONS) {
                val list = runCatching { pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
                for (ri in list) {
                    val pkg = ri.activityInfo.packageName
                    if (pkg !in seen) seen[pkg] = IconPackInfo(pkg, ri.loadLabel(pm).toString())
                }
            }
            return seen.values.sortedBy { it.label.lowercase() }
        }
    }
}

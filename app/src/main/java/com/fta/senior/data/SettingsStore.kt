package com.fta.senior.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

enum class ButtonMode(val title: String) { BOTH("两个功能"), FOREGROUND("仅强停当前"), BACKGROUND("仅清理后台") }
enum class ButtonShape(val title: String) { CIRCLE("圆形"), ROUNDED("圆角方形"), PILL("胶囊形") }

data class OverlaySettings(
    val enabled: Boolean = false,
    val mode: ButtonMode = ButtonMode.BOTH,
    val shape: ButtonShape = ButtonShape.CIRCLE,
    val size: Int = 48,
    val horizontal: Boolean = false,
    val reversed: Boolean = false,
    val showLabels: Boolean = false,
    val opacity: Int = 90,
    val idleOpacity: Int = 40,
    val backgroundColor: Int = Color.rgb(0, 47, 167),
    val foregroundColor: Int = Color.WHITE,
    val snapToEdge: Boolean = true,
    val locked: Boolean = false,
    val haptic: Boolean = true,
    val confirm: Boolean = false,
    val protectImportant: Boolean = true,
    val protectVpn: Boolean = true,
    val accepted: Boolean = false,
    val whitelist: Set<String> = emptySet(),
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val listeners = linkedSetOf<() -> Unit>()
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key?.startsWith("position_") != true) listeners.toList().forEach { it() }
    }

    init { prefs.registerOnSharedPreferenceChangeListener(preferenceListener) }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    fun read() = OverlaySettings(
        enabled = prefs.getBoolean("enabled", false),
        mode = enumValue(prefs.getString("mode", null), ButtonMode.BOTH),
        shape = enumValue(prefs.getString("shape", null), ButtonShape.CIRCLE),
        size = prefs.getInt("size", 48).coerceIn(40, 80),
        horizontal = prefs.getBoolean("horizontal", false),
        reversed = prefs.getBoolean("reversed", false),
        showLabels = prefs.getBoolean("showLabels", false),
        opacity = prefs.getInt("opacity", 90).coerceIn(30, 100),
        idleOpacity = prefs.getInt("idleOpacity", 40).coerceIn(15, 100),
        backgroundColor = prefs.getInt("backgroundColor", Color.rgb(0, 47, 167)),
        foregroundColor = prefs.getInt("foregroundColor", Color.WHITE),
        snapToEdge = prefs.getBoolean("snapToEdge", true),
        locked = prefs.getBoolean("locked", false),
        haptic = prefs.getBoolean("haptic", true),
        confirm = prefs.getBoolean("confirm", false),
        protectImportant = prefs.getBoolean("protectImportant", true),
        protectVpn = prefs.getBoolean("protectVpn", true),
        accepted = prefs.getBoolean("accepted", false),
        whitelist = prefs.getStringSet("whitelist", emptySet())!!.toSet(),
    )

    fun put(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun put(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun setWhitelisted(packageName: String, selected: Boolean) {
        val packages = read().whitelist.toMutableSet()
        if (selected) packages.add(packageName) else packages.remove(packageName)
        prefs.edit().putStringSet("whitelist", packages).apply()
    }

    fun position(landscape: Boolean): Pair<Float, Float> {
        val key = if (landscape) "position_land" else "position_port"
        return prefs.getFloat("${key}_x", 1f).coerceIn(0f, 1f) to
            prefs.getFloat("${key}_y", .35f).coerceIn(0f, 1f)
    }

    fun savePosition(landscape: Boolean, x: Float, y: Float) {
        val key = if (landscape) "position_land" else "position_port"
        prefs.edit().putFloat("${key}_x", x.coerceIn(0f, 1f))
            .putFloat("${key}_y", y.coerceIn(0f, 1f)).apply()
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}

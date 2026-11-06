package com.sysutils.batteryguard

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {
    private const val PREFS_NAME = "battery_guard_prefs"
    private const val KEY_TARGET_PACKAGE = "target_package"
    private const val KEY_TIMER_SECONDS = "timer_seconds"
    private const val KEY_BUTTON_ALPHA = "button_alpha"
    private const val KEY_BUTTON_SIZE_DP = "button_size_dp"
    private const val KEY_POS_X = "pos_x"
    private const val KEY_POS_Y = "pos_y"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTargetPackage(context: Context): String =
        prefs(context).getString(KEY_TARGET_PACKAGE, "ru.yandex.taximeter") ?: "ru.yandex.taximeter"

    fun setTargetPackage(context: Context, pkg: String) {
        prefs(context).edit().putString(KEY_TARGET_PACKAGE, pkg.trim()).apply()
    }

    fun getTimerSeconds(context: Context): Int =
        prefs(context).getInt(KEY_TIMER_SECONDS, 45)

    fun setTimerSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_TIMER_SECONDS, seconds.coerceIn(5, 300)).apply()
    }

    fun getButtonAlpha(context: Context): Float =
        prefs(context).getFloat(KEY_BUTTON_ALPHA, 0.85f)

    fun setButtonAlpha(context: Context, alpha: Float) {
        prefs(context).edit().putFloat(KEY_BUTTON_ALPHA, alpha.coerceIn(0.25f, 1.0f)).apply()
    }

    fun getButtonSizeDp(context: Context): Int =
        prefs(context).getInt(KEY_BUTTON_SIZE_DP, 62)

    fun setButtonSizeDp(context: Context, sizeDp: Int) {
        prefs(context).edit().putInt(KEY_BUTTON_SIZE_DP, sizeDp.coerceIn(44, 96)).apply()
    }

    fun getSavedPosition(context: Context): Pair<Int, Int> {
        val p = prefs(context)
        return Pair(p.getInt(KEY_POS_X, 20), p.getInt(KEY_POS_Y, 300))
    }

    fun savePosition(context: Context, x: Int, y: Int) {
        prefs(context).edit()
            .putInt(KEY_POS_X, x)
            .putInt(KEY_POS_Y, y)
            .apply()
    }
}

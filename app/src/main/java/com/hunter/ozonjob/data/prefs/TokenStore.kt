package com.hunter.ozonjob.data.prefs

import android.content.Context
import android.content.SharedPreferences

/** Хранит токен и настройки охоты. Только в памяти устройства. */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hotshift_prefs", Context.MODE_PRIVATE)

    var token: String
        get() = prefs.getString(KEY_TOKEN, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    val isAuthorized: Boolean get() = token.isNotBlank()

    var phone: String
        get() = prefs.getString(KEY_PHONE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_PHONE, value).apply()

    /** Час появления горячих смен, например 6. */
    var hotHour: Int
        get() = prefs.getInt(KEY_HOT_HOUR, 6)
        set(value) = prefs.edit().putInt(KEY_HOT_HOUR, value).apply()

    /** Целевая длительность смены в часах. */
    var durationHours: Int
        get() = prefs.getInt(KEY_DURATION, 8)
        set(value) = prefs.edit().putInt(KEY_DURATION, value).apply()

    fun logout() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_PHONE = "phone"
        const val KEY_HOT_HOUR = "hot_hour"
        const val KEY_DURATION = "duration_hours"
    }
}

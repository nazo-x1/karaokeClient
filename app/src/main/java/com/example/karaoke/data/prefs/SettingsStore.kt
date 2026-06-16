package com.example.karaoke.data.prefs

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var server: String
        get() = prefs.getString(KEY_SERVER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER, value.trim().trimEnd('/')).apply()

    var vocalsVolume: Float
        get() = prefs.getFloat(KEY_VOCALS_VOLUME, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_VOCALS_VOLUME, value).apply()

    var accompanimentVolume: Float
        get() = prefs.getFloat(KEY_ACCOMPANIMENT_VOLUME, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_ACCOMPANIMENT_VOLUME, value).apply()

    val hasServer: Boolean get() = server.isNotBlank()

    companion object {
        const val PREFS_NAME = "KTV_PREFS"
        const val KEY_SERVER = "server"
        const val KEY_VOCALS_VOLUME = "vocalsVolume"
        const val KEY_ACCOMPANIMENT_VOLUME = "accompanimentVolume"
    }
}

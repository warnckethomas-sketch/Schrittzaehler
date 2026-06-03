package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "step_tracker_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_STEP_LENGTH = "pref_step_length_cm"
        private const val DEFAULT_STEP_LENGTH = 75 // default 75 cm
        private const val KEY_AUTO_BACKUP = "pref_auto_backup_enabled"
        private const val KEY_LAST_BACKUP = "pref_last_backup_time"
        private const val KEY_CUSTOM_BACKUP_DIR = "pref_custom_backup_dir"
        private const val KEY_CUSTOM_BACKUP_FILE = "pref_custom_backup_file"
    }

    var stepLengthCm: Int
        get() = sharedPreferences.getInt(KEY_STEP_LENGTH, DEFAULT_STEP_LENGTH)
        set(value) {
            sharedPreferences.edit().putInt(KEY_STEP_LENGTH, value).apply()
        }

    var isAutoBackupEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_BACKUP, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_AUTO_BACKUP, value).apply()
        }

    var lastBackupTime: String
        get() = sharedPreferences.getString(KEY_LAST_BACKUP, "Nie") ?: "Nie"
        set(value) {
            sharedPreferences.edit().putString(KEY_LAST_BACKUP, value).apply()
        }

    var customBackupDirUri: String
        get() = sharedPreferences.getString(KEY_CUSTOM_BACKUP_DIR, "") ?: ""
        set(value) {
            sharedPreferences.edit().putString(KEY_CUSTOM_BACKUP_DIR, value).apply()
        }

    var customBackupFileName: String
        get() = sharedPreferences.getString(KEY_CUSTOM_BACKUP_FILE, "pacertrack_backup.json") ?: "pacertrack_backup.json"
        set(value) {
            sharedPreferences.edit().putString(KEY_CUSTOM_BACKUP_FILE, value).apply()
        }
}

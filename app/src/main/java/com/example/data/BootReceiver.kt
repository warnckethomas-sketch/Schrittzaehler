package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val preferencesManager = PreferencesManager(context)
            if (preferencesManager.alarmEnabled) {
                AlarmHelper.scheduleAlarm(
                    context,
                    preferencesManager.alarmHour,
                    preferencesManager.alarmMinute
                )
                Log.d("BootReceiver", "Rescheduled alarm on boot.")
            }
        }
    }
}

package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.DateUtils
import kotlinx.coroutines.runBlocking

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val preferencesManager = PreferencesManager(context)
        
        // If alarm is disabled, do nothing
        if (!preferencesManager.alarmEnabled) {
            return
        }

        // Reschedule alarm for the next day
        AlarmHelper.scheduleAlarm(context, preferencesManager.alarmHour, preferencesManager.alarmMinute)

        val todayDateStr = DateUtils.getTodayString()
        val p1Key = todayDateStr
        val p2Key = "person_2|$todayDateStr"

        val database = StepDatabase.getDatabase(context)
        val stepDao = database.stepDao()

        var p1Missing = false
        var p2Missing = false

        runBlocking {
            val p1Entry = stepDao.getEntryByDate(p1Key)
            if (p1Entry == null || p1Entry.steps <= 0) {
                p1Missing = true
            }

            val p2Entry = stepDao.getEntryByDate(p2Key)
            if (p2Entry == null || p2Entry.steps <= 0) {
                p2Missing = true
            }
        }

        // If both have entries, do not trigger a notification
        if (!p1Missing && !p2Missing) {
            return
        }

        val person1Name = preferencesManager.person1Name
        val person2Name = preferencesManager.person2Name

        val title = "Schritte eintragen!"
        val message = when {
            p1Missing && p2Missing -> "Für heute wurden noch keine Schritte für $person1Name und $person2Name eingetragen!"
            p1Missing -> "Für heute wurden noch keine Schritte für $person1Name eingetragen!"
            else -> "Für heute wurden noch keine Schritte für $person2Name eingetragen!"
        }

        triggerNotification(context, title, message)
    }

    private fun triggerNotification(context: Context, title: String, message: String) {
        val channelId = "step_tracker_alarm_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Schrittzähler Erinnerung"
            val descriptionText = "Erinnerungen für die tägliche Schritterfassung"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Try to use app icon or default system drawable if not ready
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        notificationManager.notify(1002, builder.build())
    }
}

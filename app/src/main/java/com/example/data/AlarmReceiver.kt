package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import android.media.RingtoneManager
import android.media.AudioAttributes
import com.example.MainActivity
import com.example.R
import com.example.ui.DateUtils
import kotlinx.coroutines.runBlocking

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_CONFIRM = "com.example.action.CONFIRM_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CONFIRM) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1002) // Dismiss the notification
            
            // Do NOT cancel the retry alarm!
            Log.d("AlarmReceiver", "Notification confirmed/dismissed via action button/swipe. Keeping 10-minute retry alarm active.")
            return
        }

        val preferencesManager = PreferencesManager(context)
        
        // If alarm is disabled, do nothing
        if (!preferencesManager.alarmEnabled) {
            AlarmHelper.cancelRetryAlarm(context)
            return
        }

        val isRetry = intent.getBooleanExtra("is_retry", false)
        val scheduledTime = intent.getLongExtra("scheduled_time", 0L)
        val currentTime = System.currentTimeMillis()

        // Check if the alarm was significantly delayed/deferred by the OS (e.g., more than 5 minutes late).
        // This commonly happens when the app was closed or suspended, and launching the app triggers the deferred intent.
        // We do NOT want to show the notification when the app is opened late; we only show it if triggered on time.
        if (scheduledTime > 0L && (currentTime - scheduledTime) > 5 * 60 * 1000) {
            Log.w("AlarmReceiver", "Alarm was deferred/delayed by the OS (scheduled: $scheduledTime, current: $currentTime). Skipping notification display.")
            // Reschedule daily alarm for the next day if this is the normal daily alarm, so the cycle continues
            if (!isRetry) {
                AlarmHelper.scheduleAlarm(context, preferencesManager.alarmHour, preferencesManager.alarmMinute, forceTomorrow = true)
            }
            return
        }

        // Reschedule daily alarm for the next day ONLY if it is the normal daily alarm
        if (!isRetry) {
            AlarmHelper.scheduleAlarm(context, preferencesManager.alarmHour, preferencesManager.alarmMinute, forceTomorrow = true)
        }

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

        // If both have entries, do not trigger a notification, cancel any retry and return
        if (!p1Missing && !p2Missing) {
            AlarmHelper.cancelRetryAlarm(context)
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

        // Schedule/reschedule retry alarm in 10 minutes
        AlarmHelper.scheduleRetryAlarm(context)
    }

    private fun triggerNotification(context: Context, title: String, message: String) {
        val channelId = "step_tracker_alarm_channel_v4"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Schrittzähler Erinnerung"
            val descriptionText = "Erinnerungen für die tägliche Schritterfassung"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                setSound(soundUri, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                )
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

        val confirmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_CONFIRM
        }
        val confirmPendingIntent = PendingIntent.getBroadcast(
            context,
            1003,
            confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Try to use app icon or default system drawable if not ready
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setVibrate(longArrayOf(100, 200, 300, 400, 500))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .setDeleteIntent(confirmPendingIntent) // If they swipe/dismiss, it fires ACTION_CONFIRM
            .addAction(
                0,
                "Bestätigen",
                confirmPendingIntent
            )
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        notificationManager.notify(1002, builder.build())
    }
}

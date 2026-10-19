package com.olaf.rereminder.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.olaf.rereminder.data.ReminderRepository
import com.olaf.rereminder.scheduler.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISMISS = "com.olaf.rereminder.ACTION_DISMISS"
        const val ACTION_SNOOZE = "com.olaf.rereminder.ACTION_SNOOZE"
        const val SNOOZE_DURATION_MILLIS = 10 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(AlarmReceiver.EXTRA_REMINDER_ID) ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(reminderId.hashCode())

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ReminderRepository(context.applicationContext)
                val scheduler = ReminderScheduler(context.applicationContext)
                val reminder = repository.getReminderById(reminderId) ?: return@launch

                when (intent.action) {
                    ACTION_DISMISS -> {
                        val updated = reminder.copy(
                            isCompleted = true,
                            dismissedAt = System.currentTimeMillis(),
                            modifiedAt = System.currentTimeMillis()
                        )
                        repository.saveReminder(updated)
                        scheduler.cancel(updated.id)
                    }
                    ACTION_SNOOZE -> {
                        val snoozedTime = System.currentTimeMillis() + SNOOZE_DURATION_MILLIS
                        val updated = reminder.copy(
                            scheduledTimeMillis = snoozedTime,
                            isCompleted = false,
                            isEnabled = true,
                            dismissedAt = null,
                            modifiedAt = System.currentTimeMillis()
                        )
                        repository.saveReminder(updated)
                        scheduler.schedule(updated)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

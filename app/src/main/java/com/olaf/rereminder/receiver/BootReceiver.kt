package com.olaf.rereminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.olaf.rereminder.data.ReminderRepository
import com.olaf.rereminder.scheduler.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )

        if (intent.action !in validActions) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ReminderRepository(context.applicationContext)
                val scheduler = ReminderScheduler(context.applicationContext)
                val now = System.currentTimeMillis()

                val reminders = repository.getAllReminders()
                for (reminder in reminders) {
                    if (reminder.isEnabled && !reminder.isCompleted && reminder.scheduledTimeMillis > now) {
                        scheduler.schedule(reminder)
                    } else if (reminder.scheduledTimeMillis <= now && reminder.isEnabled && !reminder.isCompleted) {
                        val expired = reminder.copy(
                            isCompleted = true,
                            modifiedAt = now
                        )
                        repository.saveReminder(expired)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}


package com.fishfeed.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.fishfeed.FishFeedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(
            ReminderIntents.EXTRA_SCHEDULE_ID,
            -1L
        )

        if (scheduleId < 0L) return

        val app = context.applicationContext as? FishFeedApp ?: return
        val action = intent.action

        Log.d(TAG, "Menerima $action untuk jadwal $scheduleId")

        // Mulai service audio ketika alarm diterima.
        when (action) {
            ReminderIntents.ACTION_FIRE,
            ReminderIntents.ACTION_REMIND,
            ReminderIntents.ACTION_TEST -> {
                try {
                    AlarmSoundService.start(context, scheduleId)

                    if (action == ReminderIntents.ACTION_TEST) {
                        app.container.notifier.showImmediate(
                            scheduleId,
                            testMode = true
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal memulai alarm", e)
                }
            }
        }

        val result = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = app.container.repository

                when (action) {
                    ReminderIntents.ACTION_FIRE -> {
                        repo.onScheduleFired(scheduleId)
                    }

                    ReminderIntents.ACTION_REMIND -> {
                        repo.onRemind(scheduleId)
                    }

                    ReminderIntents.ACTION_TEST -> {
                        // Hanya menguji suara dan notifikasi.
                    }

                    ReminderIntents.ACTION_CONFIRM -> {
                        try {
                            repo.confirm(scheduleId)
                        } finally {
                            app.container.notifier.cancel(scheduleId)
                            AlarmSoundService.stop(context)
                        }
                    }

                    ReminderIntents.ACTION_NOT_FED -> {
                        try {
                            repo.markNotFed(scheduleId)
                        } finally {
                            app.container.notifier.cancel(scheduleId)
                            AlarmSoundService.stop(context)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Gagal menangani $action untuk jadwal $scheduleId",
                    e
                )
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        private const val TAG = "FishFeedAlarm"
    }
}

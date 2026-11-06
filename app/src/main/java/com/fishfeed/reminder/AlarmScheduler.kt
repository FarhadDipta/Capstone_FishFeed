package com.fishfeed.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import android.os.Build
import com.fishfeed.AlarmActivity
import com.fishfeed.data.FeedSchedule
import com.fishfeed.util.TimeUtils
import java.text.DateFormat
import java.util.Date

/**
 * Pembungkus AlarmManager.
 * Setiap jadwal punya dua "slot" alarm:
 *  - FIRE   : alarm harian pada jam jadwal (selalu menunjuk kemunculan berikutnya).
 *  - REMIND : alarm pengulang sampai dikonfirmasi, atau alarm akhir masa jeda.
 */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Android 12+ mewajibkan izin "Alarm & pengingat" untuk alarm tepat waktu. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun scheduleNext(schedule: FeedSchedule, after: Long = TimeUtils.now()) {
        if (!schedule.reminderEnabled) {
            cancelFire(schedule.id)
            return
        }
        val at = TimeUtils.nextTrigger(schedule.hour, schedule.minute, after)
        setAlarm(schedule.id, at, pendingIntent(schedule.id, ReminderIntents.KIND_FIRE, ReminderIntents.ACTION_FIRE))
    }

    fun scheduleRemind(scheduleId: Long, at: Long) {
        setAlarm(scheduleId, at, pendingIntent(scheduleId, ReminderIntents.KIND_REMIND, ReminderIntents.ACTION_REMIND))
    }

    fun scheduleTest(scheduleId: Long, at: Long) {
        setAlarm(scheduleId, at, pendingIntent(scheduleId, ReminderIntents.KIND_TEST, ReminderIntents.ACTION_TEST))
    }

    fun cancelFire(scheduleId: Long) {
        try {
            alarmManager.cancel(pendingIntent(scheduleId, ReminderIntents.KIND_FIRE, ReminderIntents.ACTION_FIRE))
        } catch (e: Exception) {
            // Abaikan jika pembatalan alarm gagal
        }
    }

    fun cancelRemind(scheduleId: Long) {
        try {
            alarmManager.cancel(pendingIntent(scheduleId, ReminderIntents.KIND_REMIND, ReminderIntents.ACTION_REMIND))
        } catch (e: Exception) {
            // Abaikan jika pembatalan alarm gagal
        }
    }

    fun cancelAll(scheduleId: Long) {
        cancelFire(scheduleId)
        cancelRemind(scheduleId)
    }

    private fun pendingIntent(scheduleId: Long, kind: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ReminderIntents.requestCode(scheduleId, kind),
            ReminderIntents.receiverIntent(context, action, scheduleId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun setAlarm(scheduleId: Long, at: Long, pi: PendingIntent) {
        try {
            val showIntent = PendingIntent.getActivity(
                context,
                ReminderIntents.requestCode(scheduleId, ReminderIntents.KIND_OPEN),
                AlarmActivity.intent(context, scheduleId, openSnooze = false),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent), pi)
            Log.d(TAG, "Alarm jadwal $scheduleId didaftarkan pada ${DateFormat.getTimeInstance().format(Date(at))}")
        } catch (e: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                Log.d(TAG, "Alarm tidak tepat jadi fallback untuk jadwal $scheduleId: ${e.message}")
            } catch (e2: Exception) {
                Log.e(TAG, "Pendaftaran alarm jadwal $scheduleId gagal: ${e2.message}", e2)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pendaftaran alarm jadwal $scheduleId gagal: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "FishFeedAlarm"
    }
}

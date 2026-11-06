package com.fishfeed.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.fishfeed.AlarmActivity
import com.fishfeed.MainActivity
import com.fishfeed.R
import com.fishfeed.data.FeedSchedule
import com.fishfeed.data.PendingFeeding
import com.fishfeed.util.TimeUtils

class NotificationHelper(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        manager.deleteNotificationChannel("feeding_reminder")
        manager.deleteNotificationChannel("feeding_reminder_v2")
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_desc)

            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun notificationId(scheduleId: Long): Int = 1000 + scheduleId.toInt()

    /** Menampilkan (atau membunyikan ulang) notifikasi pengingat untuk satu jadwal. */
    fun show(
        pending: PendingFeeding,
        schedule: FeedSchedule,
        pondName: String,
        useFullScreen: Boolean = true
    ) {
        val id = schedule.id
        val timeText = TimeUtils.formatTime(schedule.hour, schedule.minute)
        val afterSnooze = pending.snoozeCount > 0

        // Mengetuk notifikasi atau layar terkunci membuka halaman Alarm Pakan (layar penuh).
        val openAlarm = PendingIntent.getActivity(
            context,
            ReminderIntents.requestCode(id, ReminderIntents.KIND_OPEN),
            AlarmActivity.intent(context, id, openSnooze = false),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullScreen = PendingIntent.getActivity(
            context,
            ReminderIntents.requestCode(id, ReminderIntents.KIND_FULL_SCREEN),
            AlarmActivity.intent(context, id, openSnooze = false),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val confirm = PendingIntent.getBroadcast(
            context,
            ReminderIntents.requestCode(id, ReminderIntents.KIND_CONFIRM),
            ReminderIntents.receiverIntent(context, ReminderIntents.ACTION_CONFIRM, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Jeda" membuka halaman Alarm Pakan dengan panel pilihan jeda (5/10/15/30 menit atau kustom).
        val snooze = PendingIntent.getActivity(
            context,
            ReminderIntents.requestCode(id, ReminderIntents.KIND_SNOOZE),
            AlarmActivity.intent(context, id, openSnooze = true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Waktunya memberi pakan!")
            .setContentText("$pondName - ${schedule.name} ($timeText)")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$pondName - ${schedule.name} ($timeText)\n" +
                            (if (schedule.note.isNotBlank()) "${schedule.note}\n" else "") +
                            if (afterSnooze) "Masa jeda berakhir. Apakah pakan sudah diberikan?"
                            else "Waktunya memberi pakan lele."
                )
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAlarm)
            .apply {
                if (useFullScreen) {
                    setFullScreenIntent(fullScreen, true)
                }
            } // layar terkunci: tampilkan halaman Alarm Pakan
            .setOngoing(false)       // tidak bisa digeser; hilang hanya setelah konfirmasi/jeda
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .addAction(0, "Sudah Diberi Pakan", confirm)

        if (afterSnooze) {
            val notFed = PendingIntent.getBroadcast(
                context,
                ReminderIntents.requestCode(
                    id,
                    ReminderIntents.KIND_NOT_FED
                ),
                ReminderIntents.receiverIntent(
                    context,
                    ReminderIntents.ACTION_NOT_FED,
                    id
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(0, "Tidak Diberi Pakan", notFed)
        } else {
            builder.addAction(0, "Jeda", snooze)
        }

        try {
            manager.notify(notificationId(id), builder.build())
            Log.d(TAG, "Notifikasi ditampilkan untuk jadwal $id")
        } catch (e: Exception) {
            Log.e(TAG, "Notifikasi gagal untuk jadwal $id: ${e.message}", e)
        }
    }

    fun showImmediate(scheduleId: Long, testMode: Boolean = false) {
        val openAlarm = PendingIntent.getActivity(
            context,
            ReminderIntents.requestCode(scheduleId, ReminderIntents.KIND_FULL_SCREEN),
            AlarmActivity.intent(context, scheduleId, openSnooze = false, testMode = testMode),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val confirm = PendingIntent.getBroadcast(
            context,
            ReminderIntents.requestCode(scheduleId, ReminderIntents.KIND_CONFIRM),
            ReminderIntents.receiverIntent(context, ReminderIntents.ACTION_CONFIRM, scheduleId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snooze = PendingIntent.getActivity(
            context,
            ReminderIntents.requestCode(scheduleId, ReminderIntents.KIND_SNOOZE),
            AlarmActivity.intent(context, scheduleId, openSnooze = true, testMode = testMode),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(if (testMode) R.string.reminder_test_title else R.string.reminder_title))
            .setContentText(context.getString(if (testMode) R.string.reminder_test_content else R.string.reminder_processing))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAlarm)
            .setFullScreenIntent(openAlarm, true)
            .setOngoing(false)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .addAction(0, context.getString(R.string.alarm_confirm), confirm)
            .addAction(0, context.getString(R.string.alarm_snooze), snooze)
            .build()
        try {
            manager.notify(notificationId(scheduleId), notification)
            Log.d(TAG, "Notifikasi awal ditampilkan untuk jadwal $scheduleId")
        } catch (e: Exception) {
            Log.e(TAG, "Notifikasi awal gagal untuk jadwal $scheduleId: ${e.message}", e)
        }
    }

    /** Notifikasi percobaan (bukan pengingat pakan sungguhan). */
    fun showTest() {
        val openApp = PendingIntent.getActivity(
            context,
            TEST_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Uji pengingat FishFeed")
            .setContentText("Notifikasi berfungsi. Pengingat pakan akan tampil seperti ini.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(TEST_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Notifikasi uji gagal: ${e.message}", e)
        }
    }

    fun cancel(scheduleId: Long) {
        manager.cancel(notificationId(scheduleId))
    }

    companion object {
        const val CHANNEL_ID = "feeding_reminder_v3"
        private const val TAG = "FishFeedAlarm"
        private const val TEST_NOTIFICATION_ID = 99
        private const val TEST_REQUEST_CODE = 1_999_999_000
    }
}
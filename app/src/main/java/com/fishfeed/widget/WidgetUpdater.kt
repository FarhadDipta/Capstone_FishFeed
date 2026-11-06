package com.fishfeed.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.fishfeed.MainActivity
import com.fishfeed.R
import com.fishfeed.data.AppDatabase
import com.fishfeed.reminder.ReminderIntents
import com.fishfeed.util.TimeUtils

/** Membaca database lalu menggambar ulang semua widget FishFeed di home screen. */
object WidgetUpdater {
    private const val WIDGET_OPEN_REQUEST_CODE = 2_000_000_000

    private data class WidgetInfo(
        val pondName: String,
        val scheduleName: String,
        val timeText: String,
        val statusText: String,
        /** Tidak null jika ada kejadian "Belum Diberi Pakan" yang bisa dikonfirmasi dari widget. */
        val confirmScheduleId: Long?
    )

    suspend fun update(context: Context, db: AppDatabase) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, FeedingWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = build(context, load(db))
        manager.updateAppWidget(ids, views)
    }

    private suspend fun load(db: AppDatabase): WidgetInfo {
        val now = TimeUtils.now()

        // 1) Prioritas: jadwal yang sudah tiba tetapi belum dikonfirmasi.
        for (p in db.pendingDao().getAll()) {
            val s = db.scheduleDao().get(p.scheduleId) ?: continue
            val pond = db.pondDao().get(s.pondId) ?: continue
            val status = if (p.snoozeUntil > now) "Dijeda hingga ${TimeUtils.formatClock(p.snoozeUntil)}"
            else "Belum diberi pakan"
            return WidgetInfo(pond.name, s.name, TimeUtils.formatTime(s.hour, s.minute), status, s.id)
        }

        // 2) Jadwal aktif terdekat berikutnya.
        val next = db.scheduleDao().getAllWithPond()
            .filter { it.schedule.reminderEnabled }
            .minByOrNull { TimeUtils.nextTrigger(it.schedule.hour, it.schedule.minute, now) }
            ?: return WidgetInfo("Belum ada jadwal", "Tambahkan jadwal di aplikasi", "--:--", "", null)

        val s = next.schedule
        val trigger = TimeUtils.nextTrigger(s.hour, s.minute, now)
        val day = if (TimeUtils.isToday(trigger)) "hari ini" else "besok"
        return WidgetInfo(
            next.pondName, s.name, TimeUtils.formatTime(s.hour, s.minute), "Menunggu waktu ($day)", null
        )
    }

    private fun build(context: Context, info: WidgetInfo): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_feeding)
        views.setTextViewText(R.id.widget_pond, info.pondName)
        views.setTextViewText(R.id.widget_schedule, info.scheduleName)
        views.setTextViewText(R.id.widget_time, info.timeText)
        views.setTextViewText(R.id.widget_status, info.statusText)

        val openApp = PendingIntent.getActivity(
            context,
            WIDGET_OPEN_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp)

        if (info.confirmScheduleId != null) {
            val confirm = PendingIntent.getBroadcast(
                context,
                ReminderIntents.requestCode(info.confirmScheduleId, ReminderIntents.KIND_WIDGET_CONFIRM),
                ReminderIntents.receiverIntent(context, ReminderIntents.ACTION_CONFIRM, info.confirmScheduleId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_action, confirm)
            views.setViewVisibility(R.id.widget_action, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_action, View.GONE)
        }
        return views
    }
}

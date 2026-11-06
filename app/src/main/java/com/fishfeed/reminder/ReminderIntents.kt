package com.fishfeed.reminder

import android.content.Context
import android.content.Intent

/** Konstanta Intent untuk alarm, notifikasi, dan widget. */
object ReminderIntents {
    const val ACTION_FIRE = "com.fishfeed.action.FIRE"          // waktu jadwal tiba
    const val ACTION_REMIND = "com.fishfeed.action.REMIND"      // alarm pengulang / jeda berakhir
    const val ACTION_CONFIRM = "com.fishfeed.action.CONFIRM"    // tombol "Sudah Diberi Pakan"
    const val ACTION_NOT_FED = "com.fishfeed.action.NOT_FED"    // tombol "Tidak Diberi Pakan"
    const val ACTION_TEST = "com.fishfeed.action.TEST"

    const val EXTRA_SCHEDULE_ID = "schedule_id"
    const val EXTRA_TEST_MODE = "test_mode"
    /** true = layar alarm langsung membuka panel pilihan jeda (bottom sheet). */
    const val EXTRA_OPEN_SNOOZE = "open_snooze_sheet"

    const val KIND_FIRE = 0
    const val KIND_REMIND = 1
    const val KIND_CONFIRM = 2
    const val KIND_SNOOZE = 3
    const val KIND_NOT_FED = 4
    const val KIND_OPEN = 5
    const val KIND_WIDGET_CONFIRM = 6
    const val KIND_FULL_SCREEN = 7
    const val KIND_TEST = 8

    /** Request code unik per jadwal dan jenis PendingIntent. */
    fun requestCode(scheduleId: Long, kind: Int): Int = 31 * scheduleId.hashCode() + kind

    fun receiverIntent(context: Context, action: String, scheduleId: Long): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        }
}

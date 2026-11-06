package com.fishfeed

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import com.fishfeed.reminder.ReminderIntents
import com.fishfeed.ui.screens.AlarmScreen
import com.fishfeed.ui.theme.FishFeedTheme

/**
- Halaman Alarm Pakan: layar penuh yang tampil saat alarm pakan berbunyi (juga di atas layar kunci).
- Dibuka dari notifikasi, tombol "Jeda" pada notifikasi, atau tombol "Jeda" di Beranda.
 */
class AlarmActivity : ComponentActivity() {
    private data class Request(val scheduleId: Long, val openSnooze: Boolean, val testMode: Boolean)

    private val request = mutableStateOf(Request(-1L, false, false))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        readRequest(intent)
        val app = application as? FishFeedApp
        val repo = app?.container?.repository

        setContent {
            FishFeedTheme {
                if (repo != null) {
                    val r = request.value

                    key(r.scheduleId, r.openSnooze, r.testMode) {
                        AlarmScreen(
                            repo = repo,
                            scheduleId = r.scheduleId,
                            openSnooze = r.openSnooze,
                            testMode = r.testMode,
                            onClose = {
                                if (r.testMode) app.container.notifier.cancel(r.scheduleId)
                                finish()
                            }
                        )
                    }
                } else {
                    finish()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readRequest(intent)
    }

    private fun readRequest(intent: Intent?) {
        request.value = Request(
            scheduleId = intent?.getLongExtra(
                ReminderIntents.EXTRA_SCHEDULE_ID,
                -1L
            ) ?: -1L,

            openSnooze = intent?.getBooleanExtra(
                ReminderIntents.EXTRA_OPEN_SNOOZE,
                false
            ) ?: false,

            testMode = intent?.getBooleanExtra(
                ReminderIntents.EXTRA_TEST_MODE,
                false
            ) ?: false
        )
    }

    companion object {
        fun intent(context: Context, scheduleId: Long, openSnooze: Boolean, testMode: Boolean = false): Intent =
            Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(ReminderIntents.EXTRA_SCHEDULE_ID, scheduleId)
                putExtra(ReminderIntents.EXTRA_OPEN_SNOOZE, openSnooze)
                putExtra(ReminderIntents.EXTRA_TEST_MODE, testMode)
            }
    }
}
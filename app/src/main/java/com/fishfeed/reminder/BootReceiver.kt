package com.fishfeed.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.fishfeed.FishFeedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarm hilang saat HP restart / aplikasi diperbarui, jadi dipasang ulang dari database. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext as? FishFeedApp ?: return
        val repo = app.container.repository
        val restorePending = action in RESTORE_ACTIONS
        val catchUp = action == Intent.ACTION_BOOT_COMPLETED
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repo.restoreAll(restorePending = restorePending, catchUp = catchUp)
            } catch (e: Exception) {
                Log.e(TAG, "Pemulihan alarm gagal untuk $action", e)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        private const val TAG = "FishFeedAlarm"
        private val RESTORE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}


package com.fishfeed.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.fishfeed.FishFeedApp
import com.fishfeed.R
import com.fishfeed.util.SoundHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AlarmSoundService : Service() {

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {
            ACTION_STOP -> {
                stopAlarm()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)

                if (scheduleId < 0L) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                // Foreground harus dimulai segera.
                startForeground(
                    NOTIFICATION_ID,
                    createNotification()
                )

                serviceScope.launch {
                    try {
                        val app = application as? FishFeedApp
                            ?: return@launch

                        val sound = app.container.repository
                            .getAlarmSound(scheduleId)
                            ?: SoundHelper.SOUND_TYPE_DEFAULT

                        Log.d(TAG, "Memulai suara alarm jadwal $scheduleId")

                        SoundHelper.playTestSound(
                            applicationContext,
                            sound,
                            durationMs = 24 * 60 * 60 * 1000L,
                            asAlarm = true
                        )

                    } catch (e: Exception) {
                        Log.e(TAG, "Gagal memutar alarm", e)
                    }
                }

                return START_NOT_STICKY
            }

            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm FishFeed",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga layanan suara alarm FishFeed tetap aktif"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("FishFeed")
            .setContentText("Pengingat pemberian pakan sedang aktif")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun stopAlarm() {
        SoundHelper.stopSound()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        SoundHelper.stopSound()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AlarmSoundService"

        private const val CHANNEL_ID = "fishfeed_alarm_service"
        private const val NOTIFICATION_ID = 9876

        private const val ACTION_START =
            "com.fishfeed.action.START_ALARM_SOUND"

        private const val ACTION_STOP =
            "com.fishfeed.action.STOP_ALARM_SOUND"

        private const val EXTRA_SCHEDULE_ID = "schedule_id"

        fun start(context: Context, scheduleId: Long) {
            val intent = Intent(
                context,
                AlarmSoundService::class.java
            ).apply {
                action = ACTION_START
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(
                context,
                AlarmSoundService::class.java
            )

            context.stopService(intent)
        }
    }
}

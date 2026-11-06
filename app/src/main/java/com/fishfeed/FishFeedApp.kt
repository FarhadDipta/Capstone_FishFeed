
package com.fishfeed

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.fishfeed.data.AppDatabase
import com.fishfeed.data.FeedingRepository
import com.fishfeed.reminder.AlarmScheduler
import com.fishfeed.reminder.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppContainer(app: Application) {
    val db: AppDatabase = AppDatabase.build(app)
    val scheduler = AlarmScheduler(app)
    val notifier = NotificationHelper(app)
    val repository = FeedingRepository(app, db, scheduler, notifier)
}

class FishFeedApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val notificationMutex = Mutex()

    override fun onCreate() {
        super.onCreate()

        com.fishfeed.ui.theme.ThemePrefs.init(this)

        container = AppContainer(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->

                when (event) {
                    Lifecycle.Event.ON_START -> {
                        appScope.launch {
                            notificationMutex.withLock {
                                container.repository
                                    .hidePendingSystemNotifications()
                            }
                        }
                    }

                    Lifecycle.Event.ON_STOP -> {
                        appScope.launch {
                            notificationMutex.withLock {
                                container.repository
                                    .showPendingSystemNotifications()
                            }
                        }
                    }

                    else -> Unit
                }
            }
        )
    }
}

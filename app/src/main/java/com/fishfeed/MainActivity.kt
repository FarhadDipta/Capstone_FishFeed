package com.fishfeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.fishfeed.ui.AppNav
import com.fishfeed.ui.theme.FishFeedTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as? FishFeedApp
        val repo = app?.container?.repository

        if (repo != null) {
            // Pastikan alarm harian selalu terpasang setiap aplikasi dibuka (mis. setelah izin alarm diberikan).
            lifecycleScope.launch {
                try {
                    repo.clearPendingNotificationsOnAppOpen()

                    repo.restoreAll(
                        restorePending = false,
                        catchUp = false
                    )
                } catch (e: Exception) {
                    android.util.Log.e(
                        "FishFeed",
                        "Gagal membersihkan notifikasi saat aplikasi dibuka",
                        e
                    )
                }
            }
        }

        setContent {
            FishFeedTheme {
                if (repo != null) {
                    AppNav(repo = repo)
                }
            }
        }
    }
}

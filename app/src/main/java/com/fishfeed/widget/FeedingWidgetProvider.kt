package com.fishfeed.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.fishfeed.FishFeedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FeedingWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as? FishFeedApp ?: return
        val db = app.container.db
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater.update(context, db)
            } catch (e: Exception) {
                // Tangani error update widget
            } finally {
                result.finish()
            }
        }
    }
}

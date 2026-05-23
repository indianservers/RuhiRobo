package com.indianservers.ruhi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.RemoteViews
import com.indianservers.ruhi.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RuhiWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("ruhi_widget_state", Context.MODE_PRIVATE)
        val expression = runCatching {
            RobotFaceView.Expression.valueOf(prefs.getString("expression", RobotFaceView.Expression.CURIOUS.name).orEmpty())
        }.getOrDefault(RobotFaceView.Expression.CURIOUS)
        val label = prefs.getString("mood_label", "Feeling Curious") ?: "Feeling Curious"
        val timestamp = prefs.getLong("last_seen", System.currentTimeMillis())
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.ruhi_widget).apply {
                setTextViewText(R.id.widgetMoodTextView, label)
                setTextViewText(R.id.widgetTimestampTextView, "Last seen $time")
                setImageViewBitmap(R.id.widgetFaceImageView, renderFaceBitmap(context, expression, 220))
                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    fun renderFaceBitmap(expression: RobotFaceView.Expression, size: Int, context: Context): Bitmap {
        return renderFaceBitmap(context, expression, size)
    }

    private fun renderFaceBitmap(context: Context, expression: RobotFaceView.Expression, size: Int): Bitmap {
        val view = RobotFaceView(context).apply {
            setExpression(expression)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, size, size)
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            view.draw(Canvas(it))
        }
    }
}

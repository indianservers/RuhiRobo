package com.indianservers.ruhi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.indianservers.ruhi.R
import com.indianservers.ruhi.hardware.BleRobotManager
import com.indianservers.ruhi.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RuhiService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var ble: BleRobotManager

    override fun onCreate() {
        super.onCreate()
        ble = BleRobotManager(this)
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Ruhi is active"))
        scope.launch {
            ble.batteryLevel.collectLatest { level ->
                if (level in 1..14) {
                    sendBroadcast(Intent("com.indianservers.ruhi.BATTERY_LOW").putExtra("level", level))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            ble.disconnect()
            stopSelf()
            return START_NOT_STICKY
        }
        ble.startScan()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        ble.disconnect()
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Ruhi hardware", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Ruhi")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_DISCONNECT = "com.indianservers.ruhi.DISCONNECT_HARDWARE"
        private const val CHANNEL_ID = "ruhi_hardware"
        private const val NOTIFICATION_ID = 42
    }
}

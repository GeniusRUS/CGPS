package com.genius.cgps

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import kotlin.coroutines.CoroutineContext
import androidx.core.app.NotificationCompat
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class LocationService : Service(), CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    private val updater: CGGPS by lazy { CGGPS(this) }
    private var updaterJob: Job? = null

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onDestroy() {
        updaterJob?.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updaterJob = updater.requestUpdates(actor {
            this.channel.consumeEach {
                Log.d(TAG, it.getOrNull()?.toString() ?: "null")
            }
        })

        val cancelIntent = Intent(this, LocationService::class.java).apply {
            putExtra(NOTIFICATION_ACTION, true)
        }
        val pendingIntent = PendingIntent.getService(this, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            manager?.createNotificationChannel(
                NotificationChannel("channelLocation", "Channel for location service", NotificationManager.IMPORTANCE_HIGH)
            )
            Notification.Builder(this, "channelLocation")
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Location service is working")
                .setAutoCancel(true)
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_menu_mylocation),
                        "Stop",
                        pendingIntent
                    ).build()
                )
                .build()
        } else {
            NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Location service is working")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .addAction(
                    NotificationCompat.Action.Builder(
                        android.R.drawable.ic_menu_mylocation,
                        "Stop",
                        pendingIntent
                    ).build()
                )
                .build()
        }
        startForeground(1, notification)

        return START_NOT_STICKY
    }

    inner class LocalBinder : Binder() {
        internal val service: LocationService
            get() = this@LocationService
    }

    companion object {
        private const val TAG = "CGPSLocationService"
        const val NOTIFICATION_ACTION = "notification_action"
    }
}
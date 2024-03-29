package com.genius.example

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import androidx.core.app.NotificationCompat
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import com.genius.cgps.CGPS
import com.genius.cgps.GoogleCGPS

class LocationService : Service(), CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + supervisorJob

    private val supervisorJob: Job by lazy { SupervisorJob() }
    private val updater: CGPS by lazy { GoogleCGPS(this) }
    private var updaterJob: Job? = null

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        supervisorJob.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(NOTIFICATION_ACTION, false) == true) {
            updaterJob?.cancel()
            stopSelf()
            return START_NOT_STICKY
        }

        updaterJob = launch {
            updater.requestUpdates().collect {
                ContextCompat.getSystemService(this@LocationService, NotificationManager::class.java)?.let { manager ->
                    val cancelIntent: PendingIntent = createCancelIntentAndChannel()
                    val location = it.getOrNull()?.let {
                        buildString {
                            append(it.provider)
                            append(":")
                            append(it.latitude)
                            append(",")
                            append(it.longitude)
                            append(";a:")
                            append(it.accuracy)
                            append(";s:")
                            append(it.speed)
                        }
                    }
                    val notification: Notification = createCancelNotification(cancelIntent, location ?: getString(R.string.location_service_working))
                    manager.notify(1, notification)
                } ?: Log.d(TAG, it.getOrNull()?.toString() ?: "null")
            }
        }

        val cancelIntent: PendingIntent = createCancelIntentAndChannel()
        val notification: Notification = createCancelNotification(cancelIntent)
        startForeground(1, notification)

        return START_NOT_STICKY
    }

    private fun createCancelIntentAndChannel(): PendingIntent {
        val cancelIntent = Intent(this, LocationService::class.java).apply {
            putExtra(NOTIFICATION_ACTION, true)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getService(this, 0, cancelIntent, flags)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            manager?.createNotificationChannel(
                NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.location_service_description_text), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        return pendingIntent
    }

    private fun createCancelNotification(cancelIntent: PendingIntent, contentText: String = getString(R.string.location_service_working)): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_my_location_white_24dp)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setSilent(true)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_cancel_white_24dp,
                    getString(R.string.stop_service_text),
                    cancelIntent
                ).build()
            )
            .build()
    }

    inner class LocalBinder : Binder() {
        internal val service: LocationService
            get() = this@LocationService
    }

    companion object {
        private const val TAG = "CGPSLocationService"
        private const val NOTIFICATION_CHANNEL_ID = "channelLocation"
        const val NOTIFICATION_ACTION = "notification_action"
    }
}
package com.genius.cgps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.*
import android.os.Bundle
import android.provider.Settings
import android.support.annotation.IntRange
import android.support.annotation.RequiresPermission
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.SendChannel
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeoutException
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.suspendCoroutine

class CGPS(private val context: Context) {

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?

    @Throws(LocationException::class, LocationDisabledException::class, SecurityException::class)
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun lastLocation(accuracy: Accuracy = Accuracy.COARSE) = suspendCoroutine<Location> { coroutine ->
        if (manager == null) {
            coroutine.resumeWithException(LocationException("Location manager not found"))
        } else if (!isLocationEnabled(manager)) {
            coroutine.resumeWithException(LocationDisabledException())
        } else if (!checkPermission(context, true)) {
            coroutine.resumeWithException(SecurityException("Permissions for GPS was not given"))
        } else {
            val location = manager.getLastKnownLocation(manager.getBestProvider(getCriteria(accuracy), true))
            if (location == null) {
                coroutine.resumeWithException(LocationException("Last location not found"))
            } else {
                coroutine.resume(location)
            }
        }
    }

    @Throws(LocationException::class, LocationDisabledException::class, SecurityException::class, TimeoutException::class)
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION])
    suspend fun actualLocation(accuracy: Accuracy = Accuracy.COARSE, @IntRange(from = 0) timeout: Long = 5000L) = suspendCoroutine<Location> { coroutine ->
        if (manager == null) {
            coroutine.resumeWithException(LocationException("Location manager not found"))
        } else if (!isLocationEnabled(manager)) {
            coroutine.resumeWithException(LocationDisabledException())
        } else if (!checkPermission(context, false)) {
            coroutine.resumeWithException(SecurityException("Permissions for GPS was not given"))
        } else {
            val listener = getLocationListener(coroutine)
            manager.requestSingleUpdate(getCriteria(accuracy), listener, context.mainLooper)
            launch {
                delay(timeout)
                cancelWithTimeout(coroutine, listener, timeout)
            }
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION])
    fun requestUpdates(listener: SendChannel<Pair<Location?, Exception?>>, context: CoroutineContext = UI, accuracy: Accuracy = Accuracy.COARSE, @IntRange(from = 0) timeout: Long = 5000L, @IntRange(from = 0) interval: Long = 10000L) = Job().apply {
        launch(parent = this) {
            while (true) {
                launch(context = context, parent = this@apply) {
                    try {
                        val location = actualLocation(accuracy, timeout)
                        listener.offer(Pair(location, null))
                    } catch (e: Exception) {
                        listener.offer(Pair(null, e))
                    }
                }

                delay(interval)
            }
        }.invokeOnCompletion {
            listener.close()
        }
    }

    private fun getLocationListener(coroutine: Continuation<Location>): LocationListener {
        return object : LocationListener {
            override fun onLocationChanged(location: Location?) {
                if (location == null) {
                    coroutine.resumeWithException(LocationException("Location not found"))
                } else {
                    coroutine.resume(location)
                }
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                coroutine.resumeWithException(LocationException("Status $provider changed: $status with extras $extras"))
            }

            override fun onProviderEnabled(provider: String?) {
                coroutine.resumeWithException(LocationException("Provider $provider enabled"))
            }

            override fun onProviderDisabled(provider: String?) {
                coroutine.resumeWithException(LocationException("Provider $provider disabled"))
            }
        }
    }

    private fun getCriteria(accuracy: Accuracy): Criteria {
        return Criteria().apply {
            setAccuracy(accuracy.accuracy)
            isCostAllowed = true
            powerRequirement = accuracy.power
        }
    }

    private fun cancelWithTimeout(coroutine: Continuation<Location>, listener: LocationListener, timeout: Long) {
        if (coroutine.context.isActive) {
            manager?.removeUpdates(listener)
            coroutine.resumeWithException(TimeoutException("Location timeout on $timeout ms"))
        }
    }

    enum class Accuracy(val accuracy: Int, val power: Int) {
        FINE(Criteria.ACCURACY_FINE, Criteria.POWER_HIGH),
        COARSE(Criteria.ACCURACY_COARSE, Criteria.POWER_MEDIUM),
    }
}

@Throws(IOException::class)
fun Location.toAddress(context: Context, locale: Locale = Locale.getDefault()): Address? = Geocoder(context, locale).getFromLocation(this.latitude, this.longitude, 1).firstOrNull()

fun Context.openSettings() = startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
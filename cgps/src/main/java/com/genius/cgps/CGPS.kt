@file:Suppress("UNUSED")

package com.genius.cgps

import android.content.Context
import android.content.Intent
import android.location.*
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.IntRange
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeoutException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CGPS(private val context: Context): CoroutineScope {

    override val coroutineContext = Dispatchers.Default

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?

    @Throws(LocationException::class, LocationDisabledException::class, SecurityException::class)
    suspend fun lastLocation(accuracy: Accuracy = Accuracy.COARSE) = suspendCoroutine<Location> { coroutine ->
        if (manager == null) {
            coroutine.resumeWithException(LocationException("Location manager not found"))
        } else if (!isLocationEnabled(manager)) {
            coroutine.resumeWithException(LocationDisabledException())
        } else if (!checkPermission(context, true)) {
            coroutine.resumeWithException(SecurityException("Permissions for GPS was not given"))
        } else {
            val location = manager.getLastKnownLocation(manager.getBestProvider(accuracy.toCriteria(), true))
            if (location == null) {
                coroutine.resumeWithException(LocationException("Last location not found"))
            } else {
                coroutine.resume(location)
            }
        }
    }

    @Throws(LocationException::class, LocationDisabledException::class, SecurityException::class, TimeoutException::class)
    suspend fun actualLocation(accuracy: Accuracy = Accuracy.COARSE, @IntRange(from = 0) timeout: Long = 5000L): Location {
        val coroutine = CompletableDeferred<Location>()
        if (manager == null) {
            coroutine.completeExceptionally(LocationException("Location manager not found"))
        } else if (!isLocationEnabled(manager)) {
            coroutine.completeExceptionally(LocationDisabledException())
        } else if (!checkPermission(context, false)) {
            coroutine.completeExceptionally(SecurityException("Permissions for GPS was not given"))
        } else {
            val listener = coroutine.createLocationListener()

            manager.requestSingleUpdate(accuracy.toCriteria(), listener, context.mainLooper)

            withContext(Dispatchers.Default) {
                delay(timeout)
                coroutine.cancelWithTimeout(listener, timeout)
            }
        }

        return coroutine.await()
    }

    fun requestUpdates(listener: SendChannel<Result<Location>>, context: CoroutineContext = Dispatchers.Main, accuracy: Accuracy = Accuracy.COARSE, @IntRange(from = 0) timeout: Long = 5000L, @IntRange(from = 0) interval: Long = 10000L) = Job().apply {
        launch {
            while (true) {
                launch(context = context) {
                    try {
                        val location = actualLocation(accuracy, timeout)
                        listener.offer(Result.success(location))
                    } catch (e: Exception) {
                        listener.offer(Result.failure(e))
                    }
                }

                delay(interval)
            }
        }.invokeOnCompletion {
            listener.close()
        }
    }

    private fun CompletableDeferred<Location>.createLocationListener(): LocationListener {
        return object : LocationListener {
            override fun onLocationChanged(location: Location?) {
                if (location == null) {
                    completeExceptionally(LocationException("Location not found"))
                } else {
                    complete(location)
                }
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                completeExceptionally(LocationException("Status $provider changed: $status with extras $extras"))
            }

            override fun onProviderEnabled(provider: String?) {
                completeExceptionally(LocationException("Provider $provider enabled"))
            }

            override fun onProviderDisabled(provider: String?) {
                completeExceptionally(LocationException("Provider $provider disabled"))
            }
        }
    }

    private fun CompletableDeferred<Location>.cancelWithTimeout(listener: LocationListener, timeout: Long) {
        if (isActive) {
            manager?.removeUpdates(listener)
            completeExceptionally(TimeoutException("Location timeout on $timeout ms"))
        }
    }

    private fun Accuracy.toCriteria(): Criteria = Criteria().apply {
        accuracy = accuracy
        isCostAllowed = true
        powerRequirement = power
    }

    enum class Accuracy(val accuracy: Int, val power: Int) {
        FINE(Criteria.ACCURACY_FINE, Criteria.POWER_HIGH),
        COARSE(Criteria.ACCURACY_COARSE, Criteria.POWER_MEDIUM),
    }
}

@Throws(IOException::class)
fun Location.toAddress(context: Context, locale: Locale = Locale.getDefault()): Address? = Geocoder(context, locale).getFromLocation(this.latitude, this.longitude, 1).firstOrNull()

fun Context.openSettings() = startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
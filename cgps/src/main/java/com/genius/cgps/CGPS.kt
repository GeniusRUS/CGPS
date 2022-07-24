@file:Suppress("UNUSED")

package com.genius.cgps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.*
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.IntRange
import androidx.annotation.WorkerThread
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * A class that uses the built-in location services in the Android framework
 *
 * Should be guaranteed to work on all devices that have a GPS module
 *
 * The use of this class is not as preferable as its analogue [CGGPS],
 * since working with GPS directly is a very energy-intensive operation
 *
 * @property context is needed to check permissions and for working with system location services
 * @constructor receiving [Context] instance for working with Android geolocation service
 *
 * @author Viktor Likhanov
 */
class CGPS(private val context: Context) {

    private val manager: LocationManager? by lazy { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }

    /**
     * Retrieves the last known location of the user from the device's GPS adapter
     *
     * Required permission [Manifest.permission.ACCESS_COARSE_LOCATION]
     *
     * @return [Location] user location
     * @throws LocationException in case of undefined errors. In this case, the reason is written in [LocationException.message]
     * @throws LocationDisabledException in case of disabled GPS adapter
     * @throws SecurityException in case of missing permission [Manifest.permission.ACCESS_COARSE_LOCATION] for obtaining geolocation
     */
    @Throws(LocationException::class, LocationDisabledException::class, SecurityException::class)
    suspend fun lastLocation(accuracy: Accuracy = Accuracy.COARSE) = suspendCoroutine<Location> { coroutine ->
        if (manager == null) {
            coroutine.resumeWithException(LocationException("Location manager not found"))
        } else if (!isLocationEnabled(manager)) {
            coroutine.resumeWithException(LocationDisabledException())
        } else if (!context.checkPermission(true)) {
            coroutine.resumeWithException(SecurityException("Permissions for GPS was not given"))
        } else {
            val provider = manager?.getBestProvider(accuracy.toCriteria(), true)
            if (provider == null) {
                coroutine.resumeWithException(LocationException("Provider not found for this accuracy: ${accuracy.accuracy} and power: ${accuracy.power}"))
            } else {
                val location = manager?.getLastKnownLocation(provider)
                if (location == null) {
                    coroutine.resumeWithException(LocationException("Last location not found"))
                } else {
                    coroutine.resume(location)
                }
            }
        }
    }

    /**
     * Receives the current location of the user from the device's GPS service
     *
     * For flexibility in requesting a location, you can specify [accuracy], [timeout]
     *
     * The stages of checking and possible throwing of errors coincide with the order of the error descriptions below.
     *
     * Required permission [Manifest.permission.ACCESS_FINE_LOCATION]
     *
     * @param accuracy the accuracy of the obtained coordinates. The default is [Accuracy.COARSE]
     * @param timeout timeout for getting coordinates. Default 5000 milliseconds
     * @return [Location] user location
     * @throws LocationException in case of undefined errors. In this case, the reason is written in [LocationException.message]
     * @throws LocationDisabledException in case of disabled GPS adapter
     * @throws SecurityException in case of missing permissions for obtaining geolocation
     * @throws TimeoutException in case the timeout of the geolocation request has been exceeded
     */
    @Throws(LocationException::class, LocationDisabledException::class, SecurityException::class, TimeoutException::class)
    suspend fun actualLocation(accuracy: Accuracy = Accuracy.COARSE,
                               @IntRange(from = 0) timeout: Long = 5_000L): Location {
        val coroutine = CompletableDeferred<Location>()
        if (manager == null) {
            coroutine.completeExceptionally(LocationException("Location manager not found"))
        } else if (!isLocationEnabled(manager)) {
            coroutine.completeExceptionally(LocationDisabledException())
        } else if (!context.checkPermission(false)) {
            coroutine.completeExceptionally(SecurityException("Permissions for GPS was not given"))
        } else {
            val listener = coroutine.createLocationListener()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val provider = manager?.getBestProvider(accuracy.toCriteria(), true)
                if (provider == null || manager == null) {
                    coroutine.completeExceptionally(LocationException("LocationManager not instantiated or provider instance is not defined"))
                    return coroutine.await()
                }
                manager?.getCurrentLocation(
                    provider,
                    null,
                    context.mainExecutor
                ) { location ->
                    coroutine.complete(location)
                }
            } else {
                @Suppress("DEPRECATION")
                manager?.requestSingleUpdate(accuracy.toCriteria(), listener, context.mainLooper)
            }

            try {
                withTimeout(timeout) {
                    coroutine.await()
                }
            } catch (e: TimeoutCancellationException) {
                if (coroutine.isActive) {
                    coroutine.completeExceptionally(TimeoutException("Location timeout on $timeout ms"))
                }
            } finally {
                manager?.removeUpdates(listener)
            }
        }

        return coroutine.await()
    }

    /**
     * Receives the current location of the user from the device's GPS service
     *
     * For flexibility in requesting a location, you can specify [accuracy], [timeout]
     *
     * Since a [Job] instance is returned, there is a mechanism that can control the life cycle of this request
     *
     * At the end of the work on receiving coordinates, it closes [SendChannel] and unsubscribes [LocationManager] from its listener
     *
     * Required permission [Manifest.permission.ACCESS_FINE_LOCATION]
     *
     * @param accuracy the accuracy of the obtained coordinates. The default is [Accuracy.COARSE]
     * @param timeout timeout for getting coordinates. The default is 10_000 milliseconds
     * @param interval time interval for getting coordinates. Default 5000 milliseconds
     * @return [flow] stream with results [Result]
     * @throws LocationException in case of undefined errors. In this case, the reason is written in [LocationException.message]
     * @throws LocationDisabledException in case of disabled GPS adapter
     * @throws SecurityException in case of missing permissions for obtaining geolocation
     * @throws TimeoutException in case the timeout of the geolocation request has been exceeded
     */
    fun requestUpdates(accuracy: Accuracy = Accuracy.COARSE,
                       @IntRange(from = 0) timeout: Long = 10_000L,
                       @IntRange(from = 0) interval: Long = 5_000L) = flow {
        while (true) {
            try {
                val location = actualLocation(accuracy, timeout)
                emit(Result.success(location))
            } catch (e: Exception) {
                emit(Result.failure(e))
            }

            delay(interval)
        }
    }

    private fun CompletableDeferred<Location>.createLocationListener(): LocationListener {
        return object : LocationListener {
            override fun onLocationChanged(location: Location) {
                complete(location)
            }

            @Deprecated(message = "Deprecated for Android Q+", replaceWith = ReplaceWith(""))
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    completeExceptionally(LocationException("Status $provider changed: $status with extras $extras"))
                }
            }

            override fun onProviderEnabled(provider: String) {
                completeExceptionally(LocationException("Provider $provider enabled"))
            }

            override fun onProviderDisabled(provider: String) {
                completeExceptionally(LocationException("Provider $provider disabled"))
            }
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

/**
 * Converting an instance of the [Location] class to an instance of the [Address] class using the system's built-in geocoder
 *
 * @return an instance of the [Address] class
 * @receiver an instance of the [Location] class to transform
 * @param context serves to call the system geocoder
 * @param locale it is possible to explicitly set [Locale] for geocoding results. The default is [Locale.getDefault]
 * @throws IOException in case of internal errors of the geocoder
 * @throws IllegalArgumentException if [Location.getLatitude] is not in range [-90..90]
 * @throws IllegalArgumentException if [Location.getLongitude] is not in range [-180..180]
 */
@WorkerThread
@Throws(IOException::class)
fun Location.toAddress(context: Context, locale: Locale = Locale.getDefault()): Address? = Geocoder(context, locale).getFromLocation(this.latitude, this.longitude, 1).firstOrNull()

/**
 * Calls the system geolocation settings activity
 *
 * @receiver an instance of the [Context] class to launch the system settings screen
 */
fun Context.openSettings() = startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
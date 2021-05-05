@file:Suppress("UNUSED")

package com.genius.cgps

import android.Manifest
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.annotation.IntDef
import androidx.annotation.IntRange
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeoutException

/**
 * A class that obtains user coordinates in various possible ways through internal GooglePlay services
 *
 * As a rule, it is preferable to work through it than with [CGPS],
 * because GooglePlay services cache the user's geolocation, reusing it and, thus,
 * reduce battery consumption and increase the speed of obtaining the user's geolocation
 *
 * However, their connection with services means that this class will not work on devices without
 * GooglePlay support and will throw [ServicesAvailabilityException] errors for all methods
 *
 * @property context is needed to check permissions and for the presence of GooglePlay services
 * @constructor receiving [Context] instance for working with Android geolocation service
 *
 * @author Viktor Likhanov
 */
class CGGPS(private val context: Context) {

    private val fusedLocation: FusedLocationProviderClient by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private val settings: SettingsClient by lazy { LocationServices.getSettingsClient(context) }
    private val location: LocationManager? by lazy { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }

    /**
     * Getting last known location of user by GooglePlay services
     * It can be cache from other applications during getting location to them
     *
     * @return [Location] location of user
     * @throws LocationException on undefined errors. In this case, the reason is written in [LocationException.message]
     * @throws ServicesAvailabilityException in case there are no GooglePlay services on the device
     * @throws LocationDisabledException in case of disabled GPS adapter
     * @throws SecurityException in case of missing permissions to get geolocation
     */
    @Throws(LocationException::class, LocationDisabledException::class, SecurityException::class, ServicesAvailabilityException::class)
    suspend fun lastLocation(): Location {
        val coroutine = CompletableDeferred<Location>()
        if (location == null) {
            coroutine.completeExceptionally(LocationException("Location manager not found"))
        } else if (!isGooglePlayServicesAvailable(context)) {
            coroutine.completeExceptionally(ServicesAvailabilityException())
        } else if (!isLocationEnabled(location)) {
            coroutine.completeExceptionally(LocationDisabledException())
        } else if (!checkPermission(context, true)) {
            coroutine.completeExceptionally(SecurityException("Permissions for GPS was not given"))
        } else {
            val location = fusedLocation.lastLocation.await()
            if (location == null) {
                coroutine.completeExceptionally(LocationException("Last location not found"))
            } else {
                coroutine.complete(location)
            }
        }

        return coroutine.await()
    }

    /**
     * Retrieves the user's current location from GooglePlay services
     *
     * For flexibility of the location request, you can specify [accuracy], [timeout]
     *
     * The steps of checking and possible exceptions are the same as the order of the error descriptions below.
     *
     * @param accuracy The accuracy of the obtained coordinates. Default value is [LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY]
     * @param timeout timeout for getting coordinates. The default is 5000 milliseconds
     * @return [Location] location of user
     * @throws LocationException on undefined errors. In this case, the reason is written in [LocationException.message]
     * @throws ServicesAvailabilityException in case there are no GooglePlay services on the device
     * @throws LocationDisabledException in case of disabled GPS adapter
     * @throws SecurityException in case of missing permissions to get geolocation
     * @throws TimeoutException in case the geolocation request timed out
     */
    @Throws(LocationException::class, LocationDisabledException::class, SecurityException::class, TimeoutException::class, ServicesAvailabilityException::class)
    suspend fun actualLocation(@Accuracy accuracy: Int = Accuracy.BALANCED,
                               @IntRange(from = 0) timeout: Long = 5_000L): Location {
        val coroutine = CompletableDeferred<Location>()
        if (location == null) {
            coroutine.completeExceptionally(LocationException("Location manager not found"))
        } else if (!isGooglePlayServicesAvailable(context)) {
            coroutine.completeExceptionally(ServicesAvailabilityException())
        } else if (!isLocationEnabled(location)) {
            coroutine.completeExceptionally(LocationDisabledException())
        } else if (!checkPermission(context, false)) {
            coroutine.completeExceptionally(SecurityException("Permissions for GPS was not given"))
        } else {
            val cancellationTokenSource = CancellationTokenSource()
            val locationTask = fusedLocation.getCurrentLocation(accuracy, cancellationTokenSource.token)

            try {
                withTimeout(timeout) {
                    val location = locationTask.await()
                    coroutine.complete(location)
                }
            } catch (e: TimeoutCancellationException) {
                if (coroutine.isActive) {
                    coroutine.completeExceptionally(TimeoutException("Location timeout on $timeout ms"))
                }
            } finally {
                cancellationTokenSource.cancel()
            }
        }

        return coroutine.await()
    }

    /**
     * Receives the user's current location from GooglePlay services.
     * If the GPS adapter is disabled on the device, then a request for enabling via creating an intent by GooglePlay services follows
     *
     * For full-fledged work, you need to create an instance of the [androidx.activity.result.ActivityResultLauncher] class,
     * which will receive an [IntentSender] instance if it is necessary to enable the adapter through Google services
     *
     * For flexibility of the location request, you can specify [accuracy], [timeout]
     *
     * @param accuracy The accuracy of the obtained coordinates. Default value is [LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY]
     * @param timeout timeout for getting coordinates. The default is 5000 milliseconds
     * @return [Location] location of user
     * @throws LocationException on undefined errors. In this case, the reason is written in [LocationException.message]
     * @throws SecurityException in case of missing permissions to get geolocation
     * @throws TimeoutException in case the geolocation request timed out
     * @throws ServicesAvailabilityException in case there are no GooglePlay services on the device
     * @throws ResolutionNeedException if confirmation of enabling the GPS adapter by the user is required
     */
    @Throws(LocationException::class, SecurityException::class, TimeoutException::class, ServicesAvailabilityException::class)
    suspend fun actualLocationWithEnable(@Accuracy accuracy: Int = Accuracy.BALANCED,
                                         @IntRange(from = 0) timeout: Long = 5_000L): Location {
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(createRequest(accuracy, timeout, timeout, 1))
            .build()

        try {
            settings.checkLocationSettings(settingsRequest).await()
        } catch (e: Exception) {
            when (val statusCode = (e as? ApiException)?.statusCode) {
                LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                    try {
                        val rae = e as ResolvableApiException
                        throw ResolutionNeedException(rae.resolution.intentSender)
                    } catch (sie: IntentSender.SendIntentException) {
                        throw LocationException("Cannot find activity for resolve GPS enable intent")
                    }
                }
                null -> throw e
                else -> throw LocationException("Undefined status code from the settings client: $statusCode")
            }
        }

        return actualLocation(accuracy, timeout)
    }

    /**
     * Creates a data stream in which, after a [interval] period, it makes requests to update the device's geolocation
     *
     * For flexibility of the location request, you can specify [accuracy], [timeout]
     *
     * At the end of the work on receiving coordinates, closes [SendChannel] and unsubscribes [LocationManager] from its listener
     *
     * @param accuracy The accuracy of the obtained coordinates. Default value is [LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY]
     * @param timeout timeout for getting coordinates. The default is 5000 milliseconds
     * @param interval time interval for getting coordinates. The default is 5000 milliseconds
     * @return [flow] channel of data with [Result] instances
     * @throws ServicesAvailabilityException in case there are no GooglePlay services on the device
     * @throws SecurityException in case of missing permissions to get geolocation
     */
    fun requestUpdates(@Accuracy accuracy: Int = Accuracy.BALANCED,
                       @IntRange(from = 0) timeout: Long = 5_000L,
                       @IntRange(from = 0) interval: Long = 5_000L) = flow {
        if (!isGooglePlayServicesAvailable(context)) {
            emit(Result.failure(ServicesAvailabilityException()))
            return@flow
        } else if (!checkPermission(context, false)) {
            emit(Result.failure(SecurityException("Permissions for GPS was not given")))
            return@flow
        }

        val updateChannel = Channel<Result<Location>>()

        val locationListener = CGPSCallback(null, updateChannel)

        val request = createRequest(accuracy, interval, timeout)

        fusedLocation.requestLocationUpdates(request, locationListener, Looper.getMainLooper())

        try {
            for (location in updateChannel) {
                emit(location)
            }
        } finally {
            fusedLocation.removeLocationUpdates(locationListener)
        }
    }

    private fun createRequest(@Accuracy accuracy: Int,
                              interval: Long,
                              timeout: Long,
                              updates: Int? = null): LocationRequest {
        return LocationRequest.create().apply {
            updates?.let { count -> this.numUpdates = count }
            this.interval = interval
            this.maxWaitTime = timeout
            this.priority = accuracy
        }
    }

    private class CGPSCallback(private val coroutine: CompletableDeferred<Location>?,
                               private val listener: Channel<Result<Location>>?) : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            locationResult?.locations?.getOrNull(0)?.let {
                coroutine?.complete(it)
                listener?.offer(Result.success(it))
            } ?: handleError()
        }

        override fun onLocationAvailability(locationStatus: LocationAvailability?) {
            if (locationStatus?.isLocationAvailable == false) {
                coroutine?.completeExceptionally(LocationException("Location are unavailable with those settings"))
                listener?.offer(Result.failure(LocationException("Location are unavailable with those settings")))
            }
        }

        fun handleError() {
            coroutine?.completeExceptionally(LocationException("Location not found"))
            listener?.offer(Result.failure(LocationException("Location not found")))
        }
    }

    @IntDef(
        Accuracy.HIGH,
        Accuracy.BALANCED,
        Accuracy.LOW,
        Accuracy.NO
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class Accuracy {
        companion object {
            const val HIGH = LocationRequest.PRIORITY_HIGH_ACCURACY
            const val BALANCED = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
            const val LOW = LocationRequest.PRIORITY_LOW_POWER
            const val NO = LocationRequest.PRIORITY_NO_POWER
        }
    }
}

fun isGooglePlayServicesAvailable(context: Context): Boolean {
    val googleApiAvailability = GoogleApiAvailability.getInstance()
    val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
    return resultCode == ConnectionResult.SUCCESS
}

/**
 * Internal function to check the issued appropriate permissions for the application
 *
 * Depending on the [isCoarse] flag, the presence of issued permissions is checked:
 * true - [Manifest.permission.ACCESS_COARSE_LOCATION] - find approximate geolocation
 * false - [Manifest.permission.ACCESS_FINE_LOCATION] - find the most accurate geolocation
 *
 * @param context serves to gain access to geolocation services
 * @param isCoarse serves to clarify verifiable permissions
 * @return Depending on the [isCoarse] flag, the existence of issued permissions is checked: granted (true) or not (false) whether permission for the passed arguments
 */
internal fun checkPermission(context: Context, isCoarse: Boolean) = if (isCoarse) {
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
} else {
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

internal fun isLocationEnabled(manager: LocationManager?) = manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false
    || manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false

/**
 * Thrown out in case of other reasons due to which it is impossible to get the user's geolocation
 * @constructor receives a non-null [message] reason for the error
 * @param message reason for error in text representation
 */
class LocationException(message: String) : Exception(message)

/**
 * Thrown out if the user has disabled the GPS adapter on the device
 */
class LocationDisabledException : Exception("Location adapter turned off on device")

/**
 * Thrown out if GooglePlay services are not available on the user's device
 */
class ServicesAvailabilityException : Exception("Google services is not available on this device")

/**
 * Thrown out if manual activation of the GPS adapter by the user is required using a dialog from GooglePlay services
 * @param intentSender GPS adapter enable call source
 */
class ResolutionNeedException(val intentSender: IntentSender) : Exception("Inclusion permission requested with intent sender")
@file:Suppress("UNUSED")

package com.genius.cgps

import android.Manifest
import android.content.Context
import android.content.IntentSender
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.annotation.IntDef
import androidx.annotation.IntRange
import androidx.core.content.ContextCompat
import com.huawei.hms.api.ConnectionResult
import com.huawei.hms.api.HuaweiApiAvailability
import com.huawei.hms.common.ApiException
import com.huawei.hms.common.ResolvableApiException
import com.huawei.hms.location.FusedLocationProviderClient
import com.huawei.hms.location.LocationAvailability
import com.huawei.hms.location.LocationCallback
import com.huawei.hms.location.LocationRequest
import com.huawei.hms.location.LocationResult
import com.huawei.hms.location.LocationServices
import com.huawei.hms.location.LocationSettingsRequest
import com.huawei.hms.location.LocationSettingsStatusCodes
import com.huawei.hms.location.SettingsClient
import com.huawei.hms.support.api.location.common.exception.LocationStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeoutException

/**
 * A class that obtains user coordinates in various possible ways through internal HMS
 *
 * As a rule, it is preferable to work through it than with [HardwareCGPS],
 * because GooglePlay services cache the user's geolocation, reusing it and, thus,
 * reduce battery consumption and increase the speed of obtaining the user's geolocation
 *
 * However, their connection with services means that this class will not work on devices without
 * GooglePlay support and will throw [ServicesAvailabilityException] errors for all methods
 *
 * @property context is needed to check permissions and for the presence of HMS
 * @constructor receiving [Context] instance for working with Android geolocation service
 *
 * @author Viktor Likhanov
 */
class HuaweiCGPS(private val context: Context) : CGPS {

    private val fusedLocation: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(
            context
        )
    }
    private val settings: SettingsClient by lazy { LocationServices.getSettingsClient(context) }
    private val location: LocationManager? by lazy { ContextCompat.getSystemService(context, LocationManager::class.java) }

    /**
     * Getting last known location of user by GooglePlay services
     * It can be cache from other applications during getting location to them
     *
     * Required permission [Manifest.permission.ACCESS_COARSE_LOCATION]
     *
     * @return [Location] location of user
     * @throws LocationException on undefined errors. In this case, the reason is written in [LocationException.message]
     * @throws ServicesAvailabilityException in case there are no HMS on the device
     * @throws LocationDisabledException in case of disabled GPS adapter
     * @throws SecurityException in case of missing permissions to get geolocation
     */
    @Throws(
        LocationException::class,
        LocationDisabledException::class,
        SecurityException::class,
        ServicesAvailabilityException::class
    )
    override suspend fun lastLocation(): Location {
        val coroutine = CompletableDeferred<Location>()
        if (location == null) {
            coroutine.completeExceptionally(
                LocationException(
                    LocationException.ErrorType.LOCATION_MANAGER,
                    "Location manager not found"
                )
            )
        } else if (!isHuaweiPlayServicesAvailable(context)) {
            coroutine.completeExceptionally(ServicesAvailabilityException("HMS"))
        } else if (!isLocationEnabled(location)) {
            coroutine.completeExceptionally(LocationDisabledException())
        } else if (!context.checkPermission(true)) {
            coroutine.completeExceptionally(SecurityException("Permissions for GPS was not given"))
        } else {
            val location = fusedLocation.lastLocation.await()
            if (location == null) {
                coroutine.completeExceptionally(
                    LocationException(
                        LocationException.ErrorType.LAST_LOCATION,
                        "Last location not found"
                    )
                )
            } else {
                coroutine.complete(location)
            }
        }

        return coroutine.await()
    }

    /**
     * Retrieves the user's current location from HMS
     *
     * For flexibility of the location request, you can specify [accuracy], [timeout]
     *
     * The steps of checking and possible exceptions are the same as the order of the error descriptions below.
     *
     * Required permission [Manifest.permission.ACCESS_FINE_LOCATION]
     *
     * @param accuracy The accuracy of the obtained coordinates. Default value is [LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY]
     * @param timeout timeout for getting coordinates. The default is 5000 milliseconds
     * @return [Location] location of user
     * @throws LocationException on undefined errors. In this case, the reason is written in [LocationException.message]
     * @throws ServicesAvailabilityException in case there are no HMS on the device
     * @throws LocationDisabledException in case of disabled GPS adapter
     * @throws SecurityException in case of missing fine permissions to get geolocation [Manifest.permission.ACCESS_FINE_LOCATION]
     * @throws TimeoutException in case the geolocation request timed out
     */
    @Throws(
        LocationException::class,
        LocationDisabledException::class,
        SecurityException::class,
        TimeoutException::class,
        ServicesAvailabilityException::class
    )
    override suspend fun actualLocation(
        @Accuracy accuracy: Int,
        @IntRange(from = 0) timeout: Long
    ): Location {
        val coroutine = CompletableDeferred<Location>()
        if (location == null) {
            coroutine.completeExceptionally(
                LocationException(
                    LocationException.ErrorType.LOCATION_MANAGER,
                    "Location manager not found"
                )
            )
        } else if (!isHuaweiPlayServicesAvailable(context)) {
            coroutine.completeExceptionally(ServicesAvailabilityException("HMS"))
        } else if (!isLocationEnabled(location)) {
            coroutine.completeExceptionally(LocationDisabledException())
        } else if (!context.checkPermission(false)) {
            coroutine.completeExceptionally(SecurityException("Permissions for GPS was not given"))
        } else {
            val requestCallback = CGPSCallback(coroutine = coroutine, listener = null)
            val request = createRequest(
                accuracy = accuracy,
                timeout = timeout,
                interval = timeout,
                updates = 1
            )
            val locationTask =
                fusedLocation.requestLocationUpdates(request, requestCallback, context.mainLooper)

            try {
                withTimeout(timeout) {
                    locationTask.await()
                }
            } catch (e: TimeoutCancellationException) {
                if (coroutine.isActive) {
                    coroutine.completeExceptionally(TimeoutException("Location timeout on $timeout ms"))
                }
            }
        }

        return coroutine.await()
    }

    /**
     * Receives the user's current location from HMS.
     * If the GPS adapter is disabled on the device, then a request for enabling via creating an intent by HMS follows
     *
     * For full-fledged work, you need to create an instance of the [androidx.activity.result.ActivityResultLauncher] class,
     * which will receive an [IntentSender] instance if it is necessary to enable the adapter through HMS
     *
     * For flexibility of the location request, you can specify [accuracy], [timeout]
     *
     * Required permission [Manifest.permission.ACCESS_FINE_LOCATION]
     *
     * @param accuracy The accuracy of the obtained coordinates. Default value is [LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY]
     * @param timeout timeout for getting coordinates. The default is 5000 milliseconds
     * @return [Location] location of user
     * @throws LocationException on undefined errors. In this case, the reason is written in [LocationException.message]
     * @throws SecurityException in case of missing permissions to get geolocation
     * @throws TimeoutException in case the geolocation request timed out
     * @throws ServicesAvailabilityException in case there are no HMS on the device
     * @throws ResolutionNeedException if confirmation of enabling the GPS adapter by the user is required
     */
    @Throws(
        LocationException::class,
        SecurityException::class,
        TimeoutException::class,
        ServicesAvailabilityException::class
    )
    override suspend fun actualLocationWithEnable(
        @Accuracy accuracy: Int,
        @IntRange(from = 0) timeout: Long
    ): Location {
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(createRequest(accuracy, timeout, timeout, 1))
            .build()

        try {
            settings.checkLocationSettings(settingsRequest).await()
        } catch (e: Exception) {
            when (val statusCode = (e as? ApiException)?.statusCode) {
                LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                    val rae = e as ResolvableApiException
                    throw ResolutionNeedException(rae.resolution.intentSender)
                }
                LocationStatusCode.AGC_CHECK_FAIL -> throw LocationException(
                    LocationException.ErrorType.CONNECT_VERIFICATION,
                    "AppGallery Connect identity verification failed. Verify that you have configured app information in AppGallery Connect."
                )
                null -> throw e
                else -> throw LocationException(
                    LocationException.ErrorType.OTHER,
                    "Undefined status code from the settings client: $statusCode"
                )
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
     * Required permission [Manifest.permission.ACCESS_FINE_LOCATION]
     *
     * @param accuracy The accuracy of the obtained coordinates. Default value is [LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY]
     * @param timeout timeout for getting coordinates. The default is 5000 milliseconds
     * @param interval time interval for getting coordinates. The default is 5000 milliseconds
     * @return [flow] channel of data with [Result] instances
     * @throws ServicesAvailabilityException in case there are no HMS on the device
     * @throws SecurityException in case of missing permissions to get fine geolocation [Manifest.permission.ACCESS_FINE_LOCATION]
     */
    override fun requestUpdates(
        @Accuracy accuracy: Int,
        @IntRange(from = 0) timeout: Long,
        @IntRange(from = 0) interval: Long
    ) = flow {
        if (!isHuaweiPlayServicesAvailable(context)) {
            emit(Result.failure(ServicesAvailabilityException("HMS")))
            return@flow
        } else if (!context.checkPermission(false)) {
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

    private fun createRequest(
        @Accuracy accuracy: Int,
        interval: Long,
        timeout: Long,
        updates: Int? = null
    ): LocationRequest {
        return LocationRequest.create().apply {
            updates?.let { count -> this.numUpdates = count }
            this.interval = interval
            this.maxWaitTime = timeout
            this.priority = accuracy
        }
    }

    private class CGPSCallback(
        private val coroutine: CompletableDeferred<Location>?,
        private val listener: Channel<Result<Location>>?
    ) : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.locations.firstOrNull()?.let {
                coroutine?.complete(it)
                listener?.trySend(Result.success(it))
            } ?: handleError()
        }

        override fun onLocationAvailability(locationStatus: LocationAvailability) {
            if (!locationStatus.isLocationAvailable) {
                coroutine?.completeExceptionally(
                    LocationException(
                        LocationException.ErrorType.FIDELITY,
                        "Location are unavailable with those settings"
                    )
                )
                listener?.trySend(
                    Result.failure(
                        LocationException(
                            LocationException.ErrorType.FIDELITY,
                            "Location are unavailable with those settings"
                        )
                    )
                )
            }
        }

        fun handleError() {
            coroutine?.completeExceptionally(
                LocationException(
                    LocationException.ErrorType.OTHER,
                    "Location not found"
                )
            )
            listener?.trySend(
                Result.failure(
                    LocationException(
                        LocationException.ErrorType.OTHER,
                        "Location not found"
                    )
                )
            )
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

fun isHuaweiPlayServicesAvailable(context: Context): Boolean {
    val huaweiApiAvailability = HuaweiApiAvailability.getInstance()
    val resultCode = huaweiApiAvailability.isHuaweiMobileServicesAvailable(context)
    return resultCode == ConnectionResult.SUCCESS
}
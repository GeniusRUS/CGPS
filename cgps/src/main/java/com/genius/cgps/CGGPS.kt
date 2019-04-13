@file:Suppress("UNUSED")

package com.genius.cgps

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.*
import android.support.annotation.IntDef
import android.support.annotation.IntRange
import android.support.v4.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CGGPS(private val context: Context) {

    private val manager = LocationServices.getFusedLocationProviderClient(context)
    private val settingsManager = LocationServices.getSettingsClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?

    @Throws(LocationException::class, LocationDisabledException::class, SecurityException::class, ServicesAvailabilityException::class)
    suspend fun lastLocation(): Location {
        val coroutine = CompletableDeferred<Location>()
        if (locationManager == null) {
            coroutine.completeExceptionally(LocationException("Location manager not found"))
        } else if (!isGooglePlayServicesAvailable(context)) {
            coroutine.completeExceptionally(ServicesAvailabilityException())
        } else if (!isLocationEnabled(locationManager)) {
            coroutine.completeExceptionally(LocationDisabledException())
        } else if (!checkPermission(context, true)) {
            coroutine.completeExceptionally(SecurityException("Permissions for GPS was not given"))
        } else {
            val location = manager.lastLocation.await()
            if (location == null) {
                coroutine.completeExceptionally(LocationException("Last location not found"))
            } else {
                coroutine.complete(location)
            }
        }

        return coroutine.await()
    }

    @Throws(LocationException::class, LocationDisabledException::class, SecurityException::class, TimeoutException::class, ServicesAvailabilityException::class)
    suspend fun actualLocation(@Accuracy accuracy: Int = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, @IntRange(from = 0) timeout: Long = 5000L): Location {
        val coroutine = CompletableDeferred<Location>()
        if (locationManager == null) {
            coroutine.completeExceptionally(LocationException("Location manager not found"))
        } else if (!isGooglePlayServicesAvailable(context)) {
            coroutine.completeExceptionally(ServicesAvailabilityException())
        } else if (!isLocationEnabled(locationManager)) {
            coroutine.completeExceptionally(LocationDisabledException())
        } else if (!checkPermission(context, false)) {
            coroutine.completeExceptionally(SecurityException("Permissions for GPS was not given"))
        } else {
            val listener = CGPSCallback(coroutine, null)

            requestLocationUpdates(listener, accuracy, timeout, 1)

            withContext(Dispatchers.Default) {
                delay(timeout)
                cancelWithTimeout(coroutine, listener, timeout)
            }
        }

        return coroutine.await()
    }

    @Throws(LocationException::class, LocationDisabledException::class, SecurityException::class, TimeoutException::class, ServicesAvailabilityException::class)
    suspend fun actualLocationWithEnable(@Accuracy accuracy: Int = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, requestCode: Int = 10414, @IntRange(from = 0) timeout: Long = 5000L): Location? {
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(createRequest(accuracy, timeout, 1))
            .build()

        try {
            settingsManager.checkLocationSettings(settingsRequest).await()
        } catch (e: Exception) {
            when (val statusCode = (e as? ApiException)?.statusCode) {
                LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                    if (context is Activity) {
                        try {
                            val rae = e as ResolvableApiException
                            rae.startResolutionForResult(context, requestCode)
                            throw ResolutionNeedException(requestCode)
                        } catch (sie: IntentSender.SendIntentException) {
                            throw LocationException("Cannot find activity for resolve GPS enable intent")
                        }
                    } else {
                        throw LocationException("Received context is not Activity. Please call this method with Activity instance")
                    }
                }
                null -> throw e
                else -> throw LocationException("Undefined status code from the settings client: $statusCode")
            }
        }

        return actualLocation(accuracy, timeout)
    }

    @Throws(ServicesAvailabilityException::class)
    fun requestUpdates(listener: SendChannel<Result<Location>>, @Accuracy accuracy: Int = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, @IntRange(from = 0) timeout: Long = 5000L) = Job().apply {
        if (!isGooglePlayServicesAvailable(context)) {
            throw ServicesAvailabilityException()
        } else if (!checkPermission(context, false)) {
            throw SecurityException("Permissions for GPS was not given")
        }

        val locationListener = CGPSCallback(null, listener)

        requestLocationUpdates(locationListener, accuracy, timeout)

        invokeOnCompletion {
            listener.close()
            manager?.removeLocationUpdates(locationListener)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(listener: LocationCallback, @Accuracy accuracy: Int, timeout: Long, updates: Int = Integer.MAX_VALUE) {
        val request = createRequest(accuracy, timeout, updates)

        manager.requestLocationUpdates(request, listener, context.mainLooper)
    }

    private fun createRequest(@Accuracy accuracy: Int, timeout: Long, updates: Int = Integer.MAX_VALUE): LocationRequest {
        return LocationRequest().apply {
            numUpdates = updates
            interval = timeout
            maxWaitTime = timeout
            fastestInterval = timeout / 10
            priority = accuracy
        }
    }

    private class CGPSCallback(private val coroutine: CompletableDeferred<Location>?, private val listener: SendChannel<Result<Location>>?) : LocationCallback() {
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

    private fun cancelWithTimeout(coroutine: CompletableDeferred<Location>, listener: LocationCallback, timeout: Long) {
        if (coroutine.isActive) {
            manager?.removeLocationUpdates(listener)
            coroutine.completeExceptionally(TimeoutException("Location timeout on $timeout ms"))
        }
    }

    @IntDef(
        LocationRequest.PRIORITY_HIGH_ACCURACY,
        LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY,
        LocationRequest.PRIORITY_LOW_POWER,
        LocationRequest.PRIORITY_NO_POWER
    )
    @Retention(AnnotationRetention.SOURCE)
    private annotation class Accuracy
}

suspend fun <T> Task<T>.await() = suspendCoroutine<T> { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}

fun isGooglePlayServicesAvailable(context: Context): Boolean {
    val googleApiAvailability = GoogleApiAvailability.getInstance()
    val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
    return resultCode == ConnectionResult.SUCCESS
}

internal fun checkPermission(context: Context, isCoarse: Boolean) = if (isCoarse) {
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
} else {
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

internal fun isLocationEnabled(manager: LocationManager?) = manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false
    || manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false

class LocationException(message: String): Exception(message)
class LocationDisabledException: Exception("Location adapter turned off on device")
class ServicesAvailabilityException: Exception("Google services is not available on this device")
class ResolutionNeedException(code: Int): Exception("Inclusion permission requested with request code: $code")
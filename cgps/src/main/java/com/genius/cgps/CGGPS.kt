package com.genius.cgps

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.*
import android.support.annotation.IntRange
import android.support.v4.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import java.util.concurrent.TimeoutException
import kotlin.coroutines.experimental.suspendCoroutine

class CGGPS(private val context: Context) {

    private val manager = LocationServices.getFusedLocationProviderClient(context)
    private val settingsManager = LocationServices.getSettingsClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?

    @Throws(LocationException::class, LocationDisabledException::class, SecurityException::class, ServicesAvailabilityException::class)
    suspend fun lastLocation(): Location {
        val coroutine = CompletableDeferred<Location>()
        if (locationManager == null) {
            coroutine.cancel(LocationException("Location manager not found"))
        } else if (!isGooglePlayServicesAvailable(context)) {
            coroutine.cancel(ServicesAvailabilityException())
        } else if (!isLocationEnabled(locationManager)) {
            coroutine.cancel(LocationDisabledException())
        } else if (!checkPermission(context, true)) {
            coroutine.cancel(SecurityException("Permissions for GPS was not given"))
        } else {
            val location = manager.lastLocation.await()
            if (location == null) {
                coroutine.cancel(LocationException("Last location not found"))
            } else {
                coroutine.complete(location)
            }
        }

        return coroutine.await()
    }

    @Throws(LocationException::class, LocationDisabledException::class, SecurityException::class, TimeoutException::class, ServicesAvailabilityException::class)
    suspend fun actualLocation(accuracy: Accuracy = Accuracy.BALANCED, @IntRange(from = 0) timeout: Long = 5000L): Location {
        val coroutine = CompletableDeferred<Location>()
        if (locationManager == null) {
            coroutine.cancel(LocationException("Location manager not found"))
        } else if (!isGooglePlayServicesAvailable(context)) {
            coroutine.cancel(ServicesAvailabilityException())
        } else if (!isLocationEnabled(locationManager)) {
            coroutine.cancel(LocationDisabledException())
        } else if (!checkPermission(context, false)) {
            coroutine.cancel(SecurityException("Permissions for GPS was not given"))
        } else {
            val listener = createLocationCallback(coroutine, null)

            requestLocationUpdates(listener, accuracy, timeout, 1)

            GlobalScope.launch {
                delay(timeout)
                cancelWithTimeout(coroutine, listener, timeout)
            }
        }

        return coroutine.await()
    }

    @Throws(LocationException::class, LocationDisabledException::class, SecurityException::class, TimeoutException::class, ServicesAvailabilityException::class)
    suspend fun actualLocationWithEnable(accuracy: Accuracy = Accuracy.BALANCED, requestCode: Int = 10414, @IntRange(from = 0) timeout: Long = 5000L): Location? {
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(createRequest(accuracy, timeout, 1))
            .build()

        try {
            settingsManager.checkLocationSettings(settingsRequest).await()
        } catch (e: Exception) {
            val statusCode = (e as? ApiException)?.statusCode
            when (statusCode) {
                LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                    if (context is Activity) {
                        try {
                            val rae = e as ResolvableApiException
                            rae.startResolutionForResult(context, requestCode)
                            return null
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
    fun requestUpdates(listener: SendChannel<Pair<Location?, Exception?>>, accuracy: Accuracy = Accuracy.BALANCED, @IntRange(from = 0) timeout: Long = 5000L) = Job().apply {
        if (!isGooglePlayServicesAvailable(context)) {
            throw ServicesAvailabilityException()
        } else if (!checkPermission(context, false)) {
            throw SecurityException("Permissions for GPS was not given")
        }

        val locationListener = createLocationCallback(null, listener)

        requestLocationUpdates(locationListener, accuracy, timeout)

        invokeOnCompletion {
            listener.close()
            manager?.removeLocationUpdates(locationListener)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(listener: LocationCallback, accuracy: Accuracy, timeout: Long, updates: Int = Integer.MAX_VALUE) {
        val request = createRequest(accuracy, timeout, updates)

        manager.requestLocationUpdates(request, listener, context.mainLooper)
    }

    private fun createRequest(accuracy: Accuracy, timeout: Long, updates: Int = Integer.MAX_VALUE): LocationRequest {
        return LocationRequest().apply {
            numUpdates = updates
            interval = timeout
            maxWaitTime = timeout
            fastestInterval = timeout / 10
            priority = accuracy.accuracy
        }
    }

    private fun createLocationCallback(coroutine: CompletableDeferred<Location>?, listener: SendChannel<Pair<Location?, Exception?>>?) = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            locationResult?.locations?.getOrNull(0)?.let {
                coroutine?.complete(it)
                listener?.offer(Pair(it, null))
            } ?: handleError()
        }

        override fun onLocationAvailability(locationStatus: LocationAvailability?) {
            if (locationStatus?.isLocationAvailable == false) {
                coroutine?.cancel(LocationException("Location are unavailable with those settings"))
                listener?.offer(Pair(null, LocationException("Location are unavailable with those settings")))
            }
        }

        fun handleError() {
            coroutine?.cancel(LocationException("Location not found"))
            listener?.offer(Pair(null, LocationException("Location not found")))
        }
    }

    private fun cancelWithTimeout(coroutine: CompletableDeferred<Location>, listener: LocationCallback, timeout: Long) {
        if (coroutine.isActive) {
            manager?.removeLocationUpdates(listener)
            coroutine.cancel(TimeoutException("Location timeout on $timeout ms"))
        }
    }

    enum class Accuracy(val accuracy: Int) {
        HIGH(LocationRequest.PRIORITY_HIGH_ACCURACY),
        BALANCED(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY),
        LOW_POWER(LocationRequest.PRIORITY_LOW_POWER),
        NO_POWER(LocationRequest.PRIORITY_NO_POWER),
    }
}

fun handleResult(requestCode: Int, resultCode: Int, data: Intent?, action: () -> Unit) {
    if (resultCode == Activity.RESULT_OK) {
        action.invoke()
    }
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
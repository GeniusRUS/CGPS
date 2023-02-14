package com.genius.cgps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.provider.Settings
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.Locale

fun isLocationEnabled(manager: LocationManager?) = manager?.isProviderEnabled(
    LocationManager.NETWORK_PROVIDER) ?: false
    || manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false

/**
 * Internal function to check the issued appropriate permissions for the application
 *
 * Depending on the [isCoarse] flag, the presence of issued permissions is checked:
 * true - [Manifest.permission.ACCESS_COARSE_LOCATION] - find approximate geolocation
 * false - [Manifest.permission.ACCESS_FINE_LOCATION] - find the most accurate geolocation
 *
 * @receiver [Context] instance serves to gain access to geolocation services
 * @param isCoarse serves to clarify verifiable permissions
 * @return Depending on the [isCoarse] flag, the existence of issued permissions is checked: granted (true) or not (false) whether permission for the passed arguments
 */
fun Context.checkPermission(isCoarse: Boolean) = if (isCoarse) {
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
} else {
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
@Suppress("DEPRECATION")
@WorkerThread
@Throws(IOException::class)
fun Location.toAddress(context: Context, locale: Locale = Locale.getDefault()): Address? =
    Geocoder(context, locale).getFromLocation(this.latitude, this.longitude, 1)?.firstOrNull()

/**
 * Calls the system geolocation settings activity
 *
 * @receiver an instance of the [Context] class to launch the system settings screen
 */
fun Context.openSettings() = startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
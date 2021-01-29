@file:Suppress("UNUSED")

package com.genius.cgps

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
 * Класс, осуществляющий получение координат пользователя различными возможными способами через
 * внутренние службы GooglePlay
 *
 * Как правило, работа именно через него предпочтительнее, чем с [CGPS], потому что сервисы GooglePlay
 * кэшируют геолокацию пользователя и, таким образом, сокращают потребление батареи и увеличивают скорость
 * получения геолокации пользователя
 *
 * Однако их связанность с сервисами означает, что на устройствах без поддержки GooglePlay данный класс
 * работать не будет и будет выдавать ошибки [ServicesAvailabilityException] для всех методов
 *
 * @property context необходима для проверки разрешений, наличия сервисов GooglePlay
 * @constructor принимает в себя экземпляр [Context] для работы с системной службой геолокации
 *
 * @author Виктор Лиханов
 */
class CGGPS(private val context: Context) {

    private val fusedLocation: FusedLocationProviderClient by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private val settings: SettingsClient by lazy { LocationServices.getSettingsClient(context) }
    private val location: LocationManager? by lazy { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }

    /**
     * Получает последнюю известную локацию пользователя из сервисов GooglePlay
     *
     * @return [Location] локация пользователя
     * @throws LocationException в случае неопределенных ошибок. В таком случае в [LocationException.message] пишется причина
     * @throws ServicesAvailabilityException в случае, если на устройстве отсутствуют сервисы GooglePlay
     * @throws LocationDisabledException в случае выключенного GPS-адаптера
     * @throws SecurityException в случае отсутствующих разрешений на получение геолокации
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
     * Получает актуальную локацию пользователя из сервисов GooglePlay
     *
     * Для гибкости запроса локации можно указать [accuracy], [timeout]
     *
     * Этапы проверки и возможные выбрасывания ошибок совпадают с очередностью описания ошибок ниже
     *
     * @param accuracy точность полученных координат. Значение по умолчанию [LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY]
     * @param timeout таймаут на получение координат. Значение по умолчанию 5000 миллисекунд
     * @return [Location] локация пользователя
     * @throws LocationException в случае неопределенных ошибок. В таком случае в [LocationException.message] пишется причина
     * @throws ServicesAvailabilityException в случае, если на устройстве отсутствуют сервисы GooglePlay
     * @throws LocationDisabledException в случае выключенного GPS-адаптера
     * @throws SecurityException в случае отсутствующих разрешений на получение геолокации
     * @throws TimeoutException в случае, если превышено время ожидания запроса геолокациии
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
     * Получает актуальную локацию пользователя из сервисов GooglePlay
     * В случае, если на устройстве отключен GPS-адаптер, то следует запрос на включение через создание интента в GooglePlay сервисах
     *
     * Для полноценной его работы требуется реализовать в [Activity.onActivityResult] метод проверки соответствия
     * запрошенного [requestCode] и результирующего кода, который в случае успешного включения адаптера, будет равен [Activity.RESULT_OK]
     *
     * Для гибкости запроса локации можно указать [accuracy], [timeout]
     *
     * @param accuracy точность полученных координат. Значение по умолчанию [LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY]
     * @param requestCode код запроса на получение результата включения [LocationManager]. Значение по умолчанию 10414
     * @param timeout таймаут на получение координат. Значение по умолчанию 5000 миллисекунд
     * @return [Location] локация пользователя
     * @throws LocationException в случае неопределенных ошибок. В таком случае в [LocationException.message] пишется причина
     * @throws SecurityException в случае отсутствующих разрешений на получение геолокации
     * @throws TimeoutException в случае, если превышено время ожидания запроса геолокациии
     * @throws ServicesAvailabilityException в случае, если на устройстве отсутствуют сервисы GooglePlay
     * @throws ResolutionNeedException в случае, если требуется подверждение включения GPS-адаптера пользователем
     */
    @Throws(LocationException::class, SecurityException::class, TimeoutException::class, ServicesAvailabilityException::class)
    suspend fun actualLocationWithEnable(@Accuracy accuracy: Int = Accuracy.BALANCED,
                                         requestCode: Int = 10414,
                                         @IntRange(from = 0) timeout: Long = 5_000L): Location {
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(createRequest(accuracy, timeout, timeout, 1))
            .build()

        try {
            settings.checkLocationSettings(settingsRequest).await()
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

    /**
     * Получает актуальную локацию пользователя из сервисов GooglePlay
     * В случае, если на устройстве отключен GPS-адаптер, то следует запрос на включение через запрос GooglePlay сервисов
     *
     * Для гибкости запроса локации можно указать [accuracy], [timeout]
     *
     * По окончанию работы по получению координат закрывает [SendChannel] и отписывает [LocationManager] от своего слушателя
     *
     * @param accuracy точность полученных координат. Значение по умолчанию [LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY]
     * @param timeout таймаут на получение координат. Значение по умолчанию 5000 миллисекунд
     * @param interval интервал времени для получения координат. Значение по умолчанию 5000 миллисекунд
     * @return [flow] поток с результатами [Result]
     * @throws ServicesAvailabilityException в случае, если на устройстве отсутствуют сервисы GooglePlay
     * @throws SecurityException в случае отсутствующих разрешений на получение геолокации
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

        requestLocationUpdates(locationListener, accuracy, interval, timeout)

        try {
            for (location in updateChannel) {
                emit(location)
            }
        } finally {
            fusedLocation.removeLocationUpdates(locationListener)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(listener: LocationCallback,
                                       @Accuracy accuracy: Int,
                                       interval: Long,
                                       timeout: Long,
                                       updates: Int? = null) {
        val request = createRequest(accuracy, interval, timeout, updates)

        fusedLocation.requestLocationUpdates(request, listener, Looper.getMainLooper())
    }

    private fun createRequest(@Accuracy accuracy: Int,
                              interval: Long,
                              timeout: Long,
                              updates: Int? = null): LocationRequest {
        return LocationRequest().apply {
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
 * Внутренняя функция для проверки выданных соответствующих разрешений приложению
 *
 * В зависимости от флага [isCoarse] проверяется наличие выданных разрешений:
 * true - [Manifest.permission.ACCESS_COARSE_LOCATION] - смотрится приблизительная геолокация
 * false - [Manifest.permission.ACCESS_FINE_LOCATION] - смотрится наиболее точная геолокация
 *
 * @param context служит для получения доступа к данным приложения
 * @param isCoarse служит для уточнения проверяемых разрешений
 * @return предоставлено (true) или нет (false) ли разрешение для переданных аргументов
 */
internal fun checkPermission(context: Context, isCoarse: Boolean) = if (isCoarse) {
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
} else {
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

internal fun isLocationEnabled(manager: LocationManager?) = manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false
    || manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false

/**
 * Выбрасывается в случае прочих причин, из-за которых невозможно получить геолокацию пользователя
 * @constructor принимает в себя non-null [message] причину ошибки
 * @param message - причина ошибки в текстовом представлении
 */
class LocationException(message: String) : Exception(message)

/**
 * Выбрасывается в случае, если у пользователя выключен GPS-адаптер на устройстве
 */
class LocationDisabledException : Exception("Location adapter turned off on device")

/**
 * Выбрасывается в случае, если у пользователя на устройстве не доступны GooglePlay сервисы
 */
class ServicesAvailabilityException : Exception("Google services is not available on this device")

/**
 * Выбрасывается в случае, если требуется ручное включение пользователем GPS-адаптера с помощью диалога от GooglePlay сервисов
 * @constructor принимает в себя non-null код запроса
 * @param code - код запроса на включение GPS-адаптера пользователем
 */
class ResolutionNeedException(code: Int) : Exception("Inclusion permission requested with request code: $code")
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

    /**
     * Получает последнюю известную локацию пользователя из GPS-адаптера устройства
     *
     * @return [Location] локация пользователя
     * @throws LocationException в случае неопределенных ошибок. В таком случае в [LocationException.message] пишется причина
     * @throws LocationDisabledException в случае выключенного GPS-адаптера
     * @throws SecurityException в случае отсутствующих разрешений на получение геолокации
     */
    @Throws(LocationException::class, LocationDisabledException::class, SecurityException::class)
    suspend fun lastLocation(accuracy: Accuracy = Accuracy.COARSE) = suspendCoroutine<Location> { coroutine ->
        if (manager == null) {
            coroutine.resumeWithException(LocationException("Location manager not found"))
        } else if (!isLocationEnabled(manager)) {
            coroutine.resumeWithException(LocationDisabledException())
        } else if (!checkPermission(context, true)) {
            coroutine.resumeWithException(SecurityException("Permissions for GPS was not given"))
        } else {
            val provider = manager.getBestProvider(accuracy.toCriteria(), true)
            if (provider == null) {
                coroutine.resumeWithException(LocationException("Provider not found for this accuracy: ${accuracy.accuracy} and power: ${accuracy.power}"))
            } else {
                val location = manager.getLastKnownLocation(provider)
                if (location == null) {
                    coroutine.resumeWithException(LocationException("Last location not found"))
                } else {
                    coroutine.resume(location)
                }
            }
        }
    }

    /**
     * Получает актуальную локацию пользователя из GPS-сервиса устройства
     *
     * Для гибкости запроса локации можно указать [accuracy], [timeout]
     *
     * Этапы проверки и возможные выбрасывания ошибок совпадают с очередностью описания ошибок ниже
     *
     * @param accuracy точность полученных координат. Значение по умолчанию [Accuracy.COARSE]
     * @param timeout таймаут на получение координат. Значение по умолчанию 5000 миллисекунд
     * @return [Location] локация пользователя
     * @throws LocationException в случае неопределенных ошибок. В таком случае в [LocationException.message] пишется причина
     * @throws LocationDisabledException в случае выключенного GPS-адаптера
     * @throws SecurityException в случае отсутствующих разрешений на получение геолокации
     * @throws TimeoutException в случае, если превышено время ожидания запроса геолокациии
     */
    @Throws(LocationException::class, LocationDisabledException::class, SecurityException::class, TimeoutException::class)
    suspend fun actualLocation(accuracy: Accuracy = Accuracy.COARSE,
                               @IntRange(from = 0) timeout: Long = 5000L): Location {
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

    /**
     * Получает актуальную локацию пользователя из GPS-сервиса устройства
     *
     * Для гибкости запроса локации можно указать [accuracy], [timeout]
     *
     * Так как возвращается экземпляр [Job], то существует механизм, которым можно управлять жизненным циклом этого объекта
     *
     * По окончанию работы по получению координат закрывает [SendChannel] и отписывает [LocationManager] от своего слушателя
     *
     * @param listener слушатель в виде [SendChannel] для получения потока полученных координат
     * @param accuracy точность полученных координат. Значение по умолчанию [Accuracy.COARSE]
     * @param timeout таймаут на получение координат. Значение по умолчанию 5000 миллисекунд
     * @param interval интервал времени для получения координат. Значение по умолчанию 10000 миллисекунд
     * @return [Job] работа по цикличному получению координат
     * @throws ServicesAvailabilityException в случае, если на устройстве отсутствуют сервисы GooglePlay
     * @throws SecurityException в случае отсутствующих разрешений на получение геолокации
     */
    fun requestUpdates(listener: SendChannel<Result<Location>>,
                       context: CoroutineContext = Dispatchers.Main,
                       accuracy: Accuracy = Accuracy.COARSE,
                       @IntRange(from = 0) timeout: Long = 5000L,
                       @IntRange(from = 0) interval: Long = 10000L) = Job().apply {
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

/**
 * Преобразование экземпляра класса [Location] в экземпляр класса [Address] с помощью встроенного геокодера системы
 *
 * @return экземпляр класса [Address]
 * @receiver экземпляр класса [Location] для преобразования
 * @param context служит для вызова системного геокодера
 * @param locale существует возможность явно задать [Locale] для результатов геокодинга. По умолчанию значение [Locale.getDefault]
 * @throws IOException в случае внутренних ошибок геокодера
 * @throws IllegalArgumentException если [Location.getLatitude] меньше -90 или больше 90
 * @throws IllegalArgumentException если [Location.getLongitude] меньше -180 или больше 180
 */
@Throws(IOException::class)
fun Location.toAddress(context: Context, locale: Locale = Locale.getDefault()): Address? = Geocoder(context, locale).getFromLocation(this.latitude, this.longitude, 1).firstOrNull()

/**
 * Вызывает окно настроек геолокации системы
 *
 * @receiver экземпляр класса [Location] для запуска экрана настроек системы
 */
fun Context.openSettings() = startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
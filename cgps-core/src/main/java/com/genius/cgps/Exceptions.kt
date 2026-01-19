package com.genius.cgps

import android.content.IntentSender
import androidx.annotation.IntDef

/**
 * Thrown out in case of other reasons due to which it is impossible to get the user's geolocation
 * @constructor receives a non-null [message] reason for the error with [type] for detailed code
 * @param type detailed type of exception
 * @param message reason for error in text representation
 */
class LocationException(@param:ErrorType val type: Int, message: String) : Exception(message) {

    @IntDef(
        ErrorType.LOCATION_MANAGER,
        ErrorType.FIDELITY,
        ErrorType.LAST_LOCATION,
        ErrorType.CONNECT_VERIFICATION,
        ErrorType.OTHER,
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class ErrorType {
        companion object {
            const val LOCATION_MANAGER = 201
            const val FIDELITY = 202
            const val LAST_LOCATION = 203
            const val CONNECT_VERIFICATION = 204
            const val OTHER = 209
        }
    }
}

/**
 * Thrown out if the user has disabled the GPS adapter on the device
 */
class LocationDisabledException : Exception("Location adapter turned off on device")

/**
 * Thrown out if GooglePlay/HMS services are not available on the user's device
 */
class ServicesAvailabilityException(platform: String) : Exception("$platform services is not available on this device")

/**
 * Thrown out if manual activation of the GPS adapter by the user is required using a dialog from GooglePlay/HMS
 * @param intentSender GPS adapter enable call source
 */
class ResolutionNeedException(val intentSender: IntentSender) : Exception("Inclusion permission requested with intent sender")
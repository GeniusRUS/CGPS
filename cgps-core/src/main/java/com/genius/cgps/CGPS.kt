package com.genius.cgps

import android.location.Location
import androidx.annotation.IntRange
import kotlinx.coroutines.flow.Flow

interface CGPS {
    suspend fun lastLocation(): Location
    suspend fun actualLocation(
        @Accuracy accuracy: Int = Accuracy.BALANCED,
        @IntRange(from = 0) timeout: Long = 5_000L
    ): Location
    suspend fun actualLocationWithEnable(
        @Accuracy accuracy: Int = Accuracy.BALANCED,
        @IntRange(from = 0) timeout: Long = 5_000L
    ): Location
    fun requestUpdates(
        @Accuracy accuracy: Int = Accuracy.BALANCED,
        @IntRange(from = 0) timeout: Long = 10_000L,
        @IntRange(from = 0) interval: Long = 5_000L
    ): Flow<Result<Location>>
}
package com.genius.cgps

import androidx.annotation.IntDef

@IntDef(
    Accuracy.HIGH,
    Accuracy.BALANCED,
    Accuracy.LOW,
    Accuracy.NO
)
@Retention(AnnotationRetention.SOURCE)
annotation class Accuracy {
    companion object {
        const val HIGH = 100
        const val BALANCED = 102
        const val LOW = 104
        const val NO = 105
    }
}
package com.genius.cgps

import com.huawei.hmf.tasks.CancellationTokenSource
import com.huawei.hmf.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Task<T>.await(): T = awaitImpl(null)

/**
 * @author [kotlin coroutines library](org.jetbrains.kotlinx:kotlinx-coroutines-play-services)
 */
private suspend fun <T> Task<T>.awaitImpl(cancellationTokenSource: CancellationTokenSource?): T {
    // fast path
    if (isComplete) {
        val e = exception
        return if (e == null) {
            if (isCanceled) {
                throw CancellationException("Task $this was cancelled normally.")
            } else {
                result as T
            }
        } else {
            throw e
        }
    }

    return suspendCancellableCoroutine { cont ->
        // Run the callback directly to avoid unnecessarily scheduling on the main thread.
        addOnCompleteListener(DirectExecutor) {
            val e = it.exception
            if (e == null) {
                if (it.isCanceled) cont.cancel() else cont.resume(it.result as T)
            } else {
                cont.resumeWithException(e)
            }
        }

        if (cancellationTokenSource != null) {
            cont.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
        }
    }
}

private object DirectExecutor : Executor {
    override fun execute(r: Runnable) {
        r.run()
    }
}
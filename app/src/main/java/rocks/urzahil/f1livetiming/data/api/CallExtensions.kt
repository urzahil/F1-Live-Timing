package rocks.urzahil.f1livetiming.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs this call without blocking a thread. Cancelling the calling coroutine while it waits for the response
 * cancels the HTTP call. Once the response has arrived the call is no longer cancelled by the coroutine, so a
 * caller that reads a large body should do it with runInterruptible (or cancel the call itself).
 */
suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            // The continuation can be cancelled while the response is being delivered; do not leak it.
            continuation.resume(response) { _, value, _ -> value.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }
    })
}

/** Preserves structured cancellation instead of converting it into a failed [Result]. */
suspend fun <T> cancellableResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

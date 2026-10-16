package com.example.data.api

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object NetworkClient {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private const val TAG = "F1Network"

    // Keep request/URL logging out of production builds. Failures are logged separately with
    // a sanitized endpoint label so credentials and query parameters are never emitted.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (com.example.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
        else HttpLoggingInterceptor.Level.NONE
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    // Public feed for full season standings and calendar
    private val jolpicaRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.jolpi.ca/ergast/f1/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val jolpicaService: F1JolpicaApiService =
        jolpicaRetrofit.create(F1JolpicaApiService::class.java)

    private val liveServices = ConcurrentHashMap<String, F1LiveApiService>()

    /** Normalize equivalent URLs so each configured backend reuses one Retrofit service. */
    internal fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().let { if (it.contains("://")) it else "http://$it" }
        val parsed = trimmed.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid backend URL")
        val path = parsed.encodedPath.trimEnd('/') + "/"
        return parsed.newBuilder()
            .scheme(parsed.scheme.lowercase())
            .host(parsed.host.lowercase())
            .encodedPath(path)
            .query(null)
            .fragment(null)
            .build()
            .toString()
    }

    fun createLiveService(baseUrl: String): F1LiveApiService {
        val normalized = normalizeBaseUrl(baseUrl)
        return liveServices.getOrPut(normalized) {
            Retrofit.Builder()
                .baseUrl(normalized)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(F1LiveApiService::class.java)
        }
    }

    /**
     * Quick latency ping for connection testing
     */
    suspend fun pingBackend(baseUrl: String): PingResult {
        val startTime = System.currentTimeMillis()
        return try {
            val service = createLiveService(baseUrl)
            val status = service.getStatus()
            val latency = System.currentTimeMillis() - startTime
            PingResult(
                isSuccess = true,
                latencyMs = latency,
                message = "Connected: ${status.status ?: "Online"} (${status.message ?: "Ready"})",
                statusCode = 200
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.w(TAG, "Backend ping failed: ${e.javaClass.simpleName}")
            PingResult(
                isSuccess = false,
                latencyMs = latency,
                message = "Unable to connect to the backend. Check the address and try again.",
                statusCode = -1
            )
        }
    }
}

data class PingResult(
    val isSuccess: Boolean,
    val latencyMs: Long,
    val message: String,
    val statusCode: Int
)

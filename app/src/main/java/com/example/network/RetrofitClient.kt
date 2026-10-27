package com.example.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Koneksi HTTP ke backend. Dibuat sekali saat aplikasi berjalan.
 * Untuk mengganti alamat server, ubah ApiConfig.BASE_URL.
 */
object RetrofitClient {

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        // Log request/response di Logcat (filter: "okhttp")
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    val api: ScanApi = Retrofit.Builder()
        .baseUrl(ApiConfig.API_URL)
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ScanApi::class.java)

    /** Ambil pesan "message" dari body error backend, mis. "QR sudah pernah di-input". */
    fun errorMessage(resp: Response<*>): String {
        val raw = runCatching { resp.errorBody()?.string() }.getOrNull()
        val msg = raw?.let { runCatching { moshi.adapter(ErrorBody::class.java).fromJson(it)?.message }.getOrNull() }
        return msg ?: "Server error ${resp.code()}"
    }
}

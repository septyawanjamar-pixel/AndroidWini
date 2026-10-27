package com.example.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Daftar endpoint backend (base: ApiConfig.API_URL = http://IP:5111/api/).
 * Semua route ada di C:\QR_Scanner_DB\server.js.
 */
interface ScanApi {

    /** Isi tabel Area (DBeaver) */
    @GET("areas")
    suspend fun areas(): Response<AreaListResponse>

    /** Isi tabel Grade (DBeaver) */
    @GET("grades")
    suspend fun grades(): Response<GradeListResponse>

    /** Isi tabel Type (type wire, DBeaver) */
    @GET("types")
    suspend fun types(): Response<TypeListResponse>

    /** Isi tabel POSISI untuk 1 area (A-line: 1-16, V9: 1-8) */
    @GET("posisi")
    suspend fun posisi(@Query("area") area: String): Response<PosisiListResponse>

    /** Isi tabel Alasan (alasan output) */
    @GET("alasan")
    suspend fun alasan(): Response<AlasanListResponse>

    /** Isi tabel Tidak_sesuai (alasan tidak sesuai) */
    @GET("tidak_sesuai")
    suspend fun tidakSesuai(): Response<TidakSesuaiListResponse>

    /** Simpan INPUT. 201 = OK, 400 = data salah, 409 = QR sudah di-input */
    @POST("scan/input")
    suspend fun input(@Body body: InputRequest): Response<ScanResponse>

    /** Simpan OUTPUT. 404 = belum di-input, 409 = sudah di-output */
    @POST("scan/output")
    suspend fun output(@Body body: OutputRequest): Response<ScanResponse>

    /** Detail 1 QR. 404 = tidak ada */
    @GET("scan/{qr}")
    suspend fun detail(@Path("qr") qr: String): Response<ScanResponse>

    /** Daftar data scan (terbaru dulu) */
    @GET("scan")
    suspend fun list(
        @Query("area") area: String? = null,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("sort") sort: String? = null,
    ): Response<ScanListResponse>
}

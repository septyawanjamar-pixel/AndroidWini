package com.example.network

/** Hasil cek 1 QR ke server. */
sealed interface LookupResult {
    data class Found(val row: ScanRow) : LookupResult
    data object NotFound : LookupResult
    data class Failed(val message: String) : LookupResult
}

/**
 * Semua panggilan ke backend ada di sini (ViewModel tidak memanggil API langsung).
 * Setiap fungsi mengembalikan Result: sukses berisi data, gagal berisi pesan dari server.
 */
class ScanRepository(private val api: ScanApi = RetrofitClient.api) {

    /* ------------------------------------------------------------ master */

    suspend fun areas(): Result<List<AreaDto>> = call { api.areas() }.map { it.data.orEmpty() }

    suspend fun grades(): Result<List<GradeDto>> = call { api.grades() }.map { it.data.orEmpty() }

    suspend fun types(): Result<List<TypeDto>> = call { api.types() }.map { it.data.orEmpty() }

    suspend fun alasan(): Result<List<AlasanDto>> = call { api.alasan() }.map { it.data.orEmpty() }

    suspend fun tidakSesuai(): Result<List<TidakSesuaiDto>> = call { api.tidakSesuai() }.map { it.data.orEmpty() }

    /** Posisi wire untuk 1 area dari tabel POSISI, urut nomor. */
    suspend fun posisi(area: String): Result<List<PosisiDto>> =
        call { api.posisi(area) }.map { r -> r.data.orEmpty().sortedBy { it.nomor } }

    /* ------------------------------------------------------------ input / output */

    suspend fun input(qrId: String, area: String, grade: String, typeWire: String, posisiWire: Int): Result<ScanRow?> =
        call { api.input(InputRequest(qrId, area, grade, typeWire, posisiWire)) }.map { it.data }

    suspend fun output(qrId: String, alasanId: Int?, tidakSesuaiId: Int? = null): Result<ScanRow?> =
        call { api.output(OutputRequest(qrId, alasanId, tidakSesuaiId)) }.map { it.data }

    suspend fun lookup(qrId: String): LookupResult = try {
        val resp = api.detail(qrId)
        val row = resp.body()?.data
        when {
            resp.isSuccessful && row != null -> LookupResult.Found(row)
            resp.code() == 404 -> LookupResult.NotFound
            else -> LookupResult.Failed(RetrofitClient.errorMessage(resp))
        }
    } catch (e: Exception) {
        LookupResult.Failed("Server tidak terjangkau: ${e.message}")
    }

    /* ------------------------------------------------------------ papan Output */

    suspend fun list(area: String?, limit: Int = 100, sort: String? = null, status: String? = null): Result<List<ScanRow>> =
        call { api.list(area = area, status = status, limit = limit, sort = sort) }.map { it.data.orEmpty() }

    /** Jumlah QR berstatus IN (menunggu output). */
    suspend fun pendingCount(area: String?): Result<Int> =
        call { api.list(area = area, status = "IN", limit = 1) }.map { it.total ?: 0 }

    /* ------------------------------------------------------------ helper */

    private inline fun <T> call(block: () -> retrofit2.Response<T>): Result<T> = try {
        val resp = block()
        val body = resp.body()
        if (resp.isSuccessful && body != null) Result.success(body)
        else Result.failure(Exception(RetrofitClient.errorMessage(resp)))
    } catch (e: Exception) {
        Result.failure(Exception("Server tidak terjangkau (${ApiConfig.BASE_URL}): ${e.message}"))
    }
}

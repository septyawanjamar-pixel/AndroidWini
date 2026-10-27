package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/* =============================================================================
   Bentuk data JSON — sama persis dengan backend C:\QR_Scanner_DB\server.js
   dan database C:/sqlite/aWan_data.db (tabel Area, Grade, scan_data_new).
   ============================================================================= */

/* ------------------------------------------------------------ MASTER DATA */

/** Satu area dari tabel Area. GET /api/areas -> kode_area & nama_area = kolom lokasi (mis. "Area CUROD") */
@JsonClass(generateAdapter = true)
data class AreaDto(
    @Json(name = "kode_area") val kodeArea: String,
    @Json(name = "nama_area") val namaArea: String,
    @Json(name = "aktif") val aktif: Int? = 1,
    @Json(name = "lead_time_hari") val leadTimeHari: Int? = null, // batas lead time area (tabel Batas_Lead_Time)
)

@JsonClass(generateAdapter = true)
data class AreaListResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: List<AreaDto>? = null,
)

/** Satu grade dari tabel Grade. GET /api/grades -> keterangan = "Grade A" */
@JsonClass(generateAdapter = true)
data class GradeDto(
    @Json(name = "grade") val grade: String,
    @Json(name = "keterangan") val keterangan: String? = null,
)

@JsonClass(generateAdapter = true)
data class GradeListResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: List<GradeDto>? = null,
)

/** Satu type wire dari tabel Type. GET /api/types -> { id, type_wire } mis. "NORMAL" */
@JsonClass(generateAdapter = true)
data class TypeDto(
    @Json(name = "id") val id: Int,
    @Json(name = "type_wire") val typeWire: String,
)

@JsonClass(generateAdapter = true)
data class TypeListResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: List<TypeDto>? = null,
)

/**
 * Satu posisi wire dari tabel POSISI + isinya. GET /api/posisi?area=A-line
 * Aturan: 1 posisi = 1 gulungan. terisi = ada lot IN di posisi ini.
 * urutan/ambil = urutan FIFO lot itu di area + grade-nya (1 / ambil=true = harus diambil duluan).
 */
@JsonClass(generateAdapter = true)
data class PosisiDto(
    @Json(name = "id") val id: Int,
    @Json(name = "line") val line: String,
    @Json(name = "nomor") val nomor: Int,
    @Json(name = "terisi") val terisi: Boolean = false,
    @Json(name = "dobel") val dobel: Boolean = false,
    @Json(name = "qr_id") val qrId: String? = null,
    @Json(name = "grade") val grade: String? = null,
    @Json(name = "tanggal_input") val tanggalInput: String? = null,
    @Json(name = "stagnasi") val stagnasi: Int? = null,
    @Json(name = "status_lead") val statusLead: String? = null, // "aman" | "hampir" | "lewat"
    @Json(name = "urutan") val urutan: Int? = null,
    @Json(name = "ambil") val ambil: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class PosisiListResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: List<PosisiDto>? = null,
)

/** Satu alasan output dari tabel Alasan. GET /api/alasan -> { id, alasan } mis. "MC_Trouble" */
@JsonClass(generateAdapter = true)
data class AlasanDto(
    @Json(name = "id") val id: Int,
    @Json(name = "alasan") val alasan: String,
)

@JsonClass(generateAdapter = true)
data class AlasanListResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: List<AlasanDto>? = null,
)

/** Satu alasan tidak sesuai dari tabel Tidak_sesuai. GET /api/tidak_sesuai -> mis. "Karena_Grade" */
@JsonClass(generateAdapter = true)
data class TidakSesuaiDto(
    @Json(name = "id") val id: Int,
    @Json(name = "alasan_tidaksesuai") val alasan: String,
)

@JsonClass(generateAdapter = true)
data class TidakSesuaiListResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: List<TidakSesuaiDto>? = null,
)

/* ------------------------------------------------------------ DATA SCAN */

/** Satu data scan (scan_data_new + nama Area & Grade). Kolom lain dari server diabaikan. */
@JsonClass(generateAdapter = true)
data class ScanRow(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "qr_id") val qrId: String,
    @Json(name = "area") val area: String? = null,
    @Json(name = "nama_area") val namaArea: String? = null,
    @Json(name = "grade") val grade: String? = null,
    @Json(name = "type_wire") val typeWire: String? = null,          // tabel Type
    @Json(name = "posisi_wire") val posisiWire: Int? = null,
    @Json(name = "status") val status: String? = null,              // "IN" | "OUT"
    @Json(name = "tanggal_input") val tanggalInput: String? = null,  // "2026-10-15 14:20:01"
    @Json(name = "tanggal_output") val tanggalOutput: String? = null,
    @Json(name = "alasan") val alasan: String? = null,                // alasan output (tabel Alasan)
    @Json(name = "tidak_sesuai") val tidakSesuai: String? = null,     // alasan tidak sesuai (tabel Tidak_sesuai)
    @Json(name = "tanggal_produksi") val tanggalProduksi: String? = null,
    @Json(name = "lama_stagnasi_hari") val lamaStagnasiHari: Int? = null, // hari sejak input (per tanggal)
    @Json(name = "lead_time_hari") val leadTimeHari: Int? = null,         // lead time area lot ini (tabel Batas_Lead_Time)
    @Json(name = "lewat_lead_time") val lewatLeadTime: String? = null,    // "YA" | "TIDAK" (stagnasi > lead time)
    @Json(name = "batas_lead_time") val batasLeadTime: String? = null,    // tanggal_input + lead_time_hari
    @Json(name = "tanggal_leadtime") val tanggalLeadtime: String? = null, // kolom tanggal_leadtime di scan_data_new
    @Json(name = "fifo") val fifo: FifoInfo? = null,                      // hanya di GET /api/scan/{qr}
) {
    val lewatLeadTimeYa: Boolean get() = lewatLeadTime == "YA"
}

/** Urutan FIFO 1 lot di area + grade-nya. seharusnya = lot yang harus diambil dulu (null = lot ini sudah benar). */
@JsonClass(generateAdapter = true)
data class FifoInfo(
    @Json(name = "urutan") val urutan: Int? = null,
    @Json(name = "jumlah_antri") val jumlahAntri: Int? = null,
    @Json(name = "seharusnya") val seharusnya: FifoTarget? = null,
)

@JsonClass(generateAdapter = true)
data class FifoTarget(
    @Json(name = "qr_id") val qrId: String,
    @Json(name = "posisi_wire") val posisiWire: Int? = null,
    @Json(name = "tanggal_produksi") val tanggalProduksi: String? = null,
    @Json(name = "stagnasi") val stagnasi: Int? = null,
)

/** Balasan 1 data: POST /api/scan/input, POST /api/scan/output, GET /api/scan/{qr} */
@JsonClass(generateAdapter = true)
data class ScanResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: ScanRow? = null,
)

/** Balasan daftar: GET /api/scan?area=&status=&limit= */
@JsonClass(generateAdapter = true)
data class ScanListResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: List<ScanRow>? = null,
    @Json(name = "total") val total: Int? = null,
)

/* ------------------------------------------------------------ REQUEST */

/** Body POST /api/scan/input */
@JsonClass(generateAdapter = true)
data class InputRequest(
    @Json(name = "qr_id") val qrId: String,
    @Json(name = "area") val area: String,          // lokasi dari tabel Area, mis. "Area CUROD"
    @Json(name = "grade") val grade: String,        // kode grade, mis. "A"
    @Json(name = "type_wire") val typeWire: String, // type wire dari tabel Type, mis. "NORMAL"
    @Json(name = "posisi_wire") val posisiWire: Int,
)

/** Body POST /api/scan/output (alasan wajib hanya kalau lewat lead time) */
@JsonClass(generateAdapter = true)
data class OutputRequest(
    @Json(name = "qr_id") val qrId: String,
    @Json(name = "alasan") val alasan: Int? = null, // id dari tabel Alasan
    @Json(name = "tidak_sesuai") val tidakSesuai: Int? = null, // id dari tabel Tidak_sesuai (wajib kalau posisi tidak sesuai FIFO)
)

/** Bentuk error umum backend: { success:false, message:"..." } */
@JsonClass(generateAdapter = true)
data class ErrorBody(
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "message") val message: String? = null,
)

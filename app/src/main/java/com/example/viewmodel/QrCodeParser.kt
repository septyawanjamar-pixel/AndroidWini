package com.example.viewmodel

import java.util.Calendar

/**
 * Aturan format QR — SAMA dengan backend (C:\QR_Scanner_DB\server.js, fungsi normalizeQr).
 *
 * Label di produk  : MMM-BB-TT-NNNN        contoh 100-10-11-0369
 *                    (no mesin - bulan - tanggal - no produksi)
 * Format lengkap   : YYYY-MMM-BB-TT-NNNN   contoh 2026-100-10-11-0369 (tahun ditambahkan otomatis)
 *
 * Teks lain di sekitar kode diabaikan. Bulan harus 1–12, tanggal 1–31.
 * Hasil: qr_id format lengkap, atau null bila tidak valid.
 */
object QrCodeParser {
    // No mesin 3 karakter huruf/angka, mis. "100" atau "A03"
    private val FULL = Regex("(\\d{4})-([0-9A-Za-z]{3})-(\\d{2})-(\\d{2})-(\\d{4})")
    private val LABEL = Regex("([0-9A-Za-z]{3})-(\\d{2})-(\\d{2})-(\\d{4})")

    fun parse(raw: String): String? {
        val qrId = FULL.find(raw)?.value?.uppercase()
            ?: LABEL.find(raw)?.value?.uppercase()?.let { "${Calendar.getInstance().get(Calendar.YEAR)}-$it" }
            ?: return null

        val parts = qrId.split("-")
        val bulan = parts[2].toInt()
        val tanggal = parts[3].toInt()
        if (bulan !in 1..12 || tanggal !in 1..31) return null
        return qrId
    }
}

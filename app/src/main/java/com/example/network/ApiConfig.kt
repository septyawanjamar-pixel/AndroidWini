package com.example.network

/**
 * Satu tempat untuk mengubah alamat server backend (C:\QR_Scanner_DB\server.js, port 5111).
 * - Emulator Android Studio : "http://10.0.2.2:5111/"  (10.0.2.2 = localhost PC dari dalam emulator)
 * - HP asli (WiFi yang sama): IP laptop, mis. "http://192.168.1.10:5111/" (cek dengan `ipconfig`)
 */
object ApiConfig {
    const val BASE_URL = "http://172.17.44.106:5111/"
    // const val BASE_URL = "http://192.17.102.133:5111/"   // contoh untuk HP asli

    /** Semua route backend ada di bawah /api/ */
    const val API_URL = BASE_URL + "api/"
}

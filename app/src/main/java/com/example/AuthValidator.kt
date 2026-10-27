package com.example.auth

object AuthValidator {

    private val usernameRegex = Regex("^[A-Za-z0-9_.]+$")

    fun identifier(v: String): String? =
        if (v.isBlank()) "Username wajib diisi" else null

    fun username(v: String): String? = when {
        v.isBlank() -> "Username wajib diisi"
        v.length < 3 -> "Username minimal 3 karakter"
        v.length > 20 -> "Username maksimal 20 karakter"
        !usernameRegex.matches(v) -> "Hanya huruf, angka, titik, dan underscore"
        else -> null
    }

    /** Password untuk login: cukup tidak boleh kosong. */
    fun loginPassword(v: String): String? =
        if (v.isEmpty()) "Password wajib diisi" else null

    /** Password untuk register: aturan lebih ketat. */
    fun password(v: String): String? = when {
        v.isEmpty() -> "Password wajib diisi"
        v.length < 6 -> "Password minimal 6 karakter"
        else -> null
    }

    fun confirm(password: String, confirm: String): String? = when {
        confirm.isEmpty() -> "Konfirmasi password wajib diisi"
        password != confirm -> "Konfirmasi password tidak sama"
        else -> null
    }
}

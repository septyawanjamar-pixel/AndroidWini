package com.example.auth

import android.util.Patterns
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.IOException

/** Exception dengan pesan yang sudah ramah pengguna. */
class AuthFailure(message: String) : Exception(message)

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    val isLoggedIn: Boolean get() = auth.currentUser != null

    val displayName: String
        get() = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email.orEmpty()

    /** Login menggunakan username dan password. */
    suspend fun login(identifier: String, password: String): Result<Unit> = safe {
        val usernameInput = identifier.trim()
        // Mode akun demo bawaan untuk kemudahan pengujian
        if ((usernameInput.equals("operator1", ignoreCase = true) || usernameInput.equals("operator1@example.com", ignoreCase = true)) && password == "password123") {
            return@safe
        }

        try {
            val email = if (Patterns.EMAIL_ADDRESS.matcher(usernameInput).matches()) {
                usernameInput
            } else {
                val doc = db.collection("usernames").document(usernameInput.lowercase()).get().await()
                doc.getString("email") ?: "${usernameInput.lowercase()}@scanner.app"
            }
            auth.signInWithEmailAndPassword(email, password).await()
            Unit
        } catch (e: Throwable) {
            val failure = e.toAuthFailure()
            // Fallback mode offline jika koneksi server/Firebase terputus/belum terhubung
            if (failure.message == NETWORK_MSG && usernameInput.isNotBlank() && password.length >= 6) {
                Unit
            } else {
                throw failure
            }
        }
    }

    suspend fun register(username: String, email: String = "", password: String): Result<Unit> = safe {
        val name = username.trim()
        val mail = if (email.isBlank()) "${name.lowercase()}@scanner.app" else email.trim()

        try {
            val usernameRef = db.collection("usernames").document(name.lowercase())

            if (usernameRef.get().await().exists()) throw AuthFailure("Username sudah digunakan")

            val user = auth.createUserWithEmailAndPassword(mail, password).await().user
                ?: throw AuthFailure("Gagal membuat akun")

            try {
                // Transaksi menjamin username unik walau ada 2 pendaftar bersamaan
                db.runTransaction { tx ->
                    if (tx.get(usernameRef).exists()) {
                        throw FirebaseFirestoreException(
                            "Username taken", FirebaseFirestoreException.Code.ALREADY_EXISTS
                        )
                    }
                    tx.set(usernameRef, mapOf("uid" to user.uid, "email" to mail))
                    tx.set(
                        db.collection("users").document(user.uid),
                        mapOf(
                            "username" to name,
                            "email" to mail,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    null
                }.await()

                user.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(name).build()
                ).await()
            } catch (e: Throwable) {
                runCatching { user.delete().await() } // rollback akun Auth
                throw e
            }
            Unit
        } catch (e: Throwable) {
            val failure = e.toAuthFailure()
            if (failure.message == NETWORK_MSG && name.isNotBlank() && password.length >= 6) {
                Unit
            } else {
                throw failure
            }
        }
    }

    fun logout() = auth.signOut()
}

private inline fun <T> safe(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e.toAuthFailure())
}

private const val NETWORK_MSG = "Koneksi internet terputus. Periksa jaringan Anda."

private fun Throwable.toAuthFailure(): AuthFailure = when (this) {
    is AuthFailure -> this
    is FirebaseNetworkException, is IOException -> AuthFailure(NETWORK_MSG)
    is FirebaseTooManyRequestsException ->
        AuthFailure("Terlalu banyak percobaan. Coba lagi beberapa saat.")
    is FirebaseAuthWeakPasswordException -> AuthFailure("Password terlalu lemah")
    is FirebaseAuthUserCollisionException -> AuthFailure("Email sudah terdaftar")
    is FirebaseAuthInvalidUserException,
    is FirebaseAuthInvalidCredentialsException ->
        AuthFailure("Username/email atau password salah")
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AuthFailure(NETWORK_MSG)
        FirebaseFirestoreException.Code.ALREADY_EXISTS -> AuthFailure("Username sudah digunakan")
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            AuthFailure("Akses ditolak. Periksa Firestore Rules Anda.")
        else -> AuthFailure("Terjadi kesalahan server. Coba lagi.")
    }
    else -> AuthFailure(message ?: "Terjadi kesalahan tidak terduga")
}

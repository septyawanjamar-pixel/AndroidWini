package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AuthRepository
import com.example.auth.AuthValidator
import com.example.auth.SessionStore
import com.example.model.AuthEvent
import com.example.model.LoginUiState
import com.example.model.RegisterUiState
import com.example.model.UiStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AuthRepository()
    private val session = SessionStore(application)

    private val _login = MutableStateFlow(
        LoginUiState(
            identifier = session.savedIdentifier,
            rememberMe = session.savedIdentifier.isNotEmpty()
        )
    )
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    private val _register = MutableStateFlow(RegisterUiState())
    val register: StateFlow<RegisterUiState> = _register.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    init {
        // Jika "Ingat Saya" tidak aktif, sesi Firebase lama tidak boleh dipakai
        if (!session.rememberMe) repo.logout()
    }

    val isLoggedIn: Boolean get() = repo.isLoggedIn
    val displayName: String get() = repo.displayName

    // ---------------------------------------------------------------- LOGIN

    fun onLoginIdentifierChange(v: String) = _login.update {
        it.copy(identifier = v, identifierError = AuthValidator.identifier(v), status = UiStatus.Idle)
    }

    fun onLoginPasswordChange(v: String) = _login.update {
        it.copy(password = v, passwordError = AuthValidator.loginPassword(v), status = UiStatus.Idle)
    }

    fun onRememberMeChange(checked: Boolean) = _login.update { it.copy(rememberMe = checked) }

    fun toggleLoginPasswordVisibility() = _login.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun login() {
        val s = _login.value
        if (s.status is UiStatus.Loading) return

        val idErr = AuthValidator.identifier(s.identifier)
        val pwErr = AuthValidator.loginPassword(s.password)
        if (idErr != null || pwErr != null) {
            _login.update { it.copy(identifierError = idErr, passwordError = pwErr) }
            return
        }

        _login.update { it.copy(status = UiStatus.Loading) }
        viewModelScope.launch {
            repo.login(s.identifier.trim(), s.password)
                .onSuccess {
                    session.save(s.rememberMe, s.identifier.trim())
                    _login.update { it.copy(password = "", status = UiStatus.Success) }
                    _events.tryEmit(AuthEvent.NavigateHome)
                }
                .onFailure { e ->
                    _login.update { it.copy(status = UiStatus.Error(e.message ?: "Login gagal")) }
                }
        }
    }

    // ------------------------------------------------------------- REGISTER

    fun resetRegister() { _register.value = RegisterUiState() }

    fun onRegisterUsernameChange(v: String) = _register.update {
        it.copy(username = v, usernameError = AuthValidator.username(v), status = UiStatus.Idle)
    }

    fun onRegisterPasswordChange(v: String) = _register.update {
        it.copy(
            password = v,
            passwordError = AuthValidator.password(v),
            confirmError = if (it.confirmPassword.isNotEmpty()) AuthValidator.confirm(v, it.confirmPassword) else null,
            status = UiStatus.Idle
        )
    }

    fun onRegisterConfirmChange(v: String) = _register.update {
        it.copy(confirmPassword = v, confirmError = AuthValidator.confirm(it.password, v), status = UiStatus.Idle)
    }

    fun toggleRegisterPasswordVisibility() = _register.update { it.copy(passwordVisible = !it.passwordVisible) }
    fun toggleRegisterConfirmVisibility() = _register.update { it.copy(confirmVisible = !it.confirmVisible) }

    fun register() {
        val s = _register.value
        if (s.status is UiStatus.Loading) return

        val uErr = AuthValidator.username(s.username)
        val pErr = AuthValidator.password(s.password)
        val cErr = AuthValidator.confirm(s.password, s.confirmPassword)
        if (listOf(uErr, pErr, cErr).any { it != null }) {
            _register.update {
                it.copy(usernameError = uErr, passwordError = pErr, confirmError = cErr)
            }
            return
        }

        _register.update { it.copy(status = UiStatus.Loading) }
        viewModelScope.launch {
            repo.register(s.username, password = s.password)
                .onSuccess {
                    session.rememberMe = false
                    _register.update { it.copy(status = UiStatus.Success) }
                    _events.tryEmit(AuthEvent.ShowMessage("Akun berhasil dibuat. Selamat datang!"))
                    _events.tryEmit(AuthEvent.NavigateHome)
                }
                .onFailure { e ->
                    _register.update { it.copy(status = UiStatus.Error(e.message ?: "Registrasi gagal")) }
                }
        }
    }

    // --------------------------------------------------------------- LOGOUT

    fun logout() {
        repo.logout()
        session.onLogout()
        _login.value = LoginUiState(
            identifier = session.savedIdentifier,
            rememberMe = session.savedIdentifier.isNotEmpty()
        )
        _events.tryEmit(AuthEvent.NavigateToLogin)
    }
}

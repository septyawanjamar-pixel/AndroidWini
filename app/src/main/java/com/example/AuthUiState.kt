package com.example.model

sealed interface UiStatus {
    data object Idle : UiStatus
    data object Loading : UiStatus
    data object Success : UiStatus
    data class Error(val message: String) : UiStatus
}

data class LoginUiState(
    val identifier: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val passwordVisible: Boolean = false,
    val identifierError: String? = null,
    val passwordError: String? = null,
    val status: UiStatus = UiStatus.Idle
)

data class RegisterUiState(
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val confirmVisible: Boolean = false,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val confirmError: String? = null,
    val status: UiStatus = UiStatus.Idle
)

/** Event sekali-jalan (navigasi / snackbar). */
sealed interface AuthEvent {
    data object NavigateHome : AuthEvent
    data object NavigateToLogin : AuthEvent
    data class ShowMessage(val message: String) : AuthEvent
}

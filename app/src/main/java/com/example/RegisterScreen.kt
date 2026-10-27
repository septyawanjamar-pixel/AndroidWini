package com.example.ui.auth

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.UiStatus
import com.example.viewmodel.AuthViewModel

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(Unit) { viewModel.resetRegister() }

    val state by viewModel.register.collectAsStateWithLifecycle()
    val loading = state.status is UiStatus.Loading

    AuthScaffold(
        modifier = Modifier.fillMaxSize(),
        title = "Buat Akun Baru",
        subtitle = "Daftar untuk mulai menggunakan QR Scanner System",
        footer = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sudah punya akun?", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onNavigateBack, enabled = !loading) { Text("Login") }
            }
        }
    ) {
        AuthTextField(
            label = "Username",
            value = state.username,
            onValueChange = viewModel::onRegisterUsernameChange,
            leadingIcon = Icons.Default.Person,
            error = state.usernameError,
            enabled = !loading
        )
        AuthTextField(
            label = "Password",
            value = state.password,
            onValueChange = viewModel::onRegisterPasswordChange,
            leadingIcon = Icons.Default.Lock,
            error = state.passwordError,
            enabled = !loading,
            isPassword = true,
            passwordVisible = state.passwordVisible,
            onTogglePassword = viewModel::toggleRegisterPasswordVisibility
        )
        AuthTextField(
            label = "Konfirmasi Password",
            value = state.confirmPassword,
            onValueChange = viewModel::onRegisterConfirmChange,
            leadingIcon = Icons.Default.Lock,
            error = state.confirmError,
            enabled = !loading,
            isPassword = true,
            passwordVisible = state.confirmVisible,
            onTogglePassword = viewModel::toggleRegisterConfirmVisibility,
            imeAction = ImeAction.Done,
            onImeAction = viewModel::register
        )

        (state.status as? UiStatus.Error)?.let { ErrorBanner(it.message) }

        LoadingButton(
            text = "Buat Akun",
            loading = loading,
            onClick = viewModel::register,
            icon = Icons.Default.PersonAdd
        )
    }
}

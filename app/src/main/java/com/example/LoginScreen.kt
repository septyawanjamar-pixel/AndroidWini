package com.example.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.UiStatus
import com.example.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit
) {
    val state by viewModel.login.collectAsStateWithLifecycle()
    val loading = state.status is UiStatus.Loading

    AuthScaffold(
        modifier = Modifier.fillMaxSize(),
        title = "QR Scanner System",
        subtitle = "Silakan login untuk memulai pemindaian area",
        footer = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Belum punya akun?", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onNavigateToRegister, enabled = !loading) { Text("Daftar") }
            }
        }
    ) {
        AuthTextField(
            label = "Username",
            value = state.identifier,
            onValueChange = viewModel::onLoginIdentifierChange,
            leadingIcon = Icons.Default.Person,
            error = state.identifierError,
            enabled = !loading
        )
        AuthTextField(
            label = "Password",
            value = state.password,
            onValueChange = viewModel::onLoginPasswordChange,
            leadingIcon = Icons.Default.Lock,
            error = state.passwordError,
            enabled = !loading,
            isPassword = true,
            passwordVisible = state.passwordVisible,
            onTogglePassword = viewModel::toggleLoginPasswordVisibility,
            imeAction = ImeAction.Done,
            onImeAction = viewModel::login
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Row(
                modifier = Modifier.clickable(enabled = !loading) {
                    viewModel.onRememberMeChange(!state.rememberMe)
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = state.rememberMe, onCheckedChange = viewModel::onRememberMeChange, enabled = !loading)
                Spacer(Modifier.width(2.dp))
                Text("Ingat Saya", style = MaterialTheme.typography.bodySmall)
            }
        }

        (state.status as? UiStatus.Error)?.let { ErrorBanner(it.message) }

        LoadingButton(
            text = "Login",
            loading = loading,
            onClick = viewModel::login,
            icon = Icons.AutoMirrored.Filled.Login
        )
    }
}

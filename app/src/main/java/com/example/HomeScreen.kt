package com.example.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.input.InputScreen
import com.example.ui.output.OutputScanScreen
import com.example.viewmodel.OutputViewModel
import com.example.viewmodel.ScannerViewModel

private const val TAB_INPUT = 0
private const val TAB_OUTPUT = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    displayName: String,
    onLogout: () -> Unit = {},
    scannerViewModel: ScannerViewModel = viewModel(),
    outputViewModel: OutputViewModel = viewModel(),
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_INPUT) }
    val snackbarHostState = remember { SnackbarHostState() }
    val inputState by scannerViewModel.state.collectAsState()
    val outputState by outputViewModel.state.collectAsState()
    val hasInputData = inputState.qrCode != null || inputState.grade != null || inputState.typeWire != null || inputState.wire != null

    // Tombol Back sistem: Output -> Input -> reset isian -> logout
    BackHandler {
        when {
            selectedTab != TAB_INPUT -> selectedTab = TAB_INPUT
            hasInputData -> scannerViewModel.reset()
            else -> onLogout()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (selectedTab == TAB_INPUT) "Input Scan Area" else "Output Scan Area",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        if (displayName.isNotBlank()) {
                            Text(
                                text = "Operator: $displayName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onLogout, modifier = Modifier.testTag("logout_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (selectedTab == TAB_INPUT) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = { if (hasInputData) scannerViewModel.reset() else onLogout() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("cancel_button"),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    imageVector = if (hasInputData) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (hasInputData) "Reset" else "Logout",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                            }

                            Button(
                                onClick = scannerViewModel::sendData,
                                enabled = inputState.canSend,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("send_data_button"),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                if (inputState.isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Send Data",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = { selectedTab = TAB_INPUT },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("back_to_input_button"),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Kembali ke Input",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                            }

                            // Send Data OUTPUT -> POST /api/scan/output (alasan wajib kalau lewat lead time)
                            Button(
                                onClick = outputViewModel::submitOutput,
                                enabled = outputState.canSubmit,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("send_output_button"),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                if (outputState.isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Send Data",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                    ) {
                        val navColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                        NavigationBarItem(
                            selected = selectedTab == TAB_INPUT,
                            onClick = { selectedTab = TAB_INPUT },
                            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "Menu Input") },
                            label = { Text("Input", fontWeight = FontWeight.SemiBold) },
                            colors = navColors,
                            modifier = Modifier.testTag("nav_input_tab"),
                        )
                        NavigationBarItem(
                            selected = selectedTab == TAB_OUTPUT,
                            onClick = { selectedTab = TAB_OUTPUT },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        // Jumlah QR yang menunggu output (update real-time dari server)
                                        if (outputState.summary.pending > 0) {
                                            Badge { Text(outputState.summary.pending.toString()) }
                                        }
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Menu Output")
                                }
                            },
                            label = { Text("Output", fontWeight = FontWeight.SemiBold) },
                            colors = navColors,
                            modifier = Modifier.testTag("nav_output_tab"),
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (selectedTab == TAB_INPUT) {
            InputScreen(
                viewModel = scannerViewModel,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            OutputScanScreen(
                viewModel = outputViewModel,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

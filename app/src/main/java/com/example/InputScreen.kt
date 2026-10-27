package com.example.ui.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.AutoHideAlert
import com.example.ui.components.DateRow
import com.example.ui.components.DropdownSelector
import com.example.ui.components.PosisiDropdown
import com.example.ui.components.ScannerBox
import com.example.ui.components.SectionCard
import com.example.ui.components.rememberCameraPermission
import com.example.viewmodel.ScannerViewModel

/**
 * LAYAR INPUT
 * Area & Grade diambil dari server (tabel Area / Grade di aWan_data.db). Semua bagian bisa diisi bebas urutannya. Tombol Send Data ada di bar bawah (HomeScreen).
 */
@Composable
fun InputScreen(
    viewModel: ScannerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val today = remember { viewModel.getFormattedDate() }
    val camera = rememberCameraPermission(requestOnStart = true)

    // Notifikasi singkat setiap kali selector dipilih (toast lama diganti yang baru)
    val context = LocalContext.current
    val toastHolder = remember { arrayOfNulls<Toast>(1) }
    fun notify(text: String) {
        toastHolder[0]?.cancel()
        toastHolder[0] = Toast.makeText(context, text, Toast.LENGTH_SHORT).also { it.show() }
    }

    // Popup peringatan: QR sudah pernah di-input (muncul saat Send Data)
    state.duplicateWarning?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicateWarning,
            icon = {
                Icon(
                    Icons.Default.Warning, null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(56.dp),
                )
            },
            title = {
                Text(
                    "PERINGATAN",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            },
            text = {
                Text(
                    "$msg\nData tidak terkirim.",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::dismissDuplicateWarning,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.height(48.dp),
                ) { Text("OK", fontWeight = FontWeight.Bold) }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AutoHideAlert(state.alert)

        DateRow(label = "Tanggal Pemindaian:", date = today)

        // Batas lead time = hari ini + lead time area terpilih (lead_time_hari dari /api/areas)
        val leadTimeHari = state.areas.firstOrNull { it.kodeArea == state.area }?.leadTimeHari
        DateRow(
            label = "Batas Lead Time:",
            date = when {
                state.area == null -> "Pilih area dulu"
                leadTimeHari == null -> "-"
                else -> "${viewModel.getLeadTimeDate(leadTimeHari)} ($leadTimeHari hari)"
            },
        )

        SectionCard(title = "Area Kerja") {
            DropdownSelector(
                options = state.areas.map { it.kodeArea },
                selected = state.area,
                onPick = {
                    viewModel.selectArea(it)
                    notify("Area dipilih: ${state.areaName(it) ?: it}")
                },
                placeholder = if (state.mastersLoading) "Memuat area…" else "Pilih area kerja",
                tag = "area_dropdown",
                label = { state.areaName(it) ?: it },
            )
            MasterReload(empty = state.areas.isEmpty(), loading = state.mastersLoading, onReload = viewModel::loadMasters)
        }

        SectionCard(title = "Scan QR Produk") {
            ScannerBox(
                hasCameraPermission = camera.granted,
                enabled = state.area != null,
                busy = state.isSending || state.qrChecking,
                label = "Kamera Aktif${state.areaName(state.area)?.let { " · $it" } ?: ""}",
                onQr = { viewModel.onQrScanned(it) },
                onRequestPermission = camera.request,
            )
            Spacer(Modifier.height(10.dp))
            // Keterangan saja (bukan tombol): scan dilakukan lewat kamera
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("scan_now_button"),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pindai / Scan QR ", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
            // Keterangan QR yang terakhir di-scan (tetap tampil sampai Send Data berhasil / Reset)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("QR Terbaca:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    state.qrCode ?: "Belum ada QR",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (state.qrCode != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (state.qrCode != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "Grade Wire") {
            DropdownSelector(
                options = state.grades.map { it.grade },
                selected = state.grade,
                onPick = {
                    viewModel.selectGrade(it)
                    notify("Grade dipilih: ${state.gradeName(it) ?: it}")
                },
                placeholder = if (state.mastersLoading) "Memuat grade…" else "Pilih grade",
                enabled = state.qrCode != null,
                tag = "grade_dropdown",
                label = { state.gradeName(it) ?: it },
            )
            MasterReload(empty = state.grades.isEmpty(), loading = state.mastersLoading, onReload = viewModel::loadMasters)
        }

        SectionCard(title = "Type Wire") {
            DropdownSelector(
                options = state.types.map { it.typeWire },
                selected = state.typeWire,
                onPick = {
                    viewModel.selectTypeWire(it)
                    notify("Type wire dipilih: $it")
                },
                placeholder = if (state.mastersLoading) "Memuat type wire…" else "Pilih type wire",
                enabled = state.grade != null,
                tag = "type_dropdown",
                label = { it },
            )
            MasterReload(empty = state.types.isEmpty(), loading = state.mastersLoading, onReload = viewModel::loadMasters)
        }

        SectionCard(title = "Posisi Wire") {
            PosisiDropdown(
                posisi = state.posisiList,
                selected = state.wire?.toIntOrNull(),
                onPick = {
                    viewModel.selectWirePosition(it.nomor.toString())
                    notify("Posisi wire dipilih: Posisi ${it.nomor}")
                },
                onInfo = { notify("Posisi ${it.nomor} sudah dipakai") },
                placeholder = when {
                    state.area == null -> "Pilih area dulu"
                    state.wiresLoading && state.posisiList.isEmpty() -> "Memuat posisi…"
                    state.posisiList.isEmpty() -> "Posisi belum termuat"
                    else -> "Pilih posisi wire"
                },
                enabled = state.area != null && state.posisiList.isNotEmpty() && state.typeWire != null,
            )
            if (state.area != null && state.posisiList.isEmpty() && !state.wiresLoading) {
                TextButton(onClick = viewModel::reloadPosisi) { Text("Server belum terhubung — Muat ulang") }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/** Muncul bila daftar dari server kosong (mis. server belum jalan): tombol muat ulang. */
@Composable
private fun MasterReload(empty: Boolean, loading: Boolean, onReload: () -> Unit) {
    if (!empty || loading) return
    Spacer(Modifier.height(6.dp))
    TextButton(onClick = onReload) { Text("Data kosong / server belum terhubung — Muat ulang") }
}

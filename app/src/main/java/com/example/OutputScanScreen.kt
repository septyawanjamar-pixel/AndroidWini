package com.example.ui.output

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.Amber
import com.example.ui.components.AutoHideAlert
import com.example.ui.components.DropdownSelector
import com.example.ui.components.DateRow
import com.example.ui.components.ErrRed
import com.example.ui.components.MutedGrey
import com.example.ui.components.OkGreen
import com.example.ui.components.ScannerBox
import com.example.ui.components.SectionCard
import com.example.ui.components.SummaryRow
import com.example.ui.components.rememberCameraPermission
import com.example.viewmodel.OutputViewModel

/**
 * LAYAR OUTPUT
 * Scan QR -> data input tampil lengkap -> tekan Send Data (bar bawah, HomeScreen). Area hanya untuk filter daftar.
 */
@Composable
fun OutputScanScreen(
    viewModel: OutputViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val today = remember { viewModel.getFormattedDate() }
    val camera = rememberCameraPermission(requestOnStart = false)

    // Notifikasi singkat setiap kali selector dipilih (toast lama diganti yang baru)
    val context = LocalContext.current
    val toastHolder = remember { arrayOfNulls<Toast>(1) }
    fun notify(text: String) {
        toastHolder[0]?.cancel()
        toastHolder[0] = Toast.makeText(context, text, Toast.LENGTH_SHORT).also { it.show() }
    }

    // Info non-kritis (gagal muat data, dsb.) -> Snackbar HomeScreen
    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        viewModel.clearMessage()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(msg)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AutoHideAlert(state.alert)

        // Popup info barang berikutnya (tombol i di kartu Filter Area)
        state.nextInfo?.let { row ->
            AlertDialog(
                onDismissRequest = viewModel::closeNextInfo,
                title = { Text("Info barang", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SummaryRow("QR", row.qrId, mono = true)
                        SummaryRow("Grade", row.grade?.let { "Grade $it" } ?: "-")
                        SummaryRow("Type", row.typeWire ?: "-")
                        SummaryRow("Posisi Wire", row.posisiWire?.toString() ?: "-")
                    }
                },
                confirmButton = { TextButton(onClick = viewModel::closeNextInfo) { Text("Tutup") } },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { DateRow(label = "Tanggal Output:", date = today) }
            Spacer(Modifier.width(10.dp))
            LiveIndicator(state.isLive)
        }

        SectionCard(
            title = "Filter Area",
            trailing = {
                OutlinedButton(
                    onClick = viewModel::showNextInfo,
                    enabled = state.selectedArea != null && !state.qrChecking && !state.isSubmitting,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("out_next_info_button"),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Icon(Icons.Default.Info, contentDescription = "Informasi", modifier = Modifier.size(22.dp)) }
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1.5f)) {
                    DropdownSelector(
                        options = listOf(OutputViewModel.ALL_AREAS) + state.areas.map { it.kodeArea },
                        selected = state.selectedArea ?: OutputViewModel.ALL_AREAS,
                        onPick = {
                            viewModel.selectArea(it)
                            notify("Filter area: ${if (it == OutputViewModel.ALL_AREAS) it else state.areaName(it) ?: it}")
                        },
                        placeholder = OutputViewModel.ALL_AREAS,
                        tag = "out_area_dropdown",
                        label = { if (it == OutputViewModel.ALL_AREAS) it else state.areaName(it) ?: it },
                    )
                }
                Spacer(Modifier.width(10.dp))
                // Kartu posisi selanjutnya (posisi barang tertua di area)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("out_posisi_next"),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            "Posisi selanjutnya",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            state.nextRow?.posisiWire?.let { "Posisi $it" } ?: "-",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }

        SectionCard(title = "Scan QR Barang") {
            ScannerBox(
                hasCameraPermission = camera.granted,
                busy = state.qrChecking || state.isSubmitting,
                label = "Mode OUTPUT${state.areaName(state.selectedArea)?.let { " · $it" } ?: ""}",
                onQr = { viewModel.onQrScanned(it) },
                onRequestPermission = camera.request,
                enabled = state.selectedArea != null, // Poka Yoke: kamera mati sebelum area dipilih
            )
            Spacer(Modifier.height(10.dp))
            // Keterangan saja (bukan tombol): scan dilakukan lewat kamera
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("output_scan_button"),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pindai / Scan QR Output  ", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Batal: membatalkan semua pilihan (area, QR, alasan)
        if (state.selectedArea != null || state.scanned != null) OutlinedButton(
            onClick = viewModel::cancelScan,
            enabled = !state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("out_cancel_all"),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Batal") }

        // ===================================================== ALASAN
        // Lewat lead time -> Alasan keluar LeadTime. Posisi tidak sesuai (FIFO) -> Alasan Tidak Sesuai.
        // Dalam lead time & posisi sesuai: bagian ini tidak muncul, cukup pilih area + scan + Send Data.
        if (state.perluAlasan || state.perluTidakSesuai) SectionCard(title = "Alasan Output") {
            if (state.perluAlasan) {
                Text(
                    "Barang lewat lead time: Alasan keluar LeadTime WAJIB dipilih.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrRed,
                )
            }
            state.scanned?.fifo?.seharusnya?.let { h ->
                Text(
                    "Posisi tidak sesuai: seharusnya ambil ${h.qrId} di posisi ${h.posisiWire ?: "-"}. Alasan Tidak Sesuai WAJIB dipilih.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrRed,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.perluAlasan) AlasanCard(
                    title = "Alasan keluar LeadTime *",
                    options = state.alasanList.map { it.alasan },
                    selected = state.selectedAlasan?.alasan,
                    onPick = {
                        viewModel.selectAlasan(it)
                        notify("Alasan keluar LeadTime: ${it.replace('_', ' ')}")
                    },
                    onReload = viewModel::loadAlasan,
                    onClear = viewModel::clearAlasan,
                    color = ErrRed,
                    modifier = Modifier.weight(1f),
                )
                if (state.perluTidakSesuai) AlasanCard(
                    title = "Alasan Tidak Sesuai *",
                    options = state.tidakSesuaiList.map { it.alasan },
                    selected = state.selectedTidakSesuai?.alasan,
                    onPick = {
                        viewModel.selectTidakSesuai(it)
                        notify("Alasan tidak sesuai: ${it.replace('_', ' ')}")
                    },
                    onReload = viewModel::loadTidakSesuai,
                    onClear = viewModel::clearTidakSesuai,
                    color = OkGreen,
                    tag = "tidak_sesuai_dropdown",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        state.scanned?.let { row ->
            SectionCard(title = "Data QR") {
                SummaryRow("QR", row.qrId, mono = true)
                SummaryRow("Area", row.namaArea ?: row.area)
                SummaryRow("Grade", row.grade?.let { "Grade $it" })
                SummaryRow("Type Wire", row.typeWire)
                SummaryRow("Posisi Wire", row.posisiWire?.toString())
                SummaryRow("Di-input", row.tanggalInput)
                SummaryRow("Lama stagnasi", row.lamaStagnasiHari?.let { "$it hari" })
                // Alasan yang sudah dipilih (hanya muncul kalau lewat lead time / posisi tidak sesuai FIFO)
                if (state.perluAlasan && state.selectedAlasan != null) {
                    SummaryRow("Alasan lewat lead time", state.selectedAlasan?.alasan?.replace('_', ' '))
                }
                if (state.perluTidakSesuai && state.selectedTidakSesuai != null) {
                    SummaryRow("Alasan tidak sesuai FIFO", state.selectedTidakSesuai?.alasan?.replace('_', ' '))
                }
                Spacer(Modifier.height(8.dp))
                LeadTimeBadge(
                    lewat = state.perluAlasan,
                    hari = row.lamaStagnasiHari ?: 0,
                    leadTime = row.leadTimeHari ?: OutputViewModel.LEAD_TIME_HARI,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** Status lead time QR yang di-scan: hijau = masih dalam lead time area, merah = lewat (alasan wajib). */
@Composable
private fun LeadTimeBadge(lewat: Boolean, hari: Int, leadTime: Int) {
    val color = if (lewat) ErrRed else OkGreen
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lead_time_badge"),
    ) {
        Text(
            if (lewat) "LEWAT LEAD TIME · $hari hari (> $leadTime hari) · alasan wajib diisi"
            else "DALAM LEAD TIME · $hari hari (maks $leadTime hari) · alasan opsional",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun LiveIndicator(isLive: Boolean) {
    val alpha by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "live_alpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isLive) OkGreen.copy(alpha = alpha) else MutedGrey)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            if (isLive) "LIVE" else "Offline",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isLive) OkGreen else MutedGrey,
        )
    }
}

/** Kartu dropdown: Alasan keluar LeadTime (tabel Alasan) & Alasan Tidak Sesuai (tabel Tidak_sesuai). */
@Composable
private fun AlasanCard(
    title: String,
    options: List<String>,
    selected: String?,
    onPick: (String) -> Unit,
    onReload: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Amber,
    tag: String = "alasan_dropdown",
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            DropdownSelector(
                options = options,
                selected = selected,
                onPick = onPick,
                placeholder = if (options.isEmpty()) "Memuat…" else "Pilih alasan",
                tag = tag,
                enabled = options.isNotEmpty(),
                label = { it.replace('_', ' ') },
            )
            if (selected != null) {
                Text(
                    "Kosongkan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable(onClick = onClear),
                )
            }
            if (options.isEmpty()) {
                Text(
                    "Muat ulang",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable(onClick = onReload),
                )
            }
        }
    }
}

package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Popup
import androidx.core.content.ContextCompat
import com.example.camera.RealCameraPreview
import kotlinx.coroutines.delay

/* =============================================================================
   Komponen tampilan yang dipakai bersama oleh layar Input dan Output.
   (Pengganti PokaYokeComponents.kt — tanpa kunci langkah, getar, dan bunyi.)
   ============================================================================= */

internal val OkGreen = Color(0xFF16A34A)
internal val ErrRed = Color(0xFFDC2626)
internal val Amber = Color(0xFFF59E0B)
internal val MutedGrey = Color(0xFF94A3B8)

/** Pesan status untuk UI. id unik supaya pesan yang sama tetap tampil ulang. */
data class UiAlert(val id: Long, val ok: Boolean, val text: String)

/* ------------------------------------------------------------ pesan status */

/** Pesan di tengah layar yang hilang sendiri setelah beberapa detik (supaya operator pasti membaca). */
@Composable
fun AutoHideAlert(alert: UiAlert?) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(alert?.id) {
        if (alert == null) return@LaunchedEffect
        // Pesan lama (mis. saat pindah tab lalu kembali) tidak ditampilkan ulang
        if ((android.os.SystemClock.elapsedRealtimeNanos() - alert.id) / 1_000_000 > 1_500) return@LaunchedEffect
        visible = true
        delay(if (alert.ok) 2_500 else 4_000)
        visible = false
    }
    if (visible && alert != null) {
        // Popup tidak menghalangi sentuhan: operator tetap bisa lanjut kerja selama pesan tampil
        Popup(alignment = Alignment.Center) {
            AlertBanner(ok = alert.ok, text = alert.text)
        }
    }
}

@Composable
fun AlertBanner(ok: Boolean, text: String) {
    val color = if (ok) OkGreen else ErrRed
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, color),
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .testTag(if (ok) "ok_banner" else "error_banner"),
    ) {
        Column(
            Modifier
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Error, null, tint = color, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text,
                color = color,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

/* ------------------------------------------------------------ izin kamera */

class CameraPermission(val granted: Boolean, val request: () -> Unit)

@Composable
fun rememberCameraPermission(requestOnStart: Boolean = true): CameraPermission {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (requestOnStart && !granted) launcher.launch(Manifest.permission.CAMERA) }
    return CameraPermission(granted) { launcher.launch(Manifest.permission.CAMERA) }
}

/* ------------------------------------------------------------ tampilan */

@Composable
fun DateRow(label: String, date: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.CalendarToday, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(date, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
    }
}

/** Kartu biasa dengan judul. Selalu bisa disentuh (tidak ada kunci langkah). */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,   // tombol kecil di pojok kanan judul (opsional)
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                trailing?.invoke()
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** Dropdown pilihan: kotak yang bila ditekan membuka daftar opsi. */
@Composable
fun DropdownSelector(
    options: List<String>,
    selected: String?,
    onPick: (String) -> Unit,
    placeholder: String,
    tag: String,
    enabled: Boolean = true,
    label: (String) -> String = { it },
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .border(
                    if (expanded) 2.dp else 1.dp,
                    if (expanded) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    RoundedCornerShape(12.dp),
                )
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 14.dp)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selected?.let(label) ?: placeholder,
                modifier = Modifier.weight(1f),
                fontWeight = if (selected != null) FontWeight.Bold else FontWeight.Normal,
                color = if (selected != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .heightIn(max = 320.dp), // panjang -> bisa di-scroll, tidak menutupi layar
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Text(label(opt), fontWeight = if (opt == selected) FontWeight.Bold else FontWeight.Normal)
                    },
                    trailingIcon = {
                        if (opt == selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = {
                        onPick(opt)
                        expanded = false
                    },
                    modifier = Modifier.testTag("${tag}_$opt"),
                )
            }
        }
    }
}

/** Kotak kamera. Kamera hidup bila izin sudah diberikan dan area sudah dipilih. Data hanya dari QR asli. */
@Composable
fun ScannerBox(
    hasCameraPermission: Boolean,
    busy: Boolean,
    label: String,
    onQr: (String) -> Unit,
    onRequestPermission: () -> Unit,
    enabled: Boolean = true,  // false = terkunci (mis. area belum dipilih): kamera tidak dinyalakan
) {
    val laser by rememberInfiniteTransition(label = "laser").animateFloat(
        initialValue = 0.08f, targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "laser_y",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .testTag("scanner_viewfinder_box"),
        contentAlignment = Alignment.Center,
    ) {
        if (hasCameraPermission && enabled) {
            RealCameraPreview(onQrDetected = onQr, modifier = Modifier.fillMaxSize())
        }
        ViewfinderOverlay(laser)

        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0x99000000)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (hasCameraPermission && enabled) Color(0xFF22C55E) else Amber)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (!enabled) "Terkunci" else if (hasCameraPermission) label else "Izin kamera belum ada",
                            style = MaterialTheme.typography.labelSmall, color = Color.White,
                        )
                    }
                }
                if (!hasCameraPermission && enabled) {
                    Button(
                        onClick = onRequestPermission,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Buka Kamera", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            when {
                busy -> CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                !enabled -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.QrCodeScanner, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                    Text("Pilih area kerja dulu", color = Color.White, fontWeight = FontWeight.Bold)
                }
                !hasCameraPermission -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.QrCodeScanner, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(48.dp))
                    Text("Arahkan kamera ke QR", color = Color.White, fontWeight = FontWeight.Bold)
                }
                else -> Spacer(Modifier.height(1.dp))
            }
            Spacer(Modifier.height(1.dp))
        }
        // Simulasi scan (ketuk kotak kamera) dinonaktifkan: data hanya dari QR code asli yang terbaca kamera
    }
}

@Composable
private fun BoxScope.ViewfinderOverlay(laser: Float) {
    Canvas(Modifier.matchParentSize()) {
        val len = 32.dp.toPx()
        val sw = 4.dp.toPx()
        val c = Color(0xFF38BDF8)
        val p = 22.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(c, Offset(p, p), Offset(p + len, p), sw)
        drawLine(c, Offset(p, p), Offset(p, p + len), sw)
        drawLine(c, Offset(w - p, p), Offset(w - p - len, p), sw)
        drawLine(c, Offset(w - p, p), Offset(w - p, p + len), sw)
        drawLine(c, Offset(p, h - p), Offset(p + len, h - p), sw)
        drawLine(c, Offset(p, h - p), Offset(p, h - p - len), sw)
        drawLine(c, Offset(w - p, h - p), Offset(w - p - len, h - p), sw)
        drawLine(c, Offset(w - p, h - p), Offset(w - p, h - p - len), sw)
        drawLine(Color(0xFFEF4444), Offset(p + 12f, h * laser), Offset(w - p - 12f, h * laser), 3.dp.toPx())
    }
}

@Composable
fun SummaryRow(label: String, value: String?, mono: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp),
        )
        Text(
            value ?: "—",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            ),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
}

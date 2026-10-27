package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.network.PosisiDto

/**
 * Dropdown posisi wire untuk layar INPUT.
 * Tiap pilihan diberi warna: hijau = kosong (bisa dipilih), merah = sudah dipakai (tidak bisa dipilih).
 */
@Composable
fun PosisiDropdown(
    posisi: List<PosisiDto>,
    selected: Int?,
    onPick: (PosisiDto) -> Unit,
    onInfo: (PosisiDto) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
    tag: String = "wire_dropdown",
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .border(
                    if (expanded) 2.dp else 1.dp,
                    if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape,
                )
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 14.dp)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected != null) {
                NomorKotak(selected, OkGreen)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                if (selected != null) "Posisi $selected" else placeholder,
                modifier = Modifier.weight(1f),
                fontWeight = if (selected != null) FontWeight.Bold else FontWeight.Normal,
                color = if (selected != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
                .heightIn(max = 320.dp),
        ) {
            posisi.forEach { p ->
                val warna = if (p.terisi) ErrRed else OkGreen
                DropdownMenuItem(
                    leadingIcon = { NomorKotak(p.nomor, warna) },
                    text = {
                        Text(
                            if (p.terisi) "Sudah dipakai" else "Kosong",
                            color = warna,
                            fontWeight = if (p.nomor == selected) FontWeight.Bold else FontWeight.SemiBold,
                        )
                    },
                    trailingIcon = {
                        if (p.nomor == selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = {
                        if (p.terisi) onInfo(p) else { onPick(p); expanded = false }
                    },
                    modifier = Modifier
                        .semantics { contentDescription = "Posisi ${p.nomor}, ${if (p.terisi) "sudah dipakai" else "kosong"}" }
                        .testTag("${tag}_${p.nomor}"),
                )
            }
        }
    }
}

/** Kotak kecil berwarna berisi nomor posisi. */
@Composable
private fun NomorKotak(nomor: Int, warna: Color) {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(warna),
        contentAlignment = Alignment.Center,
    ) {
        Text("$nomor", color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

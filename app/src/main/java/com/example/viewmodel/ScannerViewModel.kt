package com.example.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.AreaDto
import com.example.network.GradeDto
import com.example.network.LookupResult
import com.example.network.PosisiDto
import com.example.network.TypeDto
import com.example.network.ScanEvents
import com.example.network.ScanRepository
import com.example.network.ServerEvent
import com.example.ui.components.UiAlert
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* =============================================================================
   Layar INPUT
   - Daftar Area & Grade diambil dari backend (tabel Area & Grade di aWan_data.db).
   - Area, Scan QR, Grade, Type Wire, Posisi Wire bisa diisi dengan urutan bebas.
   - Posisi wire tampil sebagai grid: hijau = kosong, merah = terisi (1 posisi = 1 gulungan).
     Isi grid ikut berubah real-time saat HP lain input / output di area yang sama (SSE /api/events).
   - Send Data -> POST /api/scan/input
   ============================================================================= */

data class InputState(
    val areas: List<AreaDto> = emptyList(),
    val grades: List<GradeDto> = emptyList(),
    val types: List<TypeDto> = emptyList(),   // tabel Type
    val mastersLoading: Boolean = false,
    val area: String? = null,          // lokasi area, mis. "Area CUROD"
    val qrCode: String? = null,        // qr_id lengkap, mis. "2026-100-10-11-0369"
    val grade: String? = null,         // kode grade, mis. "A"
    val typeWire: String? = null,      // type wire, mis. "NORMAL"
    val wire: String? = null,          // "1".."16"
    val posisiList: List<PosisiDto> = emptyList(), // posisi + isi untuk area terpilih (tabel POSISI)
    val wiresLoading: Boolean = false,
    val qrChecking: Boolean = false,  // sedang cek QR ke server (saat scan)
    val isSending: Boolean = false,
    val alert: UiAlert? = null,
    val duplicateWarning: String? = null, // != null -> popup peringatan "QR sudah pernah di-input"
) {
    val canSend: Boolean
        get() = area != null && qrCode != null && grade != null && typeWire != null && wire != null && !isSending

    fun areaName(kode: String?): String? = kode?.let { k -> areas.firstOrNull { it.kodeArea == k }?.namaArea ?: k }
    fun gradeName(kode: String?): String? = kode?.let { k -> grades.firstOrNull { it.grade == k }?.keterangan ?: "Grade $k" }
}

class ScannerViewModel : ViewModel() {

    private var posisiJob: Job? = null

    private val _state = MutableStateFlow(InputState())
    val state: StateFlow<InputState> = _state.asStateFlow()

    private val repo = ScanRepository()
    private var lastRaw: String? = null
    private var lastRawAt = 0L

    init {
        loadMasters()
        listenRealtime()
    }

    /** Ambil daftar Area, Grade & Type dari server. */
    fun loadMasters() {
        viewModelScope.launch {
            _state.update { it.copy(mastersLoading = true) }
            val areas = repo.areas()
            val grades = repo.grades()
            val types = repo.types()
            _state.update { s ->
                val a = areas.getOrNull() ?: s.areas
                val g = grades.getOrNull() ?: s.grades
                val t = types.getOrNull() ?: s.types
                s.copy(
                    mastersLoading = false,
                    areas = a,
                    grades = g,
                    types = t,
                    area = s.area?.takeIf { k -> a.any { it.kodeArea == k } },
                    grade = s.grade?.takeIf { k -> g.any { it.grade == k } },
                    typeWire = s.typeWire?.takeIf { k -> t.any { it.typeWire == k } },
                )
            }
            _state.value.area?.let { loadPosisi(it) } // muat ulang posisi untuk area yang sedang dipilih
            (areas.exceptionOrNull() ?: grades.exceptionOrNull() ?: types.exceptionOrNull())?.let {
                alert(false, "Gagal memuat Area/Grade/Type: ${it.message}")
            }
        }
    }

    /** Pilih area -> posisi lama dikosongkan, lalu daftar posisi area itu diambil dari tabel POSISI. */
    fun selectArea(area: String) {
        if (area == _state.value.area && _state.value.posisiList.isNotEmpty()) return
        _state.update { it.copy(area = area, wire = null, posisiList = emptyList()) }
        loadPosisi(area)
    }

    /** Muat ulang grid posisi area terpilih (tombol / setelah gagal). */
    fun reloadPosisi() { _state.value.area?.let { loadPosisi(it) } }

    /**
     * Ambil posisi + isinya dari server. silent = dipanggil karena event real-time (tanpa loading).
     * Kalau posisi yang sedang dipilih ternyata baru saja diisi HP lain -> pilihan dikosongkan.
     */
    private fun loadPosisi(area: String, silent: Boolean = false) {
        posisiJob?.cancel()
        posisiJob = viewModelScope.launch {
            if (!silent) _state.update { it.copy(wiresLoading = true) }
            val result = repo.posisi(area)
            val list = result.getOrNull()
            var direbut: String? = null
            _state.update { s ->
                if (s.area != area) s // area sudah diganti lagi
                else {
                    val baru = list ?: s.posisiList
                    val wireMasihKosong = s.wire?.takeIf { w -> baru.any { it.nomor.toString() == w && !it.terisi } }
                    if (s.wire != null && wireMasihKosong == null && list != null) direbut = s.wire
                    s.copy(wiresLoading = false, posisiList = baru, wire = wireMasihKosong)
                }
            }
            direbut?.let { alert(false, "Posisi $it baru saja dipakai. Pilih posisi lain.") }
            result.exceptionOrNull()?.let { if (!silent) alert(false, "Gagal memuat posisi: ${it.message}") }
        }
    }

    /** Real-time: input / output di area yang sama -> warna grid posisi diperbarui. */
    private fun listenRealtime() {
        viewModelScope.launch {
            ScanEvents.events().collect { ev ->
                when (ev) {
                    is ServerEvent.ScanChanged -> {
                        val area = _state.value.area
                        if (area != null && (ev.area == null || ev.area == area) && !_state.value.isSending) loadPosisi(area, silent = true)
                    }
                    ServerEvent.Connected, ServerEvent.DataChanged -> reloadPosisiSilent()
                    ServerEvent.Disconnected -> Unit
                }
            }
        }
    }

    private fun reloadPosisiSilent() { _state.value.area?.let { loadPosisi(it, silent = true) } }

    fun selectGrade(grade: String) = _state.update { it.copy(grade = grade) }
    fun selectTypeWire(type: String) = _state.update { it.copy(typeWire = type) }
    /** Pilih posisi. Posisi yang terisi tidak bisa dipilih (1 posisi = 1 gulungan). */
    fun selectWirePosition(position: String) {
        val p = _state.value.posisiList.firstOrNull { it.nomor.toString() == position }
        if (p != null && p.terisi) return alert(false, "Posisi $position sudah dipakai. Pilih yang hijau.")
        _state.update { it.copy(wire = position) }
    }

    /**
     * Dipanggil kamera (bisa berkali-kali per detik).
     * Kunci langkah: Area harus sudah dipilih. QR dicek ke server dulu; yang masih IN (belum di-output) ditolak saat scan.
     */
    fun onQrScanned(raw: String) {
        val value = raw.trim()
        val before = _state.value
        if (value.isEmpty() || before.isSending || before.qrChecking) return
        if (before.area == null) return alert(false, "Pilih area kerja dulu sebelum scan QR")

        // Kamera melihat QR yang sama terus-menerus -> anggap 1 scan
        val now = SystemClock.elapsedRealtime()
        if (value == lastRaw && now - lastRawAt < DEBOUNCE_MS) return
        lastRaw = value; lastRawAt = now

        val code = QrCodeParser.parse(value)
            ?: return alert(false, "Format QR salah: $value (contoh benar: 100-10-11-0369)")
        if (code == before.qrCode) return

        _state.update { it.copy(qrChecking = true) }
        viewModelScope.launch {
            val cek = repo.lookup(code)
            _state.update { it.copy(qrChecking = false) }
            when (cek) {
                is LookupResult.Found ->
                    if (cek.row.status == "IN") return@launch alert(false, "QR $code masih IN (belum di-output). Scan ditolak.")
                is LookupResult.Failed ->
                    return@launch alert(false, "QR $code tidak bisa dicek ke server: ${cek.message}")
                LookupResult.NotFound -> Unit
            }
            // Area dikosongkan selama cek berjalan -> scan dibatalkan
            if (_state.value.area == null) return@launch
            _state.update { it.copy(qrCode = code) }
            alert(true, "QR $code terbaca.")
        }
    }

    /** Tombol Send Data -> POST /api/scan/input */
    fun sendData() {
        val s = _state.value
        if (!s.canSend) return alert(false, "Lengkapi Area, QR, Grade, Type Wire, dan Posisi Wire dulu")
        val area = s.area!!
        val code = s.qrCode!!
        val grade = s.grade!!
        val typeWire = s.typeWire!!
        val wire = s.wire!!

        viewModelScope.launch {
            _state.update { it.copy(isSending = true) }
            val result = repo.input(qrId = code, area = area, grade = grade, typeWire = typeWire, posisiWire = wire.toInt())
            _state.update { it.copy(isSending = false) }

            result.onSuccess { row ->
                // Area tetap (operator masih di area yang sama); sisanya dikosongkan untuk scan berikutnya
                lastRaw = null
                // Setelah Send Data: Area Kerja kembali kosong supaya operator memilih area lagi
                _state.update { it.copy(area = null, posisiList = emptyList(), qrCode = null, grade = null, typeWire = null, wire = null) }
                val leadtime = row?.tanggalLeadtime?.let { " · Lead time s/d $it" } ?: ""
                alert(true, "Tersimpan: $code · ${s.gradeName(grade)} · $typeWire · Posisi $wire$leadtime")
            }
            result.onFailure { e ->
                // QR sudah pernah di-input (409 dari server) -> popup peringatan besar
                if (e.message?.contains("sudah pernah di-input") == true) {
                    _state.update { it.copy(duplicateWarning = "QR $code sudah pernah di-input.") }
                    return@onFailure
                }
                alert(false, "Gagal kirim: ${e.message}")
                // mis. posisi keburu diisi HP lain -> segarkan grid
                if (e.message?.contains("masih terisi") == true) {
                    _state.update { it.copy(wire = null) }
                    loadPosisi(area, silent = true)
                }
            }
        }
    }

    /** Tutup popup peringatan QR duplikat. */
    fun dismissDuplicateWarning() = _state.update { it.copy(duplicateWarning = null) }

    /** Tombol Reset: kosongkan QR, grade, type wire, posisi (area tetap). */
    fun reset() {
        if (_state.value.isSending) return
        lastRaw = null
        _state.update { it.copy(qrCode = null, grade = null, typeWire = null, wire = null) }
    }

    private fun alert(ok: Boolean, text: String) {
        _state.update { it.copy(alert = UiAlert(SystemClock.elapsedRealtimeNanos(), ok, text)) }
    }

    fun getFormattedDate(): String = runCatching {
        SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("id-ID")).format(Date())
    }.getOrElse { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()) }

    /** Tanggal hari ini + lead time area (hari), format sama dengan Tanggal Pemindaian. */
    fun getLeadTimeDate(hari: Int): String {
        val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, hari) }.time
        return runCatching {
            SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("id-ID")).format(date)
        }.getOrElse { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date) }
    }

    private companion object {
        const val DEBOUNCE_MS = 2_500L
    }
}

package com.example.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.AlasanDto
import com.example.network.AreaDto
import com.example.network.TidakSesuaiDto
import com.example.network.LookupResult
import com.example.network.ScanEvents
import com.example.network.ScanRepository
import com.example.network.ScanRow
import com.example.network.ServerEvent
import com.example.ui.components.UiAlert
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* =============================================================================
   Layar OUTPUT
   - Filter Area (dari tabel Area) hanya menyaring daftar; kosong = semua area.
   - Scan QR -> GET /api/scan/{qr} -> data tampil -> "Send Data" (bar bawah) -> POST /api/scan/output
   - Lead time per area dari tabel Batas_Lead_Time (field lead_time_hari dari server).
       stagnasi <= lead time  -> pilih area + scan, lalu tekan Send Data
       stagnasi  > lead time  -> muncul Alasan keluar LeadTime (WAJIB), lalu tekan Send Data
   - Posisi tidak sesuai FIFO (fifo.seharusnya dari backend posisi) -> muncul Alasan Tidak Sesuai
     (GRADE / SELECTED, WAJIB). Posisi sesuai -> pilihan Tidak Sesuai tidak muncul.
   - Daftar & angka di-update real-time lewat SSE /api/events (juga saat data diubah dari DBeaver).
   ============================================================================= */

data class OutputSummary(val pending: Int = 0)

data class OutputUiState(
    val areas: List<AreaDto> = emptyList(),
    val alasanList: List<AlasanDto> = emptyList(),  // isi tabel Alasan
    val selectedAlasan: AlasanDto? = null,          // wajib hanya kalau lewat lead time
    val tidakSesuaiList: List<TidakSesuaiDto> = emptyList(), // isi tabel Tidak_sesuai
    val selectedTidakSesuai: TidakSesuaiDto? = null,         // wajib kalau posisi tidak sesuai
    val selectedArea: String? = null,            // lokasi area, null = semua area
    val scanned: ScanRow? = null,                // QR yang sudah di-scan, menunggu disimpan
    val qrChecking: Boolean = false,
    val isSubmitting: Boolean = false,
    val alert: UiAlert? = null,
    // ---- papan real-time ----
    val isLive: Boolean = false,
    val loading: Boolean = false,
    val items: List<ScanRow> = emptyList(),
    val summary: OutputSummary = OutputSummary(),   // jumlah menunggu output (badge tab Output)
    val message: String? = null,                 // info non-kritis -> snackbar
    val nextInfo: ScanRow? = null,               // info barang berikutnya (popup tombol i)
    val nextRow: ScanRow? = null,                // posisi selanjutnya (kartu di Filter Area)
) {
    fun areaName(kode: String?): String? = kode?.let { k -> areas.firstOrNull { it.kodeArea == k }?.namaArea ?: k }

    /** QR yang di-scan sudah lewat lead time area-nya -> alasan wajib (sama dengan cek di backend). */
    val perluAlasan: Boolean get() = scanned?.lewatLeadTimeYa == true

    /** Posisi QR yang di-scan bukan yang harus diambil duluan (FIFO backend posisi) -> alasan tidak sesuai wajib. */
    val perluTidakSesuai: Boolean get() = scanned?.fifo?.seharusnya != null

    /** Tombol Send Data boleh ditekan. */
    val canSubmit: Boolean get() = scanned != null && !isSubmitting &&
        (!perluAlasan || selectedAlasan != null) && (!perluTidakSesuai || selectedTidakSesuai != null)
}

class OutputViewModel : ViewModel() {

    private val _state = MutableStateFlow(OutputUiState())
    val state: StateFlow<OutputUiState> = _state.asStateFlow()

    private val repo = ScanRepository()
    private var lastRaw: String? = null
    private var lastRawAt = 0L
    private var refreshJob: Job? = null
    /** Naik tiap Batal / Send Data sukses -> hasil cek QR yang masih berjalan diabaikan. */
    private var scanToken = 0
    /** Nomor urut permintaan: jawaban server yang datang terlambat (data lama) tidak menimpa data terbaru. */
    private var nextSeq = 0
    private var listSeq = 0

    init {
        loadAreas()
        loadAlasan()
        loadTidakSesuai()
        refresh()
        listenRealtime()
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun getFormattedDate(): String = runCatching {
        SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("id-ID")).format(Date())
    }.getOrElse { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()) }

    private fun loadAreas() {
        viewModelScope.launch {
            repo.areas().onSuccess { list -> _state.update { it.copy(areas = list) } }
        }
    }

    fun loadAlasan() {
        viewModelScope.launch {
            repo.alasan()
                .onSuccess { list ->
                    _state.update { s ->
                        s.copy(alasanList = list, selectedAlasan = s.selectedAlasan?.let { a -> list.firstOrNull { it.id == a.id } })
                    }
                }
                .onFailure { e -> _state.update { it.copy(message = "Gagal memuat alasan: ${e.message}") } }
        }
    }

    fun loadTidakSesuai() {
        viewModelScope.launch {
            repo.tidakSesuai()
                .onSuccess { list ->
                    _state.update { s ->
                        s.copy(
                            tidakSesuaiList = list,
                            selectedTidakSesuai = s.selectedTidakSesuai?.let { t -> list.firstOrNull { it.id == t.id } },
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(message = "Gagal memuat alasan tidak sesuai: ${e.message}") } }
        }
    }

    fun selectTidakSesuai(nama: String) = _state.update { s ->
        s.copy(selectedTidakSesuai = s.tidakSesuaiList.firstOrNull { it.alasan == nama })
    }

    fun selectAlasan(nama: String) = _state.update { s ->
        s.copy(selectedAlasan = s.alasanList.firstOrNull { it.alasan == nama })
    }

    /** Kosongkan pilihan (opsional bila belum lewat lead time). */
    fun clearAlasan() = _state.update { it.copy(selectedAlasan = null) }
    fun clearTidakSesuai() = _state.update { it.copy(selectedTidakSesuai = null) }

    /** Pilih area untuk filter daftar. ALL_AREAS = tampilkan semua area. */
    fun selectArea(area: String) {
        val newArea = area.takeIf { it != ALL_AREAS }
        if (newArea == _state.value.selectedArea) return
        _state.update { it.copy(selectedArea = newArea, items = emptyList(), summary = OutputSummary()) }
        refresh()
    }

    /** Barang berikutnya menurut urutan input: tanggal input paling lama di area ini (null kalau tidak ada). */
    private suspend fun ambilBerikutnya(area: String): Result<ScanRow?> =
        repo.list(area, limit = 1, status = "IN", sort = "tanggal_input").map { it.firstOrNull() }

    /** Isi kartu "Posisi selanjutnya": barang tertua di area. Dipanggil tiap refresh. */
    private fun loadNext() {
        val area = _state.value.selectedArea
        val seq = ++nextSeq
        viewModelScope.launch {
            if (area == null) {
                _state.update { it.copy(nextRow = null) }
                return@launch
            }
            val next = ambilBerikutnya(area).getOrNull()
            if (seq == nextSeq && _state.value.selectedArea == area) {
                _state.update { it.copy(nextRow = next) }
            }
        }
    }

    /**
     * Tombol i: barang yang di-input paling dulu di area terpilih (status IN, urut tanggal input).
     * API: GET /api/scan?area=..&status=IN&sort=tanggal_input&limit=1
     */
    fun showNextInfo() {
        val area = _state.value.selectedArea ?: return alert(false, "Pilih satu area dulu")
        viewModelScope.launch {
            ambilBerikutnya(area)
                .onSuccess { next ->
                    if (next == null) alert(false, "Tidak ada barang menunggu output di area ini")
                    else _state.update { it.copy(nextInfo = next) }
                }
                .onFailure { e -> alert(false, "Gagal ambil data: ${e.message}") }
        }
    }

    fun closeNextInfo() = _state.update { it.copy(nextInfo = null) }

    /* ------------------------------------------------------------ SCAN */

    fun onQrScanned(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        if (value == lastRaw && now - lastRawAt < DEBOUNCE_MS) return
        lastRaw = value; lastRawAt = now

        val s = _state.value
        if (s.qrChecking || s.isSubmitting) return

        val code = QrCodeParser.parse(value)
            ?: return alert(false, "Format QR salah: $value (contoh benar: 100-10-11-0369)")
        if (code == s.scanned?.qrId) return

        _state.update { it.copy(qrChecking = true) }
        val token = scanToken
        viewModelScope.launch {
            val filterArea = _state.value.selectedArea   // area yang dipilih di Filter Area (null = semua area)
            val result = repo.lookup(code)
            _state.update { it.copy(qrChecking = false) }
            if (token != scanToken) return@launch  // sudah dibatalkan (Batal) saat cek berjalan
            when (result) {
                is LookupResult.NotFound -> alert(false, "QR $code belum pernah di-input.")
                is LookupResult.Failed -> alert(false, result.message)
                is LookupResult.Found ->
                    if (filterArea != null && result.row.area != filterArea) {
                        // Barang dari area lain tidak boleh di-output dari layar area ini
                        alert(false, "QR $code milik ${result.row.namaArea ?: result.row.area}, bukan $filterArea. Pilih area yang sesuai dulu.")
                    } else if (result.row.status == "OUT") {
                        alert(false, "QR $code sudah di-output sebelumnya.")
                    } else {
                        _state.update { it.copy(scanned = result.row, selectedAlasan = null, selectedTidakSesuai = null) }
                        val hari = result.row.lamaStagnasiHari ?: 0
                        val masalah = listOfNotNull(
                            if (result.row.lewatLeadTimeYa) "lewat lead time ($hari hari > ${result.row.leadTimeHari ?: LEAD_TIME_HARI} hari)" else null,
                            result.row.fifo?.seharusnya?.let { "posisi tidak sesuai (seharusnya ambil ${it.qrId} di posisi ${it.posisiWire ?: "-"})" },
                        )
                        if (masalah.isNotEmpty()) {
                            alert(false, "QR $code ${masalah.joinToString(" dan ")}. Pilih alasan, lalu tekan SEND DATA.")
                        } else {
                            alert(true, "QR $code terbaca ($hari hari, masih dalam lead time). Tekan SEND DATA.")
                        }
                    }
            }
        }
    }

    /** Batal: kembali ke tampilan kosong (tanpa QR, tanpa pilihan alasan, Filter Area = Semua Area). */
    fun cancelScan() {
        if (_state.value.isSubmitting) return
        lastRaw = null
        scanToken++
        _state.update { it.copy(scanned = null, selectedAlasan = null, selectedTidakSesuai = null, selectedArea = null, qrChecking = false) }
        refresh()
    }

    /* ------------------------------------------------------------ SIMPAN */

    /**
     * POST /api/scan/output
     * Alasan & tidak_sesuai yang dipilih selalu dikirim. Alasan wajib kalau lewat lead time
     * (backend menolak tanpa alasan), selain itu boleh kosong.
     */
    fun submitOutput() {
        val s = _state.value
        val row = s.scanned ?: return alert(false, "Scan QR dulu")
        if (s.isSubmitting) return
        if (s.perluAlasan && s.selectedAlasan == null) {
            return alert(false, "Lewat lead time: pilih Alasan keluar LeadTime dulu")
        }
        if (s.perluTidakSesuai && s.selectedTidakSesuai == null) {
            return alert(false, "Posisi tidak sesuai: pilih Alasan Tidak Sesuai dulu")
        }
        val alasan = s.selectedAlasan
        val tidakSesuai = if (s.perluTidakSesuai) s.selectedTidakSesuai else null

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            val result = repo.output(row.qrId, alasan?.id, tidakSesuai?.id)
            _state.update { it.copy(isSubmitting = false) }

            result.onSuccess { saved ->
                lastRaw = null
                scanToken++
                // Setelah Send Data: QR & alasan dikosongkan, Filter Area tetap supaya
                // "Posisi selanjutnya" langsung pindah ke barang berikutnya di area yang sama.
                _state.update { st ->
                    st.copy(
                        scanned = null, selectedAlasan = null, selectedTidakSesuai = null,
                        nextRow = st.nextRow?.takeIf { it.qrId != row.qrId },
                    )
                }
                // Pesan diambil dari data yang benar-benar tersimpan di server
                val hari = saved?.lamaStagnasiHari ?: row.lamaStagnasiHari ?: 0
                val info = listOfNotNull(
                    "$hari hari",
                    saved?.alasan?.replace('_', ' '),
                    saved?.tidakSesuai?.replace('_', ' '),
                ).joinToString(" · ")
                alert(true, "${row.qrId} tersimpan sebagai OUTPUT ($info).")
                refresh()
            }.onFailure { e ->
                alert(false, "Gagal simpan output: ${e.message}")
                // mis. hari berganti / lot lain baru masuk -> ambil ulang data QR supaya pilihan yang benar muncul
                if (e.message?.contains("alasan wajib", ignoreCase = true) == true ||
                    e.message?.contains("tidak sesuai", ignoreCase = true) == true) muatUlangScanned(row.qrId)
            }
        }
    }

    /** Ambil ulang data 1 QR dari server (status & lama stagnasi terbaru). */
    private fun muatUlangScanned(qrId: String) {
        viewModelScope.launch {
            val r = repo.lookup(qrId)
            if (r is LookupResult.Found && r.row.status == "IN" && _state.value.scanned?.qrId == qrId) {
                _state.update { it.copy(scanned = r.row) }
            }
        }
    }

    /* --------------------------------------------------------- papan real-time */

    fun refresh() {
        val area = _state.value.selectedArea
        loadNext()
        val seq = ++listSeq
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val list = repo.list(area)
            val pending = repo.pendingCount(area)
            if (seq != listSeq) return@launch   // sudah ada muat ulang yang lebih baru
            if (_state.value.selectedArea == area) {
                _state.update { st ->
                    st.copy(
                        loading = false,
                        items = list.getOrNull() ?: st.items,
                        summary = OutputSummary(pending = pending.getOrNull() ?: st.summary.pending),
                        message = list.exceptionOrNull()?.message,
                    )
                }
            } else {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    /** Banyak event berdekatan -> cukup 1x muat ulang. */
    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(300)
            refresh()
        }
    }

    private fun listenRealtime() {
        viewModelScope.launch {
            ScanEvents.events().collect { ev ->
                when (ev) {
                    ServerEvent.Connected -> {
                        val wasOffline = !_state.value.isLive
                        _state.update { it.copy(isLive = true) }
                        if (wasOffline) scheduleRefresh()
                    }
                    ServerEvent.Disconnected -> _state.update { it.copy(isLive = false) }
                    is ServerEvent.ScanChanged -> onScanChanged(ev)
                    ServerEvent.DataChanged -> {   // data diubah langsung dari DBeaver
                        scheduleRefresh()
                        _state.value.scanned?.let { muatUlangScanned(it.qrId) }
                    }
                }
            }
        }
    }

    private fun onScanChanged(ev: ServerEvent.ScanChanged) {
        val s = _state.value
        // QR yang sedang tampil ternyata sudah di-output dari HP atau dashboard lain
        if (ev.type == "scan_output" && s.scanned?.qrId == ev.qrId && !s.isSubmitting) {
            _state.update { it.copy(scanned = null) }
            alert(false, "QR ${ev.qrId} baru saja di-output dari perangkat lain.")
        }
        if (s.selectedArea == null || ev.area == s.selectedArea) scheduleRefresh()
    }

    private fun alert(ok: Boolean, text: String) {
        _state.update { it.copy(alert = UiAlert(SystemClock.elapsedRealtimeNanos(), ok, text)) }
    }

    companion object {
        const val ALL_AREAS = "Semua Area"
        /** Cadangan saja: lead time asli per area dari server (tabel Batas_Lead_Time) */
        const val LEAD_TIME_HARI = 4
        private const val DEBOUNCE_MS = 2_500L
    }
}

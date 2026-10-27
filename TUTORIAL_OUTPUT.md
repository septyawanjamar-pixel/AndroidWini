# Tutorial: Input POKA YOKE + Output Scanner (Real-time)

Dokumen ini menjelaskan fitur baru di aplikasi **QR Scanner**:

1. Layar Input **POKA YOKE**: urutan wajib Area → Scan QR → Grade → Posisi Wire → Kirim. Langkah berikutnya terkunci sampai langkah sebelumnya benar (lihat **bagian 0**).
2. Layar Output **POKA YOKE**: Area → Scan QR → **Verifikasi buta posisi wire**. Tampilan identik dengan Input (lihat **bagian 0B**).
3. Data **real-time**: begitu HP Input menyimpan data, layar Output di HP mana pun langsung ter-update tanpa refresh.

---

## 0. POKA YOKE di Layar Input

*Poka yoke* = desain yang membuat kesalahan **tidak mungkin terjadi**, atau langsung ketahuan saat terjadi. Di layar Input, prinsip itu diterapkan seperti ini.

### 0.1 Urutan wajib (tidak bisa dilompati)

```
 ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
 │ 1. AREA  │──►│ 2. SCAN  │──►│ 3. GRADE │──►│ 4. POSISI│──►│ 5. KIRIM │
 └──────────┘   └──────────┘   └──────────┘   └──────────┘   └──────────┘
   pilih          QR dicek        pilih          posisi         ringkasan +
   area           ke server       grade          kosong saja    Send Data
```

- Di atas layar ada **indikator langkah**: hijau ✓ = selesai, biru = sedang dikerjakan, abu-abu = terkunci.
- Kartu langkah yang terkunci tampil **redup + ikon gembok** dan **tidak bisa disentuh**.
- Layar **menggulir otomatis** ke langkah yang sedang aktif.
- Tombol **Send Data** di bawah hanya aktif saat kelima langkah lengkap.

### 0.2 Daftar pengaman

| # | Pengaman | Kesalahan yang dicegah |
|---|---|---|
| 1 | **Tidak ada nilai bawaan** untuk Area, Grade, dan Posisi. Semua harus dipilih sendiri. | Operator lupa mengganti pilihan bawaan (misalnya tetap "CUROD" / "Grade S" / "1") |
| 2 | **Kamera hanya menyala di langkah 2.** | QR lain tertangkap kamera tanpa sengaja saat sedang memilih grade/posisi |
| 3 | **Format QR dicek.** Label JSON dari backend (`{"spool_id": ...}`) diambil `spool_id`-nya; teks biasa hanya boleh huruf/angka/`-_./` (3–64 karakter). | Scan barcode/URL yang bukan label produk |
| 4 | **QR dicek ke server sebelum diterima** (*fail-closed*: kalau server tidak bisa dihubungi, QR **tidak** diterima). | Input QR yang masih menunggu output (data ganda) |
| 5 | **QR terkunci** setelah valid. QR lain yang tertangkap kamera tidak mengganti diam-diam; harus tekan **Scan Ulang**. | QR tertukar di tengah proses |
| 6 | **Posisi wire yang masih terisi** QR lain (belum di-output) di area yang sama tampil **merah "Terisi"** dan ditolak. | 2 produk tercatat di 1 posisi |
| 7 | **Reset berantai**: ubah Area → Scan, Grade, Posisi diulang. Scan Ulang → Grade, Posisi diulang. Ubah Grade → Posisi diulang. | Data lama "nyangkut" setelah langkah sebelumnya diubah |
| 8 | **Ringkasan wajib** (langkah 5) sebelum kirim, untuk dicocokkan dengan label fisik. | Salah kirim tanpa dicek |
| 9 | **Setelah terkirim**, Area tetap tetapi QR, Grade, dan Posisi **dikosongkan**. | Grade/posisi produk sebelumnya terbawa ke produk berikutnya |
| 10 | **Tanda kesalahan multi-indra**: banner merah + kartu bergoyang + HP bergetar + bunyi error. Benar = banner hijau + bunyi "ting". | Operator tidak sadar ada error di lingkungan pabrik yang bising |
| 11 | **Validasi ulang di server**: area, grade, posisi 1–16, format QR, QR ganda, posisi terisi. Dikunci per-QR dan per-posisi di database. | Data salah masuk lewat jalur selain aplikasi, atau 2 HP mengirim bersamaan |

### 0.3 Mengatur aturan "posisi terisi"

Aturan nomor 6 menganggap **1 posisi wire = 1 slot fisik** yang hanya boleh ditempati 1 produk sampai produk itu di-output. Kalau di lapangan satu posisi boleh dipakai bersama:

- Android: `viewmodel\ScannerViewModel.kt` → `const val BLOCK_OCCUPIED_WIRE = false`
- Backend: tambahkan `BLOCK_OCCUPIED_WIRE=false` di file `.env`, lalu restart server.

### 0.4 Menambah atau mengubah daftar Area / Grade

Daftar harus **sama persis** di 2 tempat. Kalau tidak, server akan menolak:

- Android: `viewmodel\ScannerViewModel.kt` → `areaOptions`, `gradeOptions`
- Backend: `src\routes\mobileRoutes.js` → `AREA_OPTIONS`, `GRADE_OPTIONS`

---

## 0B. POKA YOKE di Layar Output

### 0B.1 Urutan wajib

```
 ┌──────────┐   ┌──────────┐   ┌──────────────────────┐
 │ 1. AREA  │──►│ 2. SCAN  │──►│ 3. VERIFIKASI POSISI │──►  tersimpan otomatis bila cocok
 └──────────┘   └──────────┘   └──────────────────────┘
   tanpa          QR dicek        operator menekan posisi
   default        ke server       TEMPAT barang diambil
```

### 0B.2 Verifikasi buta (inti poka yoke Output)

Dialog "Konfirmasi Output" yang lama hanya menampilkan data lalu meminta operator menekan "Ya". Lama-lama operator menekannya tanpa membaca. Itu cuma **peringatan**, bukan poka yoke.

Sekarang:

1. Setelah QR valid, layar menampilkan QR, Grade, dan jam/operator input, **tetapi tidak menampilkan posisi wire**.
2. Operator melihat barang secara fisik, lalu menekan nomor posisi (grid 1–16) tempat barang itu diambil.
3. Server mencocokkan dengan posisi saat INPUT:
   - **Cocok** → OUTPUT tersimpan, banner hijau + bunyi, kembali ke langkah 2 untuk QR berikutnya.
   - **Tidak cocok** → ditolak, banner merah + getar + bunyi error. Tombol posisi itu ditandai merah **"Salah"**. Posisi yang benar **tidak dibocorkan**, supaya operator mengecek fisik barang, bukan menyalin angka dari layar.
   - **Salah 2 kali** → QR **dibatalkan**: operator harus cek fisik / lapor leader, lalu scan ulang.
4. Setiap salah posisi dicatat di log server (`[POKA YOKE] Posisi output tidak cocok`) dan disiarkan sebagai event Socket.io `output_mismatch`, yang bisa dipakai Web Monitor atau laporan leader.

Supaya tetap "buta", posisi wire barang yang **masih menunggu** juga disembunyikan di daftar (`Posisi ••`). Posisi baru tampil setelah barang berstatus OUTPUT.

### 0B.3 Daftar pengaman Output

| # | Pengaman | Kesalahan yang dicegah |
|---|---|---|
| 1 | Area **tanpa nilai bawaan** | Lupa ganti area, lalu bingung kenapa semua QR ditolak |
| 2 | Kamera **hanya menyala di langkah 2** | QR lain ter-scan saat sedang verifikasi |
| 3 | Format QR dicek (aturan sama dengan Input, `QrCodeParser.kt`) | Scan barcode/URL yang bukan label produk |
| 4 | QR belum pernah di-input / sudah di-output / beda area → ditolak | Output barang yang salah atau ganda |
| 5 | QR terkunci saat verifikasi. QR lain tidak bisa menggantikan; harus **Scan Ulang** | QR tertukar di tengah proses |
| 6 | **Verifikasi buta posisi wire** (maks. 2 percobaan) | Barang tertukar posisi / salah ambil barang |
| 7 | QR yang sedang diverifikasi ternyata di-output HP lain → otomatis dibatalkan di HP ini | Dua operator mengeluarkan barang yang sama |
| 8 | Bunyi + getar + banner + kartu bergoyang (sama dengan Input) | Error tidak disadari di area bising |
| 9 | Server wajib menerima posisi & mencocokkannya; dikunci per-QR di database | Data output tanpa verifikasi, atau 2 HP bersamaan |

### 0B.4 Mengatur verifikasi

- Jumlah percobaan: `viewmodel\OutputViewModel.kt` → `MAX_WRONG_ATTEMPTS = 2`
- Mematikan verifikasi posisi (misalnya posisi tidak terlihat di lapangan): tambahkan `VERIFY_OUTPUT_WIRE=false` di `.env` backend lalu restart. **Catatan:** aplikasi tetap menampilkan grid posisi; dengan setelan ini server menerima posisi apa pun. Kalau mau dimatikan permanen, kabari agar langkah 3 di aplikasi diganti tombol konfirmasi biasa.

---

## 1. Gambaran Alur Sistem

```
 HP INPUT                                   BACKEND (production-scan-backend)              HP OUTPUT
 ─────────                                  ─────────────────────────────────              ─────────
 1. Pilih Area
 2. Scan QR (dicek ke server)
 3. Pilih Grade, Posisi Wire  ──►  Send Data
                 POST /scan  ─────────────► simpan ScanLog (scanType = INPUT)
                                            │
                                            └─ Socket.io emit "scan_update" ─────────────► list Output bertambah
                                                                                            "MENUNGGU" (+1)
                                                                                            4. Pilih area yang sama
                                                                                            5. Scan QR yang sama
                                            GET /scan/lookup/:qr ◄───────────────────────── cek QR
                                            balas: QR valid ───────────────────────────────► langkah Verifikasi
                                            POST /scan/output ◄──────────────────────────── 6. tekan posisi tempat
                                              + wirePosition (pilihan operator)                barang diambil
                                            cocokkan dgn INPUT → simpan ScanLog OUTPUT
                                            (tidak cocok → 422 WIRE_MISMATCH)
                                            └─ Socket.io emit "scan_update" ─────────────► status jadi "OUTPUT" ✔
```

**Database tidak diubah.** Semua data memakai tabel `ScanLog` yang sudah ada:

| Kejadian | Baris yang dibuat di `ScanLog` |
|---|---|
| Send Data di Input | `scanType = INPUT`, area, grade, wirePosition, user (operator) |
| Verifikasi Output berhasil | `scanType = OUTPUT`, area/grade/wirePosition **disalin dari INPUT** |

Status sebuah QR = log terakhirnya. Kalau terakhir `INPUT` → **menunggu output**. Kalau terakhir `OUTPUT` → **selesai**.

### Aturan validasi yang otomatis berjalan

| Kasus | Hasil |
|---|---|
| Send Data sebelum 5 langkah poka yoke lengkap | Tombol Send Data nonaktif |
| QR di-input lagi padahal belum di-output | Ditolak: *"QR sudah di-input dan belum di-output"* |
| Scan Output untuk QR yang belum pernah di-input | Ditolak: *"QR … belum pernah di-input"* |
| Scan Output di area yang berbeda dengan Input | Ditolak: *"Area tidak cocok. QR ini milik area …"* |
| QR yang sudah di-output di-scan Output lagi | Ditolak: *"QR … sudah di-output sebelumnya"* |
| 2 HP men-scan Output QR yang sama di detik yang sama | Hanya 1 yang tersimpan (dikunci di database) |
| Kamera membaca QR yang sama berkali-kali | Dianggap 1 scan (jeda 2,5 detik) |
| QR sudah OUTPUT lalu di-input lagi | Boleh: dianggap siklus produksi baru |

---

## 2. Daftar File

### Backend — `Documents\production-scan-backend`

| File | Status | Isi |
|---|---|---|
| `src\routes\mobileRoutes.js` | **BARU** | Semua endpoint untuk Android (input, lookup, output, board) + broadcast Socket.io |
| `src\app.js` | **DIUBAH** (2 baris) | Mendaftarkan `mobileRoutes` |
| `_backup_sebelum_output\app.js` | backup | Salinan `app.js` sebelum diubah |

### Android — `Downloads\qr-scanner`

| File (di `app\src\main\java\com\example\`) | Status | Isi |
|---|---|---|
| `OutputScanScreen.kt` | **BARU** | Tampilan layar Output poka yoke (3 langkah) |
| `viewmodel\OutputViewModel.kt` | **BARU** | Logika Output: area, scan, verifikasi buta posisi, real-time |
| `PokaYokeComponents.kt` | **BARU** | Komponen bersama Input & Output: indikator langkah, kartu langkah, grid tombol, kamera, banner, bunyi/getar |
| `viewmodel\QrCodeParser.kt` | **BARU** | Aturan format QR yang sama untuk Input & Output |
| `network\OutputApi.kt` | **BARU** | Model data + Retrofit untuk endpoint Output |
| `network\ScanSocket.kt` | **BARU** | Koneksi real-time Socket.io |
| `InputPokaYokeScreen.kt` | **BARU** | Tampilan layar Input poka yoke (5 langkah) |
| `HomeScreen.kt` | **DIUBAH** | Tab Input memakai layar poka yoke, tab Output memakai layar baru. `InputScreen` / `OutputScreen` lama dihapus (ada di backup). |
| `viewmodel\ScannerViewModel.kt` | **DIUBAH** | Ditulis ulang jadi mesin langkah poka yoke (urutan, reset berantai, validasi QR, posisi terisi) |
| `network\ApiModels.kt` | **DIUBAH** (1 baris) | Field `operator` di `ScanRequest` |
| `network\ScanRepository.kt` | **DIUBAH** | Kirim `operator`, tampilkan pesan error dari server dengan jelas |
| `app\build.gradle.kts` | **DIUBAH** | Tambah library `socket.io-client` |
| `_backup_sebelum_output\...` | backup | Salinan file sebelum fitur Output |
| `_backup_sebelum_pokayoke\...` | backup | Salinan file sebelum poka yoke Input |
| `_backup_sebelum_pokayoke_output\...` | backup | Salinan file sebelum poka yoke Output |

---

## 3. Langkah 1 — Jalankan Backend

1. Buka terminal (PowerShell / CMD) di folder backend:
   ```powershell
   cd C:\Users\septy\Documents\production-scan-backend
   ```
2. Pastikan PostgreSQL menyala dan `DATABASE_URL` di file `.env` benar.
3. Tidak perlu `npm install` baru dan tidak perlu migrasi Prisma. Semua library (`express`, `socket.io`, `@prisma/client`) sudah ada.
4. Jalankan server:
   ```powershell
   npm run dev
   ```
   Harus muncul: `Server Backend aktif pada port 5111` (atau port sesuai `PORT` di `.env`).

### Tes backend tanpa HP (opsional tapi disarankan)

Buka **PowerShell kedua**, jalankan satu per satu:

```powershell
# 1) Simpan INPUT
Invoke-RestMethod -Method Post -Uri http://localhost:5111/scan -ContentType "application/json" `
  -Body '{"qr_id":"TES-001","area":"CUROD","grade":"Grade S","posisi_wire":3,"tanggal":"2026-10-12"}'
# -> ok: True, duplicate: False

# 2) Coba INPUT yang sama lagi (harus ditolak)
Invoke-RestMethod -Method Post -Uri http://localhost:5111/scan -ContentType "application/json" `
  -Body '{"qr_id":"TES-001","area":"CUROD","grade":"Grade S","posisi_wire":3}'
# -> duplicate: True

# 3) Lihat papan Output area CUROD
Invoke-RestMethod http://localhost:5111/scan/board?area=CUROD | ConvertTo-Json -Depth 5
# -> summary.pending = 1, TES-001 status "IN"

# 4) Cek QR sebelum output
Invoke-RestMethod http://localhost:5111/scan/lookup/TES-001
# -> grade "Grade S", wirePosition "3"

# 5) Simpan OUTPUT dengan posisi SALAH (harus ditolak 422, posisi benar tidak dibocorkan)
Invoke-RestMethod -Method Post -Uri http://localhost:5111/scan/output -ContentType "application/json" `
  -Body '{"qrCode":"TES-001","area":"CUROD","wirePosition":"5"}'
# -> error 422: "Posisi wire tidak cocok dengan data input..."

# 6) Simpan OUTPUT dengan posisi BENAR (3, sesuai langkah 1)
Invoke-RestMethod -Method Post -Uri http://localhost:5111/scan/output -ContentType "application/json" `
  -Body '{"qrCode":"TES-001","area":"CUROD","wirePosition":"3"}'
# -> success: True
```

Kalau langkah 6 dijalankan lagi, PowerShell akan menampilkan error **409** ("sudah di-output"). Itu artinya validasi bekerja.

---

## 4. Langkah 2 — Siapkan Android Studio

1. Buka proyek `Downloads\qr-scanner` di Android Studio.
2. Akan muncul banner **"Gradle files have changed"** → klik **Sync Now**.
   Ini mengunduh library `io.socket:socket.io-client:2.1.1` (perlu internet).
3. Cek alamat server di `app\src\main\java\com\example\network\ApiConfig.kt`:
   ```kotlin
   const val BASE_URL = "http://192.17.102.133:5111/"
   ```
   - **Emulator**: boleh pakai `http://10.0.2.2:5111/` (mengarah ke localhost laptop).
   - **HP asli**: pakai IP laptop. Cari dengan perintah `ipconfig` di CMD, lihat **IPv4 Address** pada adapter Wi-Fi (biasanya `192.168.x.x`). HP dan laptop harus di Wi-Fi yang sama.
   - Pastikan **diakhiri garis miring** `/`.
   - Alamat yang sama dipakai untuk REST **dan** real-time (Socket.io), jadi cukup ubah di satu tempat ini.
4. Izinkan port 5111 di **Windows Firewall** (kalau HP asli tidak bisa konek):
   ```powershell
   # jalankan PowerShell sebagai Administrator
   New-NetFirewallRule -DisplayName "Node 5111" -Direction Inbound -Protocol TCP -LocalPort 5111 -Action Allow
   ```
5. Klik **Run ▶**.

> `usesCleartextTraffic="true"` sudah ada di `AndroidManifest.xml`, jadi koneksi `http://` (tanpa https) diizinkan.

---

## 5. Langkah 3 — Cara Pakai di Aplikasi

### A. Layar Input (poka yoke)

1. **Langkah 1 — Area**: tekan salah satu tombol besar (CUROD / A-Line / Vertikal / Horizontal).
   Kartu menyusut jadi `✓ CUROD` dengan tombol **Ubah**, lalu kamera menyala.
2. **Langkah 2 — Scan QR**: arahkan kamera ke label. Di emulator, ketuk kotak kamera atau tombol **Pindai / Scan QR (Simulasi)**.
   - Muncul "Memverifikasi QR ke server…".
   - Valid → banner hijau + bunyi, kamera mati, lanjut ke Grade.
   - Tidak valid (format salah / masih menunggu output / server mati) → banner merah + getar + bunyi error; tetap di langkah 2.
3. **Langkah 3 — Grade**: tekan salah satu dari 6 tombol (S, A, B, C, D, SELECTED).
4. **Langkah 4 — Posisi Wire**: grid 4×4 (1–16). Posisi merah bertuliskan **Terisi** sedang dipakai QR lain di area ini dan akan ditolak.
5. **Langkah 5 — Cek & Kirim**: cocokkan ringkasan (Area, QR, Grade, Posisi) dengan label fisik, lalu tekan **Send Data**.
   - Berhasil → banner hijau *"Tersimpan: …"*, layar kembali ke **langkah 2** dengan area yang sama, siap scan QR berikutnya.
   - Gagal (misalnya posisi baru saja diisi HP lain) → banner merah dengan alasannya; data **tidak** tersimpan.
6. **Cancel Scan** di bawah = buang QR, grade, dan posisi (area tetap). Tombol **Ubah** di tiap kartu = kembali ke langkah itu (langkah sesudahnya ikut direset).

### B. Layar Output (poka yoke)

1. **Langkah 1 — Area**: tekan area tempat barang akan di-output. Sebelum area dipilih, daftar dan angka di bawah menampilkan ringkasan **semua area**.
2. **Langkah 2 — Scan QR**: arahkan kamera ke label. Di emulator, ketuk kotak kamera atau tombol **Pindai / Scan QR Output (Simulasi)** (mengambil QR pertama yang menunggu di area itu).
   - Ditolak bila QR belum pernah di-input, sudah di-output, atau milik area lain.
3. **Langkah 3 — Verifikasi Posisi**: lihat barangnya, lalu tekan nomor posisi **tempat barang diambil**.
   - Titik hijau "Sisa percobaan" menunjukkan kesempatan yang tersisa.
   - Benar → tersimpan otomatis, kembali ke langkah 2.
   - Salah → tombol itu jadi merah "Salah", coba lagi setelah cek fisik. Salah 2x → QR dibatalkan.
   - **Scan Ulang** di kartu langkah 2 = batalkan QR ini.

Bagian bawah layar:

| Bagian | Fungsi |
|---|---|
| `● LIVE` di samping tanggal | Hijau berkedip = tersambung real-time. `Offline` = koneksi putus (otomatis mencoba lagi). |
| **Menunggu Output / Output Hari Ini** | Angka ringkasan, berubah real-time |
| **Semua / Menunggu / Selesai** + 🔄 | Filter daftar dan tombol muat ulang |
| **Daftar QR** | Kuning `MENUNGGU` (posisi disembunyikan `••`), hijau `OUTPUT` (posisi tampil). |

> **Tips uji di emulator:** karena posisi disembunyikan, lihat posisi yang kamu pilih saat Input (atau cek `GET /scan/lookup/<QR>` di browser laptop) lalu tekan posisi itu di layar Output.

Badge angka di tab **Output** pada menu bawah = jumlah QR yang menunggu output, juga real-time.

---

## 6. Cara Menguji Real-time

**Cara 1 — 2 perangkat (paling meyakinkan)**
1. Jalankan app di emulator **dan** HP asli (atau 2 emulator), keduanya ke server yang sama.
2. Perangkat A: buka tab **Output**, area CUROD.
3. Perangkat B: tab **Input** → CUROD → scan → pilih grade → pilih posisi → Send Data.
4. Di perangkat A, angka **Menunggu Output** bertambah dan QR baru muncul di paling atas **tanpa refresh**.

**Cara 2 — 1 perangkat + PowerShell**
1. Buka tab **Output** di emulator, area CUROD.
2. Jalankan perintah *Tes backend* nomor 1 di atas dengan `qr_id` baru (misalnya `TES-002`).
3. QR `TES-002` langsung muncul di emulator.

---

## 7. Cara Kerja Kode (singkat)

### Real-time (Socket.io)
- Backend sudah punya Socket.io di `app.js` (dulu untuk Web Monitor).
- `mobileRoutes.js` memanggil `req.io.emit('scan_update', data)` setiap ada INPUT/OUTPUT.
- `ScanSocket.kt` membuka koneksi ke alamat yang sama dengan `ApiConfig.BASE_URL` dan mengubah event menjadi `Flow<SocketEvent>`.
- `OutputViewModel.listenRealtime()` mengumpulkan Flow itu:
  - `Connected` → tampil `LIVE`, data diambil ulang sekali (supaya data yang terlewat saat offline ikut masuk).
  - `Update` → `applyRealtime()` menyisipkan/memperbarui 1 baris dan menyesuaikan angka ringkasan **tanpa** memanggil server lagi.
  - `Disconnected` → tampil `Offline`. Library otomatis reconnect (1 s sampai 10 s).
- Event lama `new_scan_event` tetap dikirim, jadi Web Monitor yang sudah ada tidak rusak.

### Scan & verifikasi Output
1. `RealCameraPreview` memanggil `onQrScanned(qr)` berkali-kali per detik → disaring jadi 1 scan per 2,5 detik.
2. `GET /scan/lookup/{qr}` → kalau valid dan areanya sama, lanjut ke langkah verifikasi (posisi tidak ditampilkan).
3. Operator menekan posisi → `POST /scan/output` dengan `wirePosition`. Di backend dijalankan dalam **transaksi** dengan `pg_advisory_xact_lock`: cek status, cek area, cocokkan posisi, lalu simpan. 2 HP yang scan QR yang sama bersamaan tidak membuat 2 OUTPUT.
4. Balasan `422` dengan `code: "WIRE_MISMATCH"` → `OutputViewModel.onWrongPosition()` menghitung percobaan.

### Operator
Android login lewat Firebase, bukan JWT backend, sedangkan `ScanLog.userId` wajib diisi. Maka nama/email operator yang login (`displayName`) dikirim di field `operator`, dan backend membuat/memakai `User` dengan email tersebut. Password user ini diisi string acak yang bukan hash bcrypt, jadi tidak bisa dipakai login. Kalau `displayName` bukan email, data dicatat atas nama `mobile-operator@local`.

---

## 8. Troubleshooting

| Gejala | Penyebab & solusi |
|---|---|
| Layar Output selalu `Offline` | IP/port di `ApiConfig.BASE_URL` salah, server belum jalan, atau firewall memblokir port 5000. Tes buka `http://IP-LAPTOP:5000/scan/board` di browser HP; harus muncul JSON. |
| Snackbar *"Server tidak terjangkau: failed to connect"* | Sama seperti di atas. Untuk emulator coba `http://10.0.2.2:5000/`. |
| Send Data abu-abu / tidak bisa ditekan | Masih ada langkah poka yoke yang belum selesai. Lihat indikator langkah di atas layar. |
| QR selalu ditolak *"Tidak bisa verifikasi QR ke server"* | Sengaja (*fail-closed*): server harus bisa dihubungi. Cek IP / server / firewall. |
| QR ditolak *"Format QR tidak dikenal"* | Isi QR bukan label produk. Hanya JSON label backend (`spool_id`) atau kode huruf/angka/`-_./` 3–64 karakter yang diterima. |
| Semua posisi wire merah | Banyak QR di area itu belum di-output. Output dulu, atau matikan aturan (lihat bagian 0.3). |
| Server menolak *"grade harus salah satu dari…"* | Daftar grade di Android dan backend berbeda. Samakan (bagian 0.4). |
| Tidak ada bunyi | Volume notifikasi HP sedang 0 / mode senyap. |
| Output selalu *"Posisi … SALAH"* padahal yakin benar | Posisi saat Input mungkin salah dipilih. Lapor leader; cek data di `GET /scan/lookup/<QR>`. Kejadiannya tercatat di log server. |
| Output ditolak *"Pilih posisi wire tempat barang diambil"* (400) | Aplikasi versi lama (tanpa verifikasi) masih dipakai. Update aplikasi di semua HP. |
| Di layar Output, posisi barang menunggu tampil `••` | Disengaja (verifikasi buta). Posisi tampil setelah barang OUTPUT. |
| *"QR … sudah di-input dan belum di-output"* | QR itu masih menunggu di Output. Output dulu, baru boleh di-input lagi. |
| *"Area tidak cocok"* saat Output | Ganti chip area di layar Output sesuai area waktu Input. |
| Gradle Sync gagal di `socket.io-client` | Cek internet, lalu **File → Sync Project with Gradle Files**. Pastikan baris `exclude(group = "org.json", ...)` ikut tersalin. |
| Error build `Duplicate class org.json...` | Baris `exclude` di `build.gradle.kts` hilang. Tambahkan lagi. |
| Error backend `Unique constraint failed on the fields: (email)` | Dua scan pertama dari operator baru terjadi bersamaan. Kirim ulang; scan berikutnya normal. |
| Daftar Output kosong padahal sudah input | Chip area di Output berbeda dengan area Input, atau tekan tombol 🔄. |
| `GET /scans` di `ScanApi.kt` | Format balasannya sekarang mengikuti `ScanLog`. Fungsi `getScans()` tidak dipakai di mana pun, jadi aman. Jangan dipanggil dengan model `ScanRecord` lama. |

---

## 9. Membatalkan Perubahan (rollback)

Ada 2 titik backup. Masing-masing berisi `LOKASI_ASLI.txt`:

| Ingin kembali ke… | Folder backup |
|---|---|
| Versi **Input poka yoke, Output tanpa verifikasi** (dialog Konfirmasi Output) | `_backup_sebelum_pokayoke_output\` (Android & backend) |
| Versi **dengan Output, tanpa poka yoke** (tombol "Konfirmasi Pilihan") | `_backup_sebelum_pokayoke\` (Android & backend) |
| Versi **paling awal** (sebelum fitur Output) | `_backup_sebelum_output\` (Android & backend) |

Untuk kembali ke versi paling awal:

1. Salin file dari folder backup ke lokasi aslinya. Daftar lokasinya ada di `LOKASI_ASLI.txt` di dalam folder backup.
2. Hapus 4 file baru Android (`OutputScanScreen.kt`, `OutputViewModel.kt`, `OutputApi.kt`, `ScanSocket.kt`) dan `mobileRoutes.js` di backend.
3. Sync Gradle lagi.

---

## 10. Ide Pengembangan Berikutnya

- **Web Monitor**: folder `web-monitor` di backend masih kosong. Cukup dengarkan event `scan_update` yang sama untuk membuat dashboard layar besar di lantai produksi.
- **Auth JWT untuk mobile**: saat ini endpoint `/scan*` tidak memakai token. Untuk produksi, tambahkan `authMiddleware` dan kirim token dari aplikasi.
- **Laporan harian**: query `ScanLog` per area per hari: jumlah INPUT, OUTPUT, dan selisih (barang yang masih tertahan).
- **Mode offline**: simpan scan di Room (`androidx.room` sudah ada di dependency) saat server mati, lalu kirim otomatis saat online.

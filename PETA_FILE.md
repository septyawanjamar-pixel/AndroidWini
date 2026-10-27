# PETA FILE — QR Scanner (urut dari buka aplikasi sampai Output)

Semua path di bawah ada di: `app/src/main/java/com/example/`

Pola tiap layar selalu sama:
- **TAMPILAN** (Screen) = apa yang dilihat & ditekan operator
- **OTAK** (ViewModel) = data pilihan, aturan, logika tombol
- **DATA / SERVER** = penyimpanan & komunikasi ke Firebase / Express

---

## 0. APLIKASI DIBUKA
| Peran | File | Isi |
|---|---|---|
| Titik awal | `MainActivity.kt` | Pasang tema, panggil `AppNavigation()` |
| Pengatur layar | `AppNavigation.kt` | Rute: `login` → `register` → `home`. Sudah login? langsung ke `home` |
| Tema warna & huruf | `ui/theme/Color.kt`, `Theme.kt`, `Type.kt` | Warna utama, font aplikasi |

---

## 1. LAYAR LOGIN
| Peran | File | Isi |
|---|---|---|
| TAMPILAN | `LoginScreen.kt` | Form username + password, "Ingat Saya", tombol Login & ke Register |
| Komponen form | `AuthComponents.kt` | `AuthScaffold`, `AuthTextField`, `LoadingButton`, `ErrorBanner` (dipakai Login & Register) |
| OTAK | `AuthViewModel.kt` | `login()`, simpan nama operator (`displayName`), kirim event pindah layar |
| Aturan isian | `AuthValidator.kt` | Syarat username/password (panjang min/max, karakter boleh) |
| Bentuk data layar | `AuthUiState.kt` | `LoginUiState`, `RegisterUiState`, `AuthEvent` |
| SERVER | `AuthRepository.kt` | Login/daftar ke **Firebase Auth + Firestore**, terjemahan pesan error |
| Simpan "Ingat Saya" | `SessionStore.kt` | Penyimpanan terenkripsi di HP (password tidak disimpan) |

## 2. LAYAR REGISTER
| Peran | File | Isi |
|---|---|---|
| TAMPILAN | `RegisterScreen.kt` | Form username, password, konfirmasi |
| OTAK / ATURAN / SERVER | sama dengan Login | `AuthViewModel.register()`, `AuthValidator`, `AuthRepository.register()` |

---

## 3. HOME (kerangka setelah login)
| Peran | File | Isi |
|---|---|---|
| TAMPILAN | `HomeScreen.kt` | Judul atas + nama operator + Logout, tombol bawah (**Reset / Send Data** di Input, **Kembali ke Input** di Output), tab **Input / Output** + badge jumlah menunggu |

Home hanya "bingkai". Isi tengahnya = layar Input atau Output di bawah.

---

## 4. LAYAR INPUT
| Peran | File | Isi |
|---|---|---|
| TAMPILAN | `InputScreen.kt` | Kartu: Tanggal, **Area** (dropdown), **Scan QR**, **Grade** (dropdown), **Posisi Wire** (dropdown), Ringkasan |
| OTAK | `viewmodel/ScannerViewModel.kt` | `loadMasters()` (Area & Grade dari server), `wirePositionOptions`, `InputState`, `canSend`, `onQrScanned()`, `sendData()`, `reset()` |
| Aturan format QR | `viewmodel/QrCodeParser.kt` | Pola QR yang diterima (dipakai juga oleh Output) |
| Bentuk data JSON | `network/ApiModels.kt` | `AreaDto`, `GradeDto`, `ScanRow`, `InputRequest`, `OutputRequest` |
| Daftar endpoint | `network/ScanApi.kt` | `GET areas`, `GET grades`, `POST scan/input`, `POST scan/output`, `GET scan/{qr}`, `GET scan` |
| Pemanggil server | `network/ScanRepository.kt` | `areas()`, `grades()`, `input()`, `output()`, `lookup()`, `list()`, `pendingCount()`, `outputTodayCount()` |
| Koneksi HTTP | `network/RetrofitClient.kt` | Retrofit + baca pesan error backend |

Alur: pilih Area → scan QR → pilih Grade → pilih Posisi → **Send Data** → `POST /api/scan/input`

---

## 5. LAYAR OUTPUT
| Peran | File | Isi |
|---|---|---|
| TAMPILAN | `OutputScanScreen.kt` | Tanggal + LIVE, **Filter Area** (dropdown), Scan QR, kartu **Data QR** + Simpan Output, angka Menunggu/Output Hari Ini, daftar |
| OTAK | `viewmodel/OutputViewModel.kt` | `ALL_AREAS`, `onQrScanned()`, `submitOutput()`, `refresh()`, update real-time |
| Real-time | `network/ScanEvents.kt` | SSE `GET /api/events` (event `scan_input`, `scan_output`, `scan_delete`) |

Alur: (filter area) → scan QR → `GET /api/scan/{qr}` → tampil data → **Simpan Output** → `POST /api/scan/output`

---

## 6. DIPAKAI BERSAMA
| File | Isi |
|---|---|
| `ScanComponents.kt` | `DropdownSelector`, `ScannerBox` (kotak kamera), `SectionCard`, `SummaryRow`, `DateRow`, `AutoHideAlert`, izin kamera, warna `OkGreen/ErrRed/Amber/MutedGrey` |
| `camera/RealCameraPreview.kt` | Kamera + pembaca QR (CameraX + ML Kit) |
| `network/ApiConfig.kt` | **Alamat server** `BASE_URL` (+ `/api/`) — dipakai semua panggilan & real-time |

---

## MAU UBAH APA → BUKA FILE MANA
| Mau ubah | File | Yang dicari |
|---|---|---|
| Alamat / IP server | `network/ApiConfig.kt` | `BASE_URL` |
| Daftar **Area** | **DBeaver** `aWan_data.db` tabel `Area` | tidak perlu ubah aplikasi |
| Daftar **Grade** | **DBeaver** `aWan_data.db` tabel `Grade` | tidak perlu ubah aplikasi |
| Jumlah **Posisi Wire** | `viewmodel/ScannerViewModel.kt` | `wirePositionOptions = (1..16)` |
| Pola / format **QR** | `viewmodel/QrCodeParser.kt` **dan** `C:\QR_Scanner_DB\server.js` (`normalizeQr`) + CHECK di `schema.sql` | `FULL`, `LABEL` |
| Syarat username/password | `AuthValidator.kt` | `username()`, `password()` |
| Tulisan / urutan kartu Input | `InputScreen.kt` | `SectionCard(title = ...)` |
| Tulisan / urutan kartu Output | `OutputScanScreen.kt` | `SectionCard(title = ...)` |
| Tombol bawah / tab | `HomeScreen.kt` | `bottomBar` |
| Tampilan dropdown / kamera | `ScanComponents.kt` | `DropdownSelector`, `ScannerBox` |
| Tambah kolom data baru (mis. Shift) | `schema.sql` + `server.js` (`/api/scan/input`) → `ApiModels.kt` → `ScanRepository.kt` → `ScannerViewModel.kt` → `InputScreen.kt` | urut dari server ke tampilan |
| Warna tema | `ui/theme/Color.kt` | |

---

## BACKEND (VS Code) — `C:\QR_Scanner_DB\`
Database: `C:/sqlite/aWan_data.db` (file yang sama dengan DBeaver)

| File | Isi |
|---|---|
| `server.js` | Semua endpoint yang dipakai aplikasi: `GET /api/areas`, `GET /api/grades`, `POST /api/scan/input`, `POST /api/scan/output`, `GET /api/scan`, `GET /api/scan/:qr_id`, real-time `GET /api/events` |
| `Routes/area.js` | CRUD tabel **Area** (`/api/area`) |
| `Routes/grade.js` | CRUD tabel **Grade** (`/api/grade`) |
| `Routes/alasan.js` | CRUD tabel **Alasan** (`/api/alasan`) |
| `Routes/tidak_sesuai.js` | CRUD tabel **Tidak_sesuai** (`/api/tidak_sesuai`) |
| `schema.sql` | Tabel `scan_data_new`, `scan_log`, view `v_scan` (dibuat otomatis) |
| `dashboard.html` | Dashboard web `http://localhost:5111/` |

Jalankan: terminal VS Code di `C:\QR_Scanner_DB` → `npm start`.

Hubungan tabel → aplikasi:
| DBeaver (aWan_data.db) | Muncul di aplikasi |
|---|---|
| `Area.lokasi` | Dropdown **Area Kerja** (Input) & **Filter Area** (Output) |
| `Grade.grade` | Dropdown **Grade Wire** |
| `scan_data_new` | Hasil **Send Data** (status IN) & **Simpan Output** (status OUT) |

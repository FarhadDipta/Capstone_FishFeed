# FishFeed — Aplikasi Manajemen Pakan Ikan Lele (Capstone 2)

Aplikasi Android native (Kotlin + Jetpack Compose) untuk satu peternak pada satu smartphone.
Fungsi: jadwal pakan per kolam, smart reminder (alarm + notifikasi + quick action + jeda),
riwayat, statistik kedisiplinan, dan widget home screen. Semua data lokal (SQLite lewat Room),
**tanpa login, backend, Firebase, atau internet**.

## Versi 1.3.2
- **Foto kolam**: tombol "Tambah Foto" kini menawarkan **Ambil foto dengan kamera**, **Pilih dari galeri**, dan **Pilih dari file** (FileProvider + `res/xml/file_paths.xml`; tanpa izin kamera tambahan).

## Versi 1.3.1: perbaikan
- **Suara uji coba / alarm**: ditambahkan `<queries>` TTS di manifest (tanpa ini suara pengumuman diam di Android 11+). Jika mesin suara tidak ada, otomatis memakai nada alarm. Alarm sungguhan berbunyi 60 detik (berulang) sampai dikonfirmasi atau dijeda.
- **Mode gelap/terang**: tombol bulan/matahari kini ada di bar atas semua layar (selain pengaturan lengkap di Akun -> Tampilan).

## Versi 1.3: Mode Gelap / Terang + jalan dari VS Code

**Mode gelap/terang**: buka tab **Akun -> Tampilan**, pilih **Ikuti Sistem**, **Terang**, atau **Gelap**.
Pilihan tersimpan (SharedPreferences) dan langsung berlaku di semua layar, termasuk layar Alarm di atas layar kunci.
Warna diatur di satu tempat: `app/src/main/java/com/fishfeed/ui/theme/Theme.kt` (objek `FF`, tiap warna punya nilai terang dan gelap).

**Menjalankan dari VS Code** (tanpa membuka Android Studio, tapi Android Studio + SDK tetap harus terpasang):
1. Nyalakan emulator (atau sambungkan HP dengan USB debugging).
2. Buka folder ini di VS Code, lalu di terminal jalankan: `.\jalankan.ps1`
   (atau **Terminal -> Run Build Task**). Script mengatur JAVA_HOME, membuat `local.properties`, memasang aplikasi, lalu membukanya.
3. Build pertama 5-15 menit karena mengunduh Gradle dan library.

---

## Cara membuka dan menjalankan
1. Pasang **Android Studio** versi terbaru (Koala/Ladybug atau lebih baru). JDK 17+ sudah ikut terpasang.
2. `File > Open` lalu pilih folder `FishFeed` (yang berisi `settings.gradle.kts`).
3. Tunggu **Gradle Sync** selesai (butuh internet pertama kali untuk mengunduh dependensi).
   Jika diminta memasang Android SDK 34, setujui.
4. Sambungkan HP (aktifkan *USB debugging*) atau buat emulator (API 26+, disarankan API 33/34), lalu tekan **Run**.
5. Saat pertama dibuka: isi nama → tambah kolam pertama → buat jadwal → izinkan notifikasi dan alarm.

Versi: AGP 8.5.2, Kotlin 1.9.24, Compose BOM 2024.06.00, Room 2.6.1, Gradle 8.7, minSdk 26, target/compile SDK 34.

## Pembaruan versi 1.2 (disesuaikan dengan laporan capstone)
- **Setup Awal** satu halaman: Nama Lengkap, Nama Kolam, dan **Ukuran Kolam**. Jadwal awal 06.00, 12.00, 18.00 dibuat otomatis.
- **Kelola Kolam**: "Total Kolam N Unit", ukuran/luas kolam, badge AKTIF, tombol + mengambang. Jenis ikan dan jumlah ikan dihapus dari form.
- **Jadwal Pakan** menjadi tab sendiri: dropdown **Pilih Kolam / Semua Kolam**, daftar jadwal + saklar pengingat, tombol +.
  **Tambah/Edit Jadwal**: Pilih Kolam, Waktu Pakan, **Keterangan (opsional)**, status pengingat. Nama jadwal dibuat otomatis (Pakan Pagi/Siang/Sore/Malam).
- **Halaman Alarm Pakan** layar penuh ("Waktunya Kasih Makan!") dengan tombol Sudah Diberi Pakan, Jeda (Snooze), dan Tidak Diberi Pakan (setelah jeda);
  tampil juga di atas layar kunci. Keterangan jadwal ikut tampil.
- **Tunda Pemberian Pakan (Jeda)** berbentuk bottom sheet: pilihan 5/10/15/30 menit, waktu kustom (tombol - dan +), panel "Alur Pengingat Lanjutan",
  tombol "Aktifkan Jeda (N Menit)" dan "Kembali ke Pengingat".
- **Beranda**: "Halo, nama!", kartu jadwal berikutnya dengan konfirmasi/jeda, **Pakan Selesai x/y**, **Kedisiplinan %**, **Menu Cepat**
  (Jadwal Pakan, Riwayat, Statistik, Kelola Kolam), dan **Status Kolam** (OPTIMAL / PERLU CEK / BARU).
- **Riwayat**: filter kolam + waktu (default *Hari Ini*), kartu **Status Pakan (jumlah sesi)** dan **Kedisiplinan**, daftar
  "Target 12:00 • Diberikan 12:25 (terlambat 25 mnt)", dan kartu **Insight**.
- **Statistik**: penilaian (Sangat Baik / Baik / Cukup / Perlu Ditingkatkan) dengan "13 dari 14 pakan diberikan tepat waktu", donut,
  grafik batang per hari, dan kartu **Kesehatan Kolam**.
- Menu bawah: **Beranda, Jadwal, Riwayat, Statistik, Akun** (Akun = halaman profil).
- **Database versi 3** (dengan migrasi 2→3): kolom `ponds.sizeText`, `feed_schedules.note`, `user_settings.createdAt`, dan tabel baru
  **`pond_statistics`** (entitas Statistik pada ERD) yang diperbarui otomatis saat riwayat tercatat.
- Izin tambahan: `USE_FULL_SCREEN_INTENT` (agar halaman alarm muncul di layar kunci; pada Android 14+ pengguna bisa mematikannya di pengaturan aplikasi).

### Pemetaan ERD laporan ke database
| ERD (laporan) | Tabel Room | Atribut |
|---|---|---|
| Pengguna | `user_settings` | `id` (id_pengguna), `name` (nama_pengguna), `createdAt` (dibuat_pada) |
| Kolam | `ponds` | `id` (id_kolam), `name` (nama_kolam), `sizeText` (ukuran), `photoPath`; satu pengguna → banyak kolam (aplikasi satu pemilik, jadi tanpa FK pengguna) |
| Jadwal_Pakan | `feed_schedules` | `id` (id_jadwal), `hour`+`minute` (jam_pakan), `note` (keterangan), `reminderEnabled` (status_aktif), `pondId` (id_kolam) |
| Riwayat_Pakan | `feeding_history` | `id` (id_riwayat), `dateKey` (tanggal), `scheduledTime` + `confirmedAt` (jam), `status` (status_pemberian), `scheduleId` (id_jadwal) |
| Statistik | `pond_statistics` | `id` (id_statistik), `onTimeCount` (jumlah_tepat_waktu), `lateCount` (jumlah_terlambat), `notFedCount`, `disciplinePercent` (persentase_kedisiplinan), `pondId` (id_kolam) |
| (status sementara) | `pending_feedings` | status "Belum Diberi Pakan", tidak masuk riwayat |

> Jika aplikasi versi 1.1 masih terpasang di emulator, migrasi 2→3 akan menjaga datanya. Bila ada masalah, hapus aplikasi lalu pasang ulang.

## Fitur tambahan (versi 1.1)
- **Form kolam**: pilih frekuensi 1x–6x sehari, ketuk jam untuk mengubahnya (atau hapus / "Tambah Jam"). Saat kolam baru disimpan,
  jadwal dan alarmnya langsung dibuat. Untuk kolam yang diubah, jam diatur di layar Jadwal Pakan.
- **Foto kolam** (opsional) dari galeri, disalin ke penyimpanan internal aplikasi dan tampil di kartu kolam dan dashboard.
- **Dashboard**: ikon lonceng dengan angka pengingat yang menunggu (bisa konfirmasi/jeda dari sana), progres jadwal hari ini,
  jadwal berikutnya di tiap kolam, dan tombol "Atur Jadwal Pakan" bila belum ada jadwal.
- **Profil**: tombol "Uji notifikasi sekarang" untuk memeriksa izin dan bunyi notifikasi tanpa menunggu jadwal (tidak mencatat riwayat).
- Izin notifikasi diminta otomatis saat Beranda pertama dibuka (Android 13+).
- Database naik ke versi 2 (kolom `photoPath`) dengan migrasi, sehingga data lama tidak hilang.

## Struktur kode (`app/src/main/java/com/fishfeed`)
| Folder / file | Isi |
|---|---|
| `FishFeedApp.kt`, `MainActivity.kt`, `AlarmActivity.kt` | Application + penampung objek (`AppContainer`), Activity utama, dan halaman Alarm Pakan layar penuh |
| `data/Entities.kt` | Tabel Room: `UserSettings`, `Pond`, `FeedSchedule`, `FeedingHistory`, `PendingFeeding`, `PondStatistics` |
| `data/Daos.kt`, `AppDatabase.kt` | Query SQL dan database `fishfeed.db` |
| `data/FeedingRepository.kt` | **Aturan bisnis**: CRUD, status pakan, jeda, riwayat, pemulihan alarm |
| `reminder/` | `AlarmScheduler` (AlarmManager), `NotificationHelper`, `ReminderReceiver`, `BootReceiver`, `PermissionHelper` |
| `widget/` | `FeedingWidgetProvider` + `WidgetUpdater` (widget home screen sungguhan, RemoteViews) |
| `ui/` | `AppNav` (navigasi), `ViewModels`, `theme/`, `components/`, `screens/` |
| `util/TimeUtils.kt` | Hitung waktu alarm berikutnya, format tanggal Indonesia |

## Database (SQLite / Room)
| Tabel | Primary key | Foreign key | Aturan hapus |
|---|---|---|---|
| `user_settings` | `id` (selalu 1) | – | – |
| `ponds` | `id` | – | – |
| `feed_schedules` | `id` | `pondId → ponds.id` | kolam dihapus → jadwal ikut terhapus (CASCADE) |
| `feeding_history` | `id` | `pondId → ponds.id`, `scheduleId → feed_schedules.id` | kolam dihapus → riwayat ikut terhapus (CASCADE); jadwal dihapus → riwayat tetap, `scheduleId` jadi NULL (SET_NULL), nama kolam/jadwal tersalin di kolom riwayat |
| `pending_feedings` | `scheduleId` | `scheduleId → feed_schedules.id` | jadwal dihapus → ikut terhapus (CASCADE) |
| `pond_statistics` | `id` (unik per `pondId`) | `pondId → ponds.id` | kolam dihapus → ikut terhapus (CASCADE) |

Indeks unik `(scheduleId, dateKey)` pada `feeding_history` + penghapusan baris `pending_feedings` dalam
satu transaksi memastikan **satu kejadian jadwal hanya menghasilkan satu catatan riwayat**, walaupun
dikonfirmasi dari notifikasi, widget, dan layar aplikasi hampir bersamaan.

## Alur status pakan
```
Jadwal tiba ──► "Belum Diberi Pakan"  (baris pending, BELUM masuk riwayat; alarm berulang tiap 5 menit)
   │
   ├─ Konfirmasi tanpa jeda ───────────────────────────► riwayat TEPAT_WAKTU
   └─ Jeda (5/10/15/30 menit / kustom) → alarm berbunyi lagi saat jeda habis
        ├─ Konfirmasi ─────────────────────────────────► riwayat TERLAMBAT
        └─ "Tidak Diberi Pakan" (hanya setelah jeda) ───► riwayat TIDAK_DIBERI
```
Notifikasi muncul **tidak** dianggap pakan sudah diberikan; riwayat hanya dibuat setelah peternak memilih.

## Statistik
`Kedisiplinan = Tepat Waktu ÷ (Tepat Waktu + Terlambat + Tidak Diberi Pakan) × 100%` (dibulatkan).
Jika belum ada riwayat, tampil "-" dan "Belum ada riwayat" (tidak ada angka contoh di kode).
Semua angka dihitung dari tabel `feeding_history` dan bisa difilter per kolam dan periode.

## Izin Android dan keterbatasan (penting untuk pengujian)
- **Notifikasi** (Android 13+): diminta lewat banner di Beranda/Profil. Tanpa izin, reminder tidak tampil.
- **Alarm & pengingat / SCHEDULE_EXACT_ALARM** (Android 12+): perlu diaktifkan pengguna agar alarm tepat waktu.
  Jika belum, aplikasi memakai alarm tidak-tepat sebagai cadangan (bisa meleset beberapa menit) dan menampilkan banner.
- **Restart HP**: `BootReceiver` memasang ulang semua alarm dan alarm pengulang. Jadwal yang terlewat ≤ 60 menit
  saat HP mati akan dibunyikan setelah HP menyala.
- **Penghemat baterai** pada beberapa merek HP (Xiaomi, Oppo, Vivo, Realme, dll.) dapat menunda alarm.
  Atur FishFeed ke "Tanpa pembatasan" (tombol pintasnya ada di menu Profil).
- Alarm berulang dibatasi **12 kali (±1 jam)** per kejadian agar tidak berbunyi tanpa henti; setelah itu status
  tetap "Belum Diberi Pakan" di aplikasi dan widget sampai dikonfirmasi.
- Jika sampai jadwal yang sama berbunyi lagi di hari berikutnya dan kejadian kemarin belum pernah dikonfirmasi,
  kejadian kemarin dicatat otomatis sebagai **Tidak Diberi Pakan**.
- Satu jadwal hanya menghasilkan satu kejadian per hari.
- Widget memperbarui diri saat data berubah dan juga setiap ±30 menit (batas minimum Android).

## Asumsi teknis (tidak dijelaskan rinci di proposal)
- Konfirmasi dari alarm pengulang sebelum menekan Jeda tetap dihitung **Tepat Waktu** ("alarm pertama tanpa jeda").
- Tombol "Jeda" pada notifikasi membuka layar Jeda di dalam aplikasi (sesuai desain: pilihan 5/10/15/30/kustom + "Mulai Jeda").
- Persen kedisiplinan memasukkan "Tidak Diberi Pakan" ke pembagi (lebih jujur). Jika ingin hanya Tepat Waktu ÷ (Tepat + Terlambat),
  ubah `DisciplineStats.percent` di `ui/ViewModels.kt`.
- Tidak ada tombol "Keluar" karena aplikasi tidak memiliki akun; halaman Profil berisi nama, kelola kolam, dan izin.

## Skenario pengujian (Tahap 8)
1. Setup awal (nama, nama kolam, ukuran) → data tersimpan, 3 jadwal awal (06.00/12.00/18.00) terbuat, tutup-buka aplikasi data tetap ada.
2. Tambah 2 kolam, beri jadwal masing-masing, ubah dan hapus jadwal/kolam (cek cascade).
3. Buat jadwal 2 menit dari sekarang → notifikasi + alarm berbunyi → tekan **Sudah Diberi Pakan** → riwayat *Tepat Waktu*.
4. Jadwal berikutnya → tekan **Jeda** 5 menit (atau kustom 1 menit) → alarm berbunyi lagi → **Sudah Diberi Pakan** → riwayat *Terlambat*; ulangi dan pilih **Tidak Diberi** → *Tidak Diberi Pakan*.
5. Konfirmasi dari notifikasi lalu buka aplikasi → tidak ada riwayat ganda.
6. Statistik cocok dengan jumlah riwayat; kosongkan data (kolam baru) → tampil "-".
7. Pasang widget (tekan lama home screen → Widget → FishFeed) → tampil kolam, jadwal, waktu, status; tombol konfirmasi di widget bekerja.
8. Mode pesawat (offline) → semua fitur tetap berjalan.
9. Restart HP/emulator → alarm tetap berbunyi sesuai jadwal.
10. Alarm berbunyi → halaman **Alarm Pakan** tampil (kunci layar juga) → **Jeda (Snooze)** membuka bottom sheet → pilih 5 menit → **Aktifkan Jeda**.
11. Riwayat (Hari Ini) menampilkan kartu sesi, kedisiplinan, dan insight; Beranda menampilkan Status Kolam OPTIMAL/PERLU CEK sesuai persentase.

package com.fishfeed.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fishfeed.util.SoundHelper

/** Pengaturan pengguna. Hanya ada satu baris (id = 1) karena aplikasi untuk satu pemilik. */
@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 1,
    val name: String,
    /** Waktu pengguna pertama kali menyelesaikan setup (kolom "dibuat_pada" pada ERD). */
    val createdAt: Long = System.currentTimeMillis()
)

/** Kolam ikan. Satu pengguna dapat memiliki banyak kolam. */
@Entity(tableName = "ponds")
data class Pond(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Tidak lagi diisi dari layar (laporan tidak memuatnya); dipertahankan agar data lama aman. */
    val fishType: String = "Lele",
    val fishCount: Int? = null,
    /** Ukuran kolam sebagai teks bebas, mis. "4 x 5 m" (sesuai wireframe Setup Awal). */
    val sizeText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Path file foto kolam di penyimpanan internal aplikasi (opsional). */
    val photoPath: String? = null,

    /** Pengaturan suara alarm (QRIS, Default, atau Custom MP3) khusus untuk kolam ini. */
    @ColumnInfo(name = "sound_uri")
    val soundUri: String = SoundHelper.SOUND_TYPE_DEFAULT
)

/**
 * Jadwal pakan milik satu kolam.
 * Kolam dihapus -> jadwal ikut terhapus (CASCADE).
 */
@Entity(
    tableName = "feed_schedules",
    foreignKeys = [
        ForeignKey(
            entity = Pond::class,
            parentColumns = ["id"],
            childColumns = ["pondId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pondId")]
)
data class FeedSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pondId: Long,
    val name: String,
    val hour: Int,
    val minute: Int,
    val reminderEnabled: Boolean = true,
    /** Keterangan opsional (atribut "keterangan" pada ERD), mis. "Pelet apung 2 kg". */
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Riwayat pemberian pakan. Dibuat HANYA saat peternak mengonfirmasi (status final).
 * - Kolam dihapus   -> riwayat ikut terhapus (CASCADE).
 * - Jadwal dihapus  -> riwayat tetap ada, scheduleId menjadi NULL (SET_NULL).
 */
@Entity(
    tableName = "feeding_history",
    foreignKeys = [
        ForeignKey(
            entity = Pond::class,
            parentColumns = ["id"],
            childColumns = ["pondId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FeedSchedule::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("pondId"),
        Index("scheduleId"),
        Index(value = ["scheduleId", "dateKey"], unique = true)
    ]
)
data class FeedingHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pondId: Long,
    val scheduleId: Long?,
    val pondName: String,
    val scheduleName: String,
    /** Jam jadwal, format "HH:mm". */
    val scheduledTime: String,
    /** Tanggal kejadian, format "yyyy-MM-dd". */
    val dateKey: String,
    /** Salah satu dari [FeedStatus]. */
    val status: String,
    val confirmedAt: Long
)

/**
- Status sementara "Belum Diberi Pakan": jadwal sudah tiba tetapi belum dikonfirmasi.

- Baris ini TIDAK termasuk riwayat. Satu jadwal hanya punya satu baris aktif.

 */
@Entity(
    tableName = "pending_feedings",
    foreignKeys = [
        ForeignKey(
            entity = FeedSchedule::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PendingFeeding(
    @PrimaryKey val scheduleId: Long,
    val dateKey: String,
    val firedAt: Long,
    /** Berapa kali peternak menekan Jeda. 0 = alarm pertama belum pernah dijeda. */
    val snoozeCount: Int = 0,
    /** Waktu (epoch ms) jeda berakhir; 0 jika tidak sedang dijeda. */
    val snoozeUntil: Long = 0,
    /** Berapa kali alarm pengulang sudah berbunyi (dibatasi MAX_NAG). */
    val nagCount: Int = 0
)

/**
- Ringkasan kedisiplinan per kolam (entitas "Statistik" pada ERD).

- Diperbarui otomatis setiap kali sebuah riwayat tercatat, dalam transaksi yang sama.

- disciplinePercent = Tepat Waktu / (Tepat Waktu + Terlambat + Tidak Diberi) x 100.

 */
@Entity(
    tableName = "pond_statistics",
    foreignKeys = [
        ForeignKey(
            entity = Pond::class,
            parentColumns = ["id"],
            childColumns = ["pondId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["pondId"], unique = true)]
)
data class PondStatistics(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pondId: Long,
    val onTimeCount: Int,
    val lateCount: Int,
    val notFedCount: Int,
    val disciplinePercent: Int,
    val updatedAt: Long
)

object FeedStatus {
    const val TEPAT_WAKTU = "TEPAT_WAKTU"
    const val TERLAMBAT = "TERLAMBAT"
    const val TIDAK_DIBERI = "TIDAK_DIBERI"

    fun label(status: String): String = when (status) {
        TEPAT_WAKTU -> "Tepat Waktu"
        TERLAMBAT -> "Terlambat"
        TIDAK_DIBERI -> "Tidak Diberi Pakan"
        else -> status
    }
}

/** Hasil query gabungan (bukan tabel). */
data class PondWithCount(
    @Embedded val pond: Pond,
    @ColumnInfo(name = "scheduleCount") val scheduleCount: Int
)

data class ScheduleWithPond(
    @Embedded val schedule: FeedSchedule,
    @ColumnInfo(name = "pondName") val pondName: String
)

/** Gabungan jadwal + nama kolam untuk layar alarm (bukan tabel). */
data class AlarmInfo(val schedule: FeedSchedule, val pondName: String)
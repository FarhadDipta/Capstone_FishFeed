package com.fishfeed.data

import android.content.Context
import androidx.room.withTransaction
import com.fishfeed.reminder.AlarmScheduler
import com.fishfeed.reminder.NotificationHelper
import com.fishfeed.util.PhotoStorage
import com.fishfeed.util.TimeUtils
import com.fishfeed.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/**
 * Satu-satunya pintu akses data + aturan bisnis FishFeed.
 *
 * Aturan status (sesuai proposal):
 *  - Jadwal tiba           -> baris PendingFeeding dibuat = "Belum Diberi Pakan" (belum riwayat).
 *  - Konfirmasi tanpa jeda -> riwayat TEPAT_WAKTU.
 *  - Jeda lalu konfirmasi  -> riwayat TERLAMBAT.
 *  - Jeda lalu "Tidak Diberi" -> riwayat TIDAK_DIBERI.
 * Seluruh perubahan status memakai Mutex + transaksi supaya satu kejadian hanya menghasilkan
 * satu riwayat, baik dikonfirmasi dari notifikasi, widget, maupun layar aplikasi.
 */
class FeedingRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val scheduler: AlarmScheduler,
    private val notifier: NotificationHelper

) {
    private val lock = Mutex()

    companion object {
        /** Jarak alarm pengulang selama belum dikonfirmasi. */
        const val NAG_INTERVAL_MS = 5 * 60_000L
        /** Batas pengulangan (12 x 5 menit = 1 jam) agar tidak berbunyi tanpa henti. */
        const val MAX_NAG = 12
        /** Setelah HP menyala kembali, jadwal yang terlewat <= 60 menit masih dibunyikan. */
        const val CATCH_UP_WINDOW_MS = 60 * 60_000L
    }

    suspend fun clearPendingNotificationsOnAppOpen() {
        db.pendingDao().getAll().forEach { pending ->
            notifier.cancel(pending.scheduleId)
        }
    }

    fun observeUser(): Flow<UserSettings?> = db.userDao().observe()
    suspend fun getUserName(): String? = db.userDao().get()?.name
    suspend fun saveUserName(name: String) {
        val old = db.userDao().get()
        db.userDao().upsert(
            UserSettings(1, name.trim(), old?.createdAt ?: System.currentTimeMillis())
        )
    }

    fun observePonds(): Flow<List<Pond>> = db.pondDao().observeAll()
    fun observePondsWithCount(): Flow<List<PondWithCount>> = db.pondDao().observeWithCount()
    fun observePond(id: Long): Flow<Pond?> = db.pondDao().observe(id)
    suspend fun getPond(id: Long): Pond? = db.pondDao().get(id)
    suspend fun pondCount(): Int = db.pondDao().count()

    suspend fun savePond(pond: Pond): Long {
        val id: Long
        if (pond.id == 0L) {
            id = db.pondDao().insert(pond)
        } else {
            db.pondDao().update(pond)
            db.historyDao().renamePond(pond.id, pond.name)
            id = pond.id
        }
        refreshWidget()
        return id
    }

    /**
     * Sembunyikan seluruh notifikasi pengingat Android
     * ketika aplikasi sedang berada di depan.
     */
    suspend fun hidePendingSystemNotifications() {
        db.pendingDao().getAll().forEach { pending ->
            notifier.cancel(pending.scheduleId)
        }
    }

    /**
     * Tampilkan kembali notifikasi pengingat Android
     * ketika aplikasi masuk ke latar belakang.
     */
    suspend fun showPendingSystemNotifications() {
        db.pendingDao().getAll().forEach { pending ->
            val schedule = db.scheduleDao().get(pending.scheduleId)
                ?: return@forEach

            if (!schedule.reminderEnabled) return@forEach

            val pond = db.pondDao().get(schedule.pondId)
                ?: return@forEach

            notifier.show(
                pending = pending,
                schedule = schedule,
                pondName = pond.name,
                useFullScreen = false
            )
        }
    }

    /**
     * Menyimpan kolam. Untuk kolam BARU, jadwal pakan dibuat otomatis dari daftar jam [times]
     * (jam, menit) dan alarmnya langsung dipasang. Untuk kolam yang diubah, jadwal tidak disentuh.
     */
    suspend fun savePondWithSchedules(pond: Pond, times: List<Pair<Int, Int>>): Long {
        val id = savePond(pond)
        if (pond.id == 0L) {
            times.sortedWith(compareBy({ it.first }, { it.second })).forEach { (h, m) ->
                saveSchedule(
                    FeedSchedule(
                        pondId = id,
                        name = "Pakan ${TimeUtils.periodLabel(h)}",
                        hour = h,
                        minute = m
                    )
                )
            }
        }
        return id
    }

    suspend fun deletePond(pond: Pond) = lock.withLock {
        // Batalkan alarm & notifikasi semua jadwal kolam ini; baris jadwal/riwayat/pending
        // ikut terhapus oleh FOREIGN KEY ... ON DELETE CASCADE.
        db.scheduleDao().getByPond(pond.id).forEach {
            scheduler.cancelAll(it.id)
            notifier.cancel(it.id)
        }
        db.pondDao().delete(pond)
        PhotoStorage.delete(pond.photoPath)
        refreshWidget()
    }

    fun observeSchedules(pondId: Long): Flow<List<FeedSchedule>> = db.scheduleDao().observeByPond(pondId)
    fun observeAllSchedules(): Flow<List<ScheduleWithPond>> = db.scheduleDao().observeAllWithPond()
    suspend fun getSchedule(id: Long): FeedSchedule? = db.scheduleDao().get(id)

    suspend fun getTestScheduleId(): Long? = db.scheduleDao().getAllWithPond()
        .firstOrNull { it.schedule.reminderEnabled }?.schedule?.id

    fun scheduleReminderTest(scheduleId: Long) {
        scheduler.scheduleTest(scheduleId, TimeUtils.now() + 5_000L)
    }

    suspend fun saveSchedule(schedule: FeedSchedule): Long = lock.withLock {
        val id = if (schedule.id == 0L) db.scheduleDao().insert(schedule) else {
            db.scheduleDao().update(schedule)
            schedule.id
        }
        val saved = schedule.copy(id = id)
        if (saved.reminderEnabled) {
            scheduler.scheduleNext(saved)
        } else {
            scheduler.cancelAll(id)
            notifier.cancel(id)
        }
        refreshWidget()
        id
    }

    suspend fun setReminderEnabled(schedule: FeedSchedule, enabled: Boolean) {
        saveSchedule(schedule.copy(reminderEnabled = enabled))
    }

    suspend fun deleteSchedule(schedule: FeedSchedule) = lock.withLock {
        scheduler.cancelAll(schedule.id)
        notifier.cancel(schedule.id)
        db.scheduleDao().delete(schedule)
        refreshWidget()
    }

    fun observeStatistics(): Flow<List<PondStatistics>> = db.statisticsDao().observeAll()

    /** Data untuk layar alarm: jadwal, nama kolam, dan status "Belum Diberi Pakan" saat ini. */
    suspend fun getAlarmInfo(scheduleId: Long): AlarmInfo? {
        val schedule = db.scheduleDao().get(scheduleId) ?: return null
        val pondName = db.pondDao().get(schedule.pondId)?.name ?: ""
        return AlarmInfo(schedule, pondName)
    }

    suspend fun getAlarmSound(scheduleId: Long): String? {
        val schedule = db.scheduleDao().get(scheduleId) ?: return null
        return db.pondDao().get(schedule.pondId)?.soundUri
    }

    fun observePending(): Flow<List<PendingFeeding>> = db.pendingDao().observeAll()
    fun observePending(scheduleId: Long): Flow<PendingFeeding?> = db.pendingDao().observe(scheduleId)
    fun observeHistoryByDate(dateKey: String): Flow<List<FeedingHistory>> = db.historyDao().observeByDate(dateKey)
    fun observeHistory(pondId: Long?, fromKey: String): Flow<List<FeedingHistory>> =
        db.historyDao().observeFiltered(pondId, fromKey)

    /** Dipanggil receiver saat alarm harian sebuah jadwal berbunyi. */
    suspend fun onScheduleFired(scheduleId: Long) = lock.withLock {
        val schedule = db.scheduleDao().get(scheduleId) ?: return@withLock
        if (!schedule.reminderEnabled) return@withLock
        try {
            scheduler.scheduleNext(schedule, after = TimeUtils.now() + 60_000)
        } catch (e: Exception) {
            android.util.Log.e("FishFeedAlarm", "Alarm berikutnya gagal untuk jadwal $scheduleId", e)
        }
        startOccurrence(schedule, TimeUtils.todayKey())
    }

    /** Membuat status "Belum Diberi Pakan" + notifikasi untuk satu kejadian. Harus dipanggil di dalam lock. */
    private suspend fun startOccurrence(schedule: FeedSchedule, dateKey: String) {
        val old = db.pendingDao().get(schedule.id)
        if (old != null) {
            if (old.dateKey == dateKey) return // kejadian hari ini sudah aktif
            // Sisa kejadian hari sebelumnya yang tidak pernah dikonfirmasi ditutup sebagai Tidak Diberi.
            closeOccurrence(old, FeedStatus.TIDAK_DIBERI)
        }
        if (db.historyDao().get(schedule.id, dateKey) != null) return // sudah tercatat hari ini
        val pending = PendingFeeding(schedule.id, dateKey, TimeUtils.now())
        db.pendingDao().upsert(pending)
        notifier.show(
            pending = pending,
            schedule = schedule,
            pondName = pondNameOf(schedule),
            useFullScreen = true
        )
        scheduler.scheduleRemind(schedule.id, TimeUtils.now() + NAG_INTERVAL_MS)
        refreshWidget()
    }

    /** Alarm pengulang atau akhir masa jeda. */
    suspend fun onRemind(scheduleId: Long) = lock.withLock {
        val pending = db.pendingDao().get(scheduleId) ?: return@withLock
        val schedule = db.scheduleDao().get(scheduleId) ?: return@withLock
        if (!schedule.reminderEnabled) return@withLock
        val now = TimeUtils.now()
        if (pending.snoozeUntil > now + 1_000) {
            // Masih dalam masa jeda: tunggu sampai jeda berakhir.
            scheduler.scheduleRemind(scheduleId, pending.snoozeUntil)
            return@withLock
        }
        if (pending.nagCount >= MAX_NAG) return@withLock
        val updated = pending.copy(nagCount = pending.nagCount + 1, snoozeUntil = 0)
        db.pendingDao().upsert(updated)
        notifier.show(
            pending = updated,
            schedule = schedule,
            pondName = pondNameOf(schedule),
            useFullScreen = true
        )
        scheduler.scheduleRemind(scheduleId, now + NAG_INTERVAL_MS)
        refreshWidget()
    }

    /** Peternak mengonfirmasi sudah memberi pakan. @return true jika riwayat baru tercatat. */
    suspend fun confirm(scheduleId: Long): Boolean = lock.withLock {
        val pending = db.pendingDao().get(scheduleId)
        scheduler.cancelRemind(scheduleId)
        notifier.cancel(scheduleId)
        if (pending == null) {
            refreshWidget()
            return@withLock false
        }
        val status = if (pending.snoozeCount == 0) FeedStatus.TEPAT_WAKTU else FeedStatus.TERLAMBAT
        val ok = closeOccurrence(pending, status)
        refreshWidget()
        ok
    }

    /** Peternak menjeda alarm selama [minutes] menit. */
    suspend fun snooze(scheduleId: Long, minutes: Int): Boolean = lock.withLock {
        val pending = db.pendingDao().get(scheduleId) ?: return@withLock false
        val until = TimeUtils.now() + minutes * 60_000L
        db.pendingDao().upsert(
            pending.copy(snoozeCount = pending.snoozeCount + 1, snoozeUntil = until, nagCount = 0)
        )
        notifier.cancel(scheduleId)
        scheduler.scheduleRemind(scheduleId, until)
        refreshWidget()
        true
    }

    /** Hanya boleh setelah jeda (sesuai proposal). */
    suspend fun markNotFed(scheduleId: Long): Boolean = lock.withLock {
        val pending = db.pendingDao().get(scheduleId) ?: return@withLock false
        if (pending.snoozeCount == 0) return@withLock false
        scheduler.cancelRemind(scheduleId)
        notifier.cancel(scheduleId)
        val ok = closeOccurrence(pending, FeedStatus.TIDAK_DIBERI)
        refreshWidget()
        ok
    }

    /**
     * Memindahkan satu kejadian dari "pending" ke riwayat secara atomik.
     * Jika baris pending sudah tidak ada (sudah diselesaikan lewat jalur lain) tidak ada riwayat baru.
     */
    private suspend fun closeOccurrence(pending: PendingFeeding, status: String): Boolean =
        db.withTransaction {
            val removed = db.pendingDao().delete(pending.scheduleId, pending.dateKey)
            if (removed == 0) return@withTransaction false
            val schedule = db.scheduleDao().get(pending.scheduleId) ?: return@withTransaction false
            val pond = db.pondDao().get(schedule.pondId) ?: return@withTransaction false
            val rowId = db.historyDao().insert(
                FeedingHistory(
                    pondId = pond.id,
                    scheduleId = schedule.id,
                    pondName = pond.name,
                    scheduleName = schedule.name,
                    scheduledTime = TimeUtils.formatTime(schedule.hour, schedule.minute),
                    dateKey = pending.dateKey,
                    status = status,
                    confirmedAt = TimeUtils.now()
                )
            )
            if (rowId == -1L) return@withTransaction false
            recalcStatistics(pond.id)
            true
        }

    /** Menghitung ulang ringkasan kedisiplinan satu kolam dari tabel riwayat. */
    private suspend fun recalcStatistics(pondId: Long) {
        val on = db.historyDao().countByStatus(pondId, FeedStatus.TEPAT_WAKTU)
        val late = db.historyDao().countByStatus(pondId, FeedStatus.TERLAMBAT)
        val notFed = db.historyDao().countByStatus(pondId, FeedStatus.TIDAK_DIBERI)
        val total = on + late + notFed
        val percent = if (total == 0) 0 else (on * 100.0 / total).roundToInt()
        db.statisticsDao().upsert(
            PondStatistics(
                pondId = pondId,
                onTimeCount = on,
                lateCount = late,
                notFedCount = notFed,
                disciplinePercent = percent,
                updatedAt = TimeUtils.now()
            )
        )
    }

    /**
     * Memasang ulang alarm. Dipanggil saat aplikasi dibuka, HP restart, jam/zona waktu berubah,
     * aplikasi diperbarui, atau izin alarm tepat waktu berubah.
     *
     * @param restorePending pasang ulang alarm pengulang untuk status "Belum Diberi Pakan".
     * @param catchUp        bunyikan jadwal yang terlewat <= 60 menit selagi HP mati (hanya saat boot).
     */
    suspend fun restoreAll(restorePending: Boolean, catchUp: Boolean) = lock.withLock {
        val now = TimeUtils.now()
        val today = TimeUtils.todayKey()
        for (item in db.scheduleDao().getAllWithPond()) {
            val s = item.schedule
            if (!s.reminderEnabled) {
                scheduler.cancelFire(s.id)
                continue
            }
            scheduler.scheduleNext(s)
            if (catchUp) {
                val todayAt = TimeUtils.todayAt(s.hour, s.minute)
                if (todayAt <= now && now - todayAt <= CATCH_UP_WINDOW_MS && s.createdAt < todayAt &&
                    db.pendingDao().get(s.id) == null && db.historyDao().get(s.id, today) == null
                ) {
                    startOccurrence(s, today)
                }
            }
        }
        if (restorePending) {
            for (p in db.pendingDao().getAll()) {
                val s = db.scheduleDao().get(p.scheduleId) ?: continue
                if (!s.reminderEnabled) continue
                scheduler.scheduleRemind(p.scheduleId, maxOf(p.snoozeUntil, now + 3_000))
            }
        }
        refreshWidget()
    }

    /** Notifikasi uji: memastikan izin, suara, dan tampilan notifikasi bekerja. Tidak mencatat riwayat. */
    fun sendTestNotification() = notifier.showTest()

    private suspend fun pondNameOf(schedule: FeedSchedule): String =
        db.pondDao().get(schedule.pondId)?.name ?: ""

    private suspend fun refreshWidget() {
        try {
            WidgetUpdater.update(context, db)
        } catch (e: Exception) {
            // Widget hanya tampilan tambahan; kegagalan update tidak boleh membatalkan pencatatan.
        }
    }
}

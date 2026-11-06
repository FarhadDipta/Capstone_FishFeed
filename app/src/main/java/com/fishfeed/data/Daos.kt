package com.fishfeed.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun observe(): Flow<UserSettings?>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun get(): UserSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: UserSettings)
}

@Dao
interface PondDao {
    @Query("SELECT * FROM ponds ORDER BY id")
    fun observeAll(): Flow<List<Pond>>

    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM feed_schedules s WHERE s.pondId = p.id) AS scheduleCount " +
            "FROM ponds p ORDER BY p.id"
    )
    fun observeWithCount(): Flow<List<PondWithCount>>

    @Query("SELECT * FROM ponds WHERE id = :id")
    suspend fun get(id: Long): Pond?

    @Query("SELECT * FROM ponds WHERE id = :id")
    fun observe(id: Long): Flow<Pond?>

    @Query("SELECT COUNT(*) FROM ponds")
    suspend fun count(): Int

    @Insert
    suspend fun insert(pond: Pond): Long

    @Update
    suspend fun update(pond: Pond)

    @Delete
    suspend fun delete(pond: Pond)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM feed_schedules WHERE pondId = :pondId ORDER BY hour, minute")
    fun observeByPond(pondId: Long): Flow<List<FeedSchedule>>

    @Query("SELECT * FROM feed_schedules WHERE pondId = :pondId")
    suspend fun getByPond(pondId: Long): List<FeedSchedule>

    @Query(
        "SELECT s.*, p.name AS pondName FROM feed_schedules s " +
            "INNER JOIN ponds p ON p.id = s.pondId ORDER BY s.hour, s.minute"
    )
    fun observeAllWithPond(): Flow<List<ScheduleWithPond>>

    @Query(
        "SELECT s.*, p.name AS pondName FROM feed_schedules s " +
            "INNER JOIN ponds p ON p.id = s.pondId ORDER BY s.hour, s.minute"
    )
    suspend fun getAllWithPond(): List<ScheduleWithPond>

    @Query("SELECT * FROM feed_schedules WHERE id = :id")
    suspend fun get(id: Long): FeedSchedule?

    @Insert
    suspend fun insert(schedule: FeedSchedule): Long

    @Update
    suspend fun update(schedule: FeedSchedule)

    @Delete
    suspend fun delete(schedule: FeedSchedule)
}

@Dao
interface PendingDao {
    @Query("SELECT * FROM pending_feedings")
    fun observeAll(): Flow<List<PendingFeeding>>

    @Query("SELECT * FROM pending_feedings ORDER BY firedAt")
    suspend fun getAll(): List<PendingFeeding>

    @Query("SELECT * FROM pending_feedings WHERE scheduleId = :scheduleId")
    suspend fun get(scheduleId: Long): PendingFeeding?

    @Query("SELECT * FROM pending_feedings WHERE scheduleId = :scheduleId")
    fun observe(scheduleId: Long): Flow<PendingFeeding?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pending: PendingFeeding)

    /** Mengembalikan jumlah baris terhapus. 0 berarti kejadian ini sudah diselesaikan lebih dulu. */
    @Query("DELETE FROM pending_feedings WHERE scheduleId = :scheduleId AND dateKey = :dateKey")
    suspend fun delete(scheduleId: Long, dateKey: String): Int
}

@Dao
interface HistoryDao {
    /** IGNORE + indeks unik = tidak mungkin ada riwayat ganda untuk satu kejadian. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(history: FeedingHistory): Long

    @Query("UPDATE feeding_history SET pondName = :name WHERE pondId = :pondId")
    suspend fun renamePond(pondId: Long, name: String)

    @Query("SELECT COUNT(*) FROM feeding_history WHERE pondId = :pondId AND status = :status")
    suspend fun countByStatus(pondId: Long, status: String): Int

    @Query("SELECT * FROM feeding_history WHERE scheduleId = :scheduleId AND dateKey = :dateKey LIMIT 1")
    suspend fun get(scheduleId: Long, dateKey: String): FeedingHistory?

    @Query("SELECT * FROM feeding_history WHERE dateKey = :dateKey")
    fun observeByDate(dateKey: String): Flow<List<FeedingHistory>>

    /** pondId = NULL berarti semua kolam. fromKey berformat yyyy-MM-dd ("0000-00-00" = semua tanggal). */
    @Query(
        "SELECT * FROM feeding_history WHERE (:pondId IS NULL OR pondId = :pondId) " +
            "AND dateKey >= :fromKey ORDER BY dateKey DESC, scheduledTime DESC, id DESC"
    )
    fun observeFiltered(pondId: Long?, fromKey: String): Flow<List<FeedingHistory>>
}

@Dao
interface StatisticsDao {
    @Query("SELECT * FROM pond_statistics")
    fun observeAll(): Flow<List<PondStatistics>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: PondStatistics)
}

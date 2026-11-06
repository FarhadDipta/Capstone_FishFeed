package com.fishfeed.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fishfeed.data.FeedStatus
import com.fishfeed.data.FeedSchedule
import com.fishfeed.data.FeedingHistory
import com.fishfeed.data.FeedingRepository
import com.fishfeed.data.Pond
import com.fishfeed.data.PondStatistics
import com.fishfeed.data.PondWithCount
import com.fishfeed.data.ScheduleWithPond
import com.fishfeed.util.TimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Pembuat ViewModel dengan parameter (tanpa library DI). */
fun <VM : ViewModel> vmFactory(build: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = build() as T
    }

private val whileSubscribed = SharingStarted.WhileSubscribed(5_000)

enum class TodayState { MENUNGGU, BELUM_DIBERI, DIJEDA, TEPAT_WAKTU, TERLAMBAT, TIDAK_DIBERI, TERLEWAT, NONAKTIF }

data class TodayItem(
    val schedule: FeedSchedule,
    val pondName: String,
    val state: TodayState,
    /** Keterangan tambahan, mis. "Dijeda hingga 08:15". */
    val detail: String?,
    /** true jika peternak sudah pernah menjeda (membuka opsi "Tidak Diberi Pakan"). */
    val afterSnooze: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(private val repo: FeedingRepository) : ViewModel() {
    val userName: StateFlow<String> = repo.observeUser().map { it?.name ?: "" }
        .stateIn(viewModelScope, whileSubscribed, "")

    val ponds: StateFlow<List<PondWithCount>> = repo.observePondsWithCount()
        .stateIn(viewModelScope, whileSubscribed, emptyList())

    /** Ringkasan kedisiplinan per kolam (tabel statistik), kunci = id kolam. */
    val stats: StateFlow<Map<Long, PondStatistics>> = repo.observeStatistics()
        .map { list -> list.associateBy { it.pondId } }
        .stateIn(viewModelScope, whileSubscribed, emptyMap())

    /** Detak tiap 30 detik agar status "Menunggu waktu" / "Terlewat" dan pergantian hari ikut diperbarui. */
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(30_000)
        }
    }

    private val todayHistory = ticker.map { TimeUtils.todayKey() }
        .distinctUntilChanged()
        .flatMapLatest { repo.observeHistoryByDate(it) }

    val items: StateFlow<List<TodayItem>> = combine(
        repo.observeAllSchedules(), repo.observePending(), todayHistory, ticker
    ) { schedules, pendings, history, _ ->
        val now = TimeUtils.now()
        schedules.map { sw ->
            val s = sw.schedule
            val pending = pendings.firstOrNull { it.scheduleId == s.id }
            val done = history.firstOrNull { it.scheduleId == s.id }
            var detail: String? = null
            val state = when {
                pending != null && pending.snoozeUntil > now -> {
                    detail = "Dijeda hingga ${TimeUtils.formatClock(pending.snoozeUntil)}"
                    TodayState.DIJEDA
                }
                pending != null -> {
                    if (pending.dateKey != TimeUtils.todayKey()) {
                        detail = "Sejak ${TimeUtils.formatDate(pending.dateKey)}"
                    }
                    TodayState.BELUM_DIBERI
                }
                done != null -> when (done.status) {
                    FeedStatus.TEPAT_WAKTU -> TodayState.TEPAT_WAKTU
                    FeedStatus.TERLAMBAT -> TodayState.TERLAMBAT
                    else -> TodayState.TIDAK_DIBERI
                }
                !s.reminderEnabled -> TodayState.NONAKTIF
                TimeUtils.todayAt(s.hour, s.minute) > now -> TodayState.MENUNGGU
                else -> {
                    detail = "Tidak ada konfirmasi tercatat"
                    TodayState.TERLEWAT
                }
            }
            TodayItem(s, sw.pondName, state, detail, (pending?.snoozeCount ?: 0) > 0)
        }.sortedWith(
            compareBy<TodayItem> {
                if (it.state == TodayState.BELUM_DIBERI || it.state == TodayState.DIJEDA) 0 else 1
            }.thenBy { it.schedule.hour }.thenBy { it.schedule.minute }
        )
    }.stateIn(viewModelScope, whileSubscribed, emptyList())

    fun confirm(scheduleId: Long) {
        viewModelScope.launch { repo.confirm(scheduleId) }
    }

    fun notFed(scheduleId: Long) {
        viewModelScope.launch { repo.markNotFed(scheduleId) }
    }
}

class PondsViewModel(private val repo: FeedingRepository) : ViewModel() {
    val ponds: StateFlow<List<PondWithCount>> = repo.observePondsWithCount()
        .stateIn(viewModelScope, whileSubscribed, emptyList())

    fun delete(pond: Pond) {
        viewModelScope.launch { repo.deletePond(pond) }
    }
}

class ScheduleTabViewModel(private val repo: FeedingRepository) : ViewModel() {
    val ponds: StateFlow<List<Pond>> = repo.observePonds().stateIn(viewModelScope, whileSubscribed, emptyList())

    /** null = Semua Kolam. */
    val pondFilter = MutableStateFlow<Long?>(null)

    val schedules: StateFlow<List<ScheduleWithPond>> =
        combine(repo.observeAllSchedules(), pondFilter) { all, filter ->
            if (filter == null) all else all.filter { it.schedule.pondId == filter }
        }.stateIn(viewModelScope, whileSubscribed, emptyList())

    fun toggle(schedule: FeedSchedule, enabled: Boolean) {
        viewModelScope.launch { repo.setReminderEnabled(schedule, enabled) }
    }

    fun delete(schedule: FeedSchedule) {
        viewModelScope.launch { repo.deleteSchedule(schedule) }
    }
}

enum class Period(val label: String, val days: Long?) {
    TODAY("Hari Ini", 0),
    D7("7 Hari Terakhir", 6),
    D30("30 Hari Terakhir", 29),
    ALL("Semua Waktu", null);

    fun fromKey(): String = if (days == null) "0000-00-00" else TimeUtils.dateKeyDaysAgo(days)
}

@OptIn(ExperimentalCoroutinesApi::class)
class MonitorViewModel(repo: FeedingRepository, defaultPeriod: Period = Period.D7) : ViewModel() {
    val ponds: StateFlow<List<Pond>> = repo.observePonds().stateIn(viewModelScope, whileSubscribed, emptyList())

    /** null = semua kolam. */
    val pondFilter = MutableStateFlow<Long?>(null)
    val period = MutableStateFlow(defaultPeriod)

    val history: StateFlow<List<FeedingHistory>> = combine(pondFilter, period) { pond, per -> pond to per }
        .flatMapLatest { (pond, per) -> repo.observeHistory(pond, per.fromKey()) }
        .stateIn(viewModelScope, whileSubscribed, emptyList())
}

/**
 * Rumus kedisiplinan:
 *   persen = Tepat Waktu / (Tepat Waktu + Terlambat + Tidak Diberi Pakan) x 100%
 * Jika belum ada riwayat sama sekali, [percent] bernilai null (ditampilkan sebagai "-").
 */
data class DisciplineStats(val onTime: Int, val late: Int, val notFed: Int) {
    val total: Int get() = onTime + late + notFed
    val percent: Int? get() = if (total == 0) null else (onTime * 100.0 / total).roundToInt()

    companion object {
        fun of(list: List<FeedingHistory>) = DisciplineStats(
            onTime = list.count { it.status == FeedStatus.TEPAT_WAKTU },
            late = list.count { it.status == FeedStatus.TERLAMBAT },
            notFed = list.count { it.status == FeedStatus.TIDAK_DIBERI }
        )
    }
}

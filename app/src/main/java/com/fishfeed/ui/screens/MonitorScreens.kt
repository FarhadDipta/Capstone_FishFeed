package com.fishfeed.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fishfeed.data.FeedingHistory
import com.fishfeed.data.FeedingRepository
import com.fishfeed.ui.DisciplineStats
import com.fishfeed.ui.MonitorViewModel
import com.fishfeed.ui.Period
import com.fishfeed.ui.components.*
import com.fishfeed.ui.theme.FF
import com.fishfeed.ui.vmFactory
import com.fishfeed.util.TimeUtils
import java.time.LocalDate

/** Filter kolam + periode yang dipakai bersama oleh Riwayat dan Statistik. */
@Composable
private fun FilterBar(vm: MonitorViewModel) {
    val ponds by vm.ponds.collectAsStateWithLifecycle()
    val pondFilter by vm.pondFilter.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()

    val pondLabel = ponds.firstOrNull { it.id == pondFilter }?.name ?: "Semua Kolam"
    val pondOptions: List<Pair<String, Long?>> =
        listOf("Semua Kolam" to null) + ponds.map { it.name to it.id }
    val periodOptions = Period.values().map { it.label to it }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DropdownSelector(pondLabel, pondOptions, { vm.pondFilter.value = it }, Modifier.weight(1f))
        DropdownSelector(period.label, periodOptions, { vm.period.value = it }, Modifier.weight(1f))
    }
}

/** Label penilaian kedisiplinan berdasarkan persentase. */
internal fun disciplineLabel(percent: Int?): String = when {
    percent == null -> "Belum ada data"
    percent >= 90 -> "Sangat Baik"
    percent >= 75 -> "Baik"
    percent >= 50 -> "Cukup"
    else -> "Perlu Ditingkatkan"
}

internal fun disciplineColor(percent: Int?): Color = when {
    percent == null -> FF.Neutral
    percent >= 75 -> FF.OnTime
    percent >= 50 -> FF.Late
    else -> FF.NotFed
}

@Composable
fun HistoryScreen(repo: FeedingRepository, onSelectTab: (String) -> Unit) {
    val vm: MonitorViewModel = viewModel(
        key = "history",
        factory = vmFactory { MonitorViewModel(repo, Period.TODAY) }
    )
    val history by vm.history.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val stats = remember(history) { DisciplineStats.of(history) }

    TabScaffold("Riwayat Pakan", ROUTE_HISTORY, onSelectTab) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { FilterBar(vm) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FFCard(Modifier.weight(1f)) {
                        Text("STATUS PAKAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FF.TextMuted)
                        Text("${stats.total} Sesi", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = FF.PrimaryDark)
                        Text(
                            "Tepat ${stats.onTime} • Lambat ${stats.late} • Lewat ${stats.notFed}",
                            fontSize = 11.sp,
                            color = FF.TextMuted
                        )
                    }
                    FFCard(Modifier.weight(1f)) {
                        Text("KEDISIPLINAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FF.TextMuted)
                        Text(
                            stats.percent?.let { "$it%" } ?: "-",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = FF.PrimaryDark
                        )
                        Text(disciplineLabel(stats.percent), fontSize = 11.sp, color = disciplineColor(stats.percent))
                    }
                }
            }
            item { SectionTitle(if (period == Period.TODAY) "Aktivitas Hari Ini" else "Aktivitas Pakan") }
            if (history.isEmpty()) {
                item { EmptyHint("Belum ada riwayat pada filter ini. Riwayat tercatat setelah pakan dikonfirmasi.") }
            } else {
                items(history, key = { "history_${it.id}" }) { h -> HistoryCard(h, showDate = period != Period.TODAY) }
                item {
                    Surface(color = FF.PrimaryLight, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                if (period == Period.TODAY) "Insight Hari Ini" else "Insight",
                                fontWeight = FontWeight.Bold,
                                color = FF.PrimaryDark
                            )
                            Text(insightText(history), fontSize = 13.sp, color = FF.TextDark)
                        }
                    }
                }
            }
        }
    }
}

private fun delayMinutes(h: FeedingHistory): Long =
    TimeUtils.minutesAfterSchedule(h.dateKey, h.scheduledTime, h.confirmedAt).coerceAtLeast(0)

private fun insightText(history: List<FeedingHistory>): String {
    val late = history.filter { it.status == com.fishfeed.data.FeedStatus.TERLAMBAT }
    val notFed = history.filter { it.status == com.fishfeed.data.FeedStatus.TIDAK_DIBERI }
    if (late.isEmpty() && notFed.isEmpty()) {
        return "Semua pakan tercatat tepat waktu. Pertahankan kedisiplinan ini!"
    }
    val parts = mutableListOf<String>()
    if (late.isNotEmpty()) {
        val avg = late.map { delayMinutes(it) }.average().toInt()
        parts.add("${late.size} pakan terlambat (rata-rata $avg menit)")
    }
    if (notFed.isNotEmpty()) parts.add("${notFed.size} pakan tidak diberikan")
    return parts.joinToString(" dan ").replaceFirstChar { it.uppercase() } +
        ". Periksa kembali jam jadwal agar sesuai dengan kegiatan harianmu."
}

@Composable
private fun HistoryCard(h: FeedingHistory, showDate: Boolean) {
    val (label, fg, bg) = historyStatusStyle(h.status)
    val clock = TimeUtils.formatClock(h.confirmedAt)
    val detail = when (h.status) {
        com.fishfeed.data.FeedStatus.TEPAT_WAKTU -> "Target ${h.scheduledTime} • Diberikan $clock"
        com.fishfeed.data.FeedStatus.TERLAMBAT ->
            "Target ${h.scheduledTime} • Diberikan $clock (terlambat ${delayMinutes(h)} mnt)"
        else -> "Target ${h.scheduledTime} • Dilewati (dicatat $clock)"
    }
    FFCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(h.scheduleName, fontWeight = FontWeight.Bold)
                Text(detail, color = FF.TextMuted, fontSize = 12.sp)
                Text(
                    if (showDate) "${h.pondName} • ${TimeUtils.formatDate(h.dateKey)}" else h.pondName,
                    color = FF.TextMuted,
                    fontSize = 12.sp
                )
            }
            StatusChip(label, fg, bg)
        }
    }
}

@Composable
fun StatsScreen(repo: FeedingRepository, onSelectTab: (String) -> Unit) {
    val vm: MonitorViewModel = viewModel(key = "stats", factory = vmFactory { MonitorViewModel(repo) })
    val history by vm.history.collectAsStateWithLifecycle()
    val stats = remember(history) { DisciplineStats.of(history) }

    TabScaffold("Statistik Kedisiplinan", ROUTE_STATS, onSelectTab) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FilterBar(vm)
            FFCard(Modifier.fillMaxWidth()) {
                Text("Kedisiplinan Pemberian Pakan", fontSize = 12.sp, color = FF.TextMuted)
                Text(
                    disciplineLabel(stats.percent),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = disciplineColor(stats.percent)
                )
                Text(
                    if (stats.total == 0) "Belum ada pakan yang tercatat pada filter ini."
                    else "${stats.onTime} dari ${stats.total} pakan diberikan tepat waktu.",
                    fontSize = 13.sp,
                    color = FF.TextDark
                )
            }
            FFCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Donut(stats, Modifier.size(120.dp))
                    Spacer(Modifier.width(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LegendRow(FF.OnTime, "Tepat Waktu", stats.onTime)
                        LegendRow(FF.Late, "Terlambat", stats.late)
                        LegendRow(FF.NotFed, "Tidak Diberi Pakan", stats.notFed)
                    }
                }
            }
            FFCard(Modifier.fillMaxWidth()) {
                SectionTitle("Pemberian Pakan (persentase tepat waktu per hari)")
                Spacer(Modifier.height(8.dp))
                DailyBars(history)
            }
            val pondFilter by vm.pondFilter.collectAsStateWithLifecycle()
            val ponds by vm.ponds.collectAsStateWithLifecycle()
            val pondName = ponds.firstOrNull { it.id == pondFilter }?.name ?: "Semua Kolam"
            val pct = stats.percent
            FFCard(Modifier.fillMaxWidth()) {
                Text("Kesehatan Kolam", fontSize = 12.sp, color = FF.TextMuted)
                Text(pondName, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        pct == null -> "Belum ada data"
                        pct >= 80 -> "Optimal & Stabil"
                        else -> "Perlu Perhatian"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (pct == null) FF.Neutral else if (pct >= 80) FF.OnTime else FF.Late
                )
                Text("Penilaian dari keteraturan pemberian pakan pada periode yang dipilih.", fontSize = 12.sp, color = FF.TextMuted)
            }
            FFCard(Modifier.fillMaxWidth()) {
                Text("Cara menghitung", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "Kedisiplinan = Tepat Waktu ÷ (Tepat Waktu + Terlambat + Tidak Diberi Pakan) × 100%. " +
                        "Jadwal yang belum dikonfirmasi belum dihitung karena belum masuk riwayat.",
                    color = FF.TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, modifier = Modifier.width(120.dp))
        Text("$count", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Donut(stats: DisciplineStats, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            // Lingkaran dasar (abu-abu) lalu busur per status.
            drawArc(FF.NeutralBg, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (stats.total > 0) {
                var start = -90f
                listOf(
                    stats.onTime to FF.OnTime,
                    stats.late to FF.Late,
                    stats.notFed to FF.NotFed
                ).forEach { (count, color) ->
                    if (count > 0) {
                        val sweep = 360f * count / stats.total
                        drawArc(color, start, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                        start += sweep
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stats.percent?.let { "$it%" } ?: "-",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = FF.TextDark
            )
            Text(
                if (stats.total == 0) "Belum ada\nriwayat" else "Tepat Waktu",
                fontSize = 10.sp,
                color = FF.TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Grafik batang kedisiplinan per hari untuk 7 hari terakhir (dari data riwayat nyata). */
@Composable
private fun DailyBars(history: List<FeedingHistory>) {
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val byDay = history.groupBy { it.dateKey }
    Row(
        Modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { day ->
            val s = DisciplineStats.of(byDay[day.toString()] ?: emptyList())
            val pct = s.percent
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(pct?.let { "$it" } ?: "-", fontSize = 10.sp, color = FF.TextMuted)
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .width(20.dp)
                        .height(((pct ?: 0) * 0.9f).dp.coerceAtLeast(2.dp))
                        .background(if (pct == null) FF.NeutralBg else FF.OnTime, RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.height(4.dp))
                Text(TimeUtils.dayShort(day), fontSize = 11.sp, color = FF.TextMuted)
            }
        }
    }
}

package com.fishfeed.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fishfeed.data.FeedingRepository
import com.fishfeed.ui.DashboardViewModel
import com.fishfeed.ui.TodayItem
import com.fishfeed.ui.TodayState
import com.fishfeed.ui.components.*
import com.fishfeed.ui.theme.FF
import com.fishfeed.ui.vmFactory
import com.fishfeed.util.TimeUtils
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import com.fishfeed.AlarmActivity
import com.fishfeed.reminder.AlarmSoundService

private fun todayStyle(state: TodayState): Triple<String, Color, Color> = when (state) {
    TodayState.BELUM_DIBERI -> Triple("Belum diberi pakan", FF.NotFed, FF.NotFedBg)
    TodayState.DIJEDA -> Triple("Dijeda", FF.Late, FF.LateBg)
    TodayState.TEPAT_WAKTU -> Triple("Tepat Waktu", FF.OnTime, FF.OnTimeBg)
    TodayState.TERLAMBAT -> Triple("Terlambat", FF.Late, FF.LateBg)
    TodayState.TIDAK_DIBERI -> Triple("Tidak Diberi Pakan", FF.NotFed, FF.NotFedBg)
    TodayState.TERLEWAT -> Triple("Terlewat", FF.Neutral, FF.NeutralBg)
    TodayState.NONAKTIF -> Triple("Pengingat nonaktif", FF.Neutral, FF.NeutralBg)
    TodayState.MENUNGGU -> Triple("Menunggu waktu", FF.Primary, FF.PrimaryLight)
}

private fun isFinished(s: TodayState) =
    s == TodayState.TEPAT_WAKTU || s == TodayState.TERLAMBAT || s == TodayState.TIDAK_DIBERI

@Composable
fun DashboardScreen(
    repo: FeedingRepository,
    onSelectTab: (String) -> Unit,
    onManagePonds: () -> Unit,
    onSnooze: (Long) -> Unit
) {
    val vm: DashboardViewModel = viewModel(factory = vmFactory { DashboardViewModel(repo) })
    val name by vm.userName.collectAsStateWithLifecycle()
    val ponds by vm.ponds.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    var showBell by remember { mutableStateOf(false) }

    val context = LocalContext.current

    fun confirmFromDashboard(scheduleId: Long) {
        AlarmSoundService.stop(context)
        vm.confirm(scheduleId)
    }

    fun notFedFromDashboard(scheduleId: Long) {
        AlarmSoundService.stop(context)
        vm.notFed(scheduleId)
    }

    fun openAlarmScreen(scheduleId: Long) {
        context.startActivity(
            AlarmActivity.intent(
                context = context,
                scheduleId = scheduleId,
                openSnooze = false
            )
        )
    }

    val waiting = items.filter { it.state == TodayState.BELUM_DIBERI || it.state == TodayState.DIJEDA }
    val finished = items.count { isFinished(it.state) }
    val total = items.count { it.state != TodayState.NONAKTIF }

    // Jadwal berikutnya: yang sedang menunggu konfirmasi lebih dulu, lalu jadwal terdekat yang belum tiba.
    val next: TodayItem? = waiting.firstOrNull()
        ?: items.filter { it.state == TodayState.MENUNGGU }
            .minByOrNull { it.schedule.hour * 60 + it.schedule.minute }

    // Kedisiplinan keseluruhan dari tabel statistik per kolam.
    val onTimeAll = stats.values.sumOf { it.onTimeCount }
    val totalAll = stats.values.sumOf { it.onTimeCount + it.lateCount + it.notFedCount }
    val disciplineAll = if (totalAll == 0) null else (onTimeAll * 100.0 / totalAll).roundToInt()

    TabScaffold(
        title = "FishFeed",
        selectedRoute = ROUTE_HOME,
        onSelectTab = onSelectTab,
        topBarActions = {
            IconButton(onClick = { showBell = true }) {
                BadgedBox(badge = { if (waiting.isNotEmpty()) Badge { Text("${waiting.size}") } }) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Pengingat")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { PermissionBanner(autoRequest = true) }

            item {
                Column {
                    Text(
                        if (name.isBlank()) "Halo, Peternak!" else "Halo, $name!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = FF.TextDark
                    )
                    Text("Semoga budidaya lelemu lancar hari ini", color = FF.TextMuted, fontSize = 13.sp)
                }
            }

            // Kartu jadwal pakan berikutnya
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            next?.let {
                                if (
                                    it.state == TodayState.BELUM_DIBERI ||
                                    it.state == TodayState.DIJEDA
                                ) {
                                    openAlarmScreen(it.schedule.id)
                                }
                            }
                        }
                ) {
                    Column(
                        Modifier
                            .background(Brush.verticalGradient(listOf(FF.Primary, FF.PrimaryDeep)))
                            .padding(18.dp)
                    ) {
                        Text("JADWAL PAKAN BERIKUTNYA", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (next == null) {
                            Spacer(Modifier.height(8.dp))
                            Text("Tidak ada jadwal yang menunggu", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (items.isEmpty()) "Atur jam pakan agar alarm pengingat aktif."
                                else "Semua jadwal hari ini sudah selesai atau terlewat.",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp
                            )
                            if (items.isEmpty() && ponds.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = { onSelectTab(ROUTE_SCHEDULE) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FF.PrimaryDeep)
                                ) { Text("Atur Jadwal Pakan", fontWeight = FontWeight.Bold) }
                            }
                        } else {
                            val isWaiting = next.state == TodayState.BELUM_DIBERI || next.state == TodayState.DIJEDA
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    TimeUtils.formatTime(next.schedule.hour, next.schedule.minute),
                                    color = Color.White,
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.padding(bottom = 6.dp)) {
                                    Text(next.schedule.name, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(next.pondName, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                                }
                            }
                            if (next.schedule.note.isNotBlank()) {
                                Text(next.schedule.note, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            if (next.state == TodayState.BELUM_DIBERI) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            confirmFromDashboard(next.schedule.id)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White,
                                            contentColor = FF.PrimaryDeep
                                        )
                                    ) {
                                        Text(
                                            "Sudah Diberi Pakan",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }

                                    if (!next.afterSnooze) {
                                        OutlinedButton(
                                            onClick = {
                                                onSnooze(next.schedule.id)
                                            },
                                            modifier = Modifier.height(46.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.5.dp, Color.White),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Text("Jeda", fontWeight = FontWeight.SemiBold)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                notFedFromDashboard(next.schedule.id)
                                            },
                                            modifier = Modifier.height(46.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.5.dp, Color.White),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Text(
                                                "Tidak Diberi",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            } else if (next.state == TodayState.DIJEDA) {
                                Text(
                                    next.detail ?: "Pengingat sedang dijeda",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // Pakan selesai + kedisiplinan
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FFCard(Modifier.weight(1f)) {
                        Text("Pakan Selesai", color = FF.TextMuted, fontSize = 12.sp)
                        Text("$finished/$total", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = FF.PrimaryDark)
                        Text("Target hari ini", color = FF.TextMuted, fontSize = 11.sp)
                    }
                    FFCard(Modifier.weight(1f)) {
                        Text("Kedisiplinan", color = FF.TextMuted, fontSize = 12.sp)
                        Text(
                            disciplineAll?.let { "$it%" } ?: "-",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = FF.PrimaryDark
                        )
                        Text(
                            if (disciplineAll == null) "Belum ada riwayat" else "Semua kolam",
                            color = FF.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Menu cepat
            item { SectionTitle("Menu Cepat") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickTile("Jadwal Pakan", Icons.Filled.CalendarMonth, Modifier.weight(1f)) { onSelectTab(ROUTE_SCHEDULE) }
                    QuickTile("Riwayat", Icons.Filled.History, Modifier.weight(1f)) { onSelectTab(ROUTE_HISTORY) }
                    QuickTile("Statistik", Icons.Filled.BarChart, Modifier.weight(1f)) { onSelectTab(ROUTE_STATS) }
                    QuickTile("Kelola Kolam", Icons.Filled.WaterDrop, Modifier.weight(1f)) { onManagePonds() }
                }
            }

            // Status kolam
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Status Kolam", Modifier.weight(1f))
                    TextButton(onClick = onManagePonds) { Text("Lihat Semua", color = FF.Primary) }
                }
            }
            if (ponds.isEmpty()) {
                item { EmptyHint("Belum ada kolam. Tambahkan lewat menu Kelola Kolam.") }
            } else {
                items(ponds, key = { "pond_${it.pond.id}" }) { p ->
                    val pondItems = items.filter { it.schedule.pondId == p.pond.id && it.state != TodayState.NONAKTIF }
                    val pondDone = pondItems.count { isFinished(it.state) }
                    val st = stats[p.pond.id]
                    val stTotal = st?.let { it.onTimeCount + it.lateCount + it.notFedCount } ?: 0
                    val (chipText, chipFg, chipBg) = when {
                        st == null || stTotal == 0 -> Triple("BARU", FF.Primary, FF.PrimaryLight)
                        st.disciplinePercent >= 80 -> Triple("OPTIMAL", FF.OnTime, FF.OnTimeBg)
                        else -> Triple("PERLU CEK", FF.Late, FF.LateBg)
                    }
                    val pondNext = items
                        .filter { it.schedule.pondId == p.pond.id && it.schedule.reminderEnabled }
                        .minByOrNull { TimeUtils.nextTrigger(it.schedule.hour, it.schedule.minute) }
                    FFCard(Modifier.fillMaxWidth(), onClick = { onSelectTab(ROUTE_SCHEDULE) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PondPhoto(p.pond.photoPath, Modifier.size(56.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.pond.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    StatusChip(chipText, chipFg, chipBg)
                                }
                                Text(
                                    if (pondNext != null)
                                        "Jadwal berikutnya ${TimeUtils.formatTime(pondNext.schedule.hour, pondNext.schedule.minute)}"
                                    else "Belum ada jadwal aktif",
                                    color = FF.TextMuted,
                                    fontSize = 12.sp
                                )
                                if (pondItems.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { pondDone / pondItems.size.toFloat() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(50)),
                                        color = FF.Primary,
                                        trackColor = FF.PrimaryLight
                                    )
                                    Text("$pondDone dari ${pondItems.size} jadwal selesai hari ini", color = FF.TextMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Jadwal Pakan Hari Ini") }
            if (items.isEmpty()) {
                item { EmptyHint("Belum ada jadwal pakan.") }
            } else {
                items(items, key = { "today_${it.schedule.id}" }) { item ->
                    TodayCard(
                        item,
                        onConfirm = {
                            confirmFromDashboard(item.schedule.id)
                        },
                        onSnooze = {
                            onSnooze(item.schedule.id)
                        },
                        onNotFed = {
                            notFedFromDashboard(item.schedule.id)
                        }
                    )
                }
            }
        }
    }

    if (showBell) {
        AlertDialog(
            onDismissRequest = { showBell = false },
            title = { Text("Pengingat Pakan") },
            text = {
                if (waiting.isEmpty()) {
                    Text("Tidak ada pengingat yang menunggu. Semua jadwal yang sudah tiba sudah dikonfirmasi.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        waiting.forEach { w ->
                            Column {
                                Text(
                                    "${w.pondName} - ${w.schedule.name} (${TimeUtils.formatTime(w.schedule.hour, w.schedule.minute)})",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(w.detail ?: "Belum diberi pakan", color = FF.TextMuted, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = {
                                            confirmFromDashboard(w.schedule.id)
                                        }
                                    ) {
                                        Text("Sudah Diberi Pakan", color = FF.Primary)
                                    }
                                    if (!w.afterSnooze) {
                                        TextButton(
                                            onClick = {
                                                showBell = false
                                                onSnooze(w.schedule.id)
                                            }
                                        ) {
                                            Text("Jeda", color = FF.Late)
                                        }
                                    } else {
                                        TextButton(
                                            onClick = {
                                                notFedFromDashboard(w.schedule.id)
                                            }
                                        ) {
                                            Text("Tidak Diberi Pakan", color = FF.NotFed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBell = false }) { Text("Tutup") } }
        )
    }
}

@Composable
private fun QuickTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(FF.Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(44.dp).background(FF.PrimaryLight, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = FF.PrimaryDark) }
        Text(label, fontSize = 11.sp, textAlign = TextAlign.Center, color = FF.TextDark, maxLines = 2)
    }
}


@Composable
private fun TodayCard(
    item: TodayItem,
    onConfirm: () -> Unit,
    onSnooze: () -> Unit,
    onNotFed: () -> Unit
) {
    val (label, fg, bg) = todayStyle(item.state)

    FFCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.AccessTime,
                null,
                tint = FF.Primary
            )

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "${TimeUtils.formatTime(item.schedule.hour, item.schedule.minute)}  ${item.schedule.name}",
                    fontWeight = FontWeight.Bold
                )

                Text(
                    item.pondName,
                    color = FF.TextMuted,
                    fontSize = 13.sp
                )

                if (item.detail != null) {
                    Text(
                        item.detail,
                        color = FF.TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            StatusChip(label, fg, bg)
        }

        if (item.state == TodayState.BELUM_DIBERI) {
            Spacer(Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FF.Primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "Sudah Diberi Pakan",
                        fontSize = 13.sp
                    )
                }

                if (!item.afterSnooze) {
                    OutlinedButton(
                        onClick = onSnooze,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("Jeda", fontSize = 13.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onNotFed,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(
                            "Tidak Diberi",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}


package com.fishfeed.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fishfeed.data.FeedSchedule
import com.fishfeed.data.FeedingRepository
import com.fishfeed.ui.ScheduleTabViewModel
import com.fishfeed.ui.components.*
import com.fishfeed.ui.theme.FF
import com.fishfeed.ui.vmFactory
import com.fishfeed.util.TimeUtils
import kotlinx.coroutines.launch

/**
 * Halaman Jadwal Pakan (tab): pilih kolam (atau Semua Kolam), daftar jadwal dengan saklar pengingat,
 * ketuk untuk mengubah, tombol + untuk menambah.
 */
@Composable
fun ScheduleListScreen(
    repo: FeedingRepository,
    onSelectTab: (String) -> Unit,
    onAdd: (pondId: Long) -> Unit,
    onEdit: (pondId: Long, scheduleId: Long) -> Unit
) {
    val vm: ScheduleTabViewModel = viewModel(factory = vmFactory { ScheduleTabViewModel(repo) })
    val ponds by vm.ponds.collectAsStateWithLifecycle()
    val filter by vm.pondFilter.collectAsStateWithLifecycle()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<FeedSchedule?>(null) }

    val pondLabel = ponds.firstOrNull { it.id == filter }?.name ?: "Semua Kolam"
    val pondOptions: List<Pair<String, Long?>> = listOf("Semua Kolam" to null) + ponds.map { it.name to it.id }

    TabScaffold("Jadwal Pakan", ROUTE_SCHEDULE, onSelectTab) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Pilih Kolam", fontSize = 12.sp, color = FF.TextMuted)
                        DropdownSelector(pondLabel, pondOptions, { vm.pondFilter.value = it })
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Daftar Jadwal", Modifier.weight(1f))
                        Text("${schedules.count { it.schedule.reminderEnabled }} jadwal aktif", fontSize = 12.sp, color = FF.TextMuted)
                    }
                }
                if (ponds.isEmpty()) {
                    item { EmptyHint("Belum ada kolam. Tambahkan kolam lewat menu Akun > Kelola Kolam.") }
                } else if (schedules.isEmpty()) {
                    item { EmptyHint("Belum ada jadwal pakan. Tekan tombol + untuk menambahkan.") }
                } else {
                    items(schedules, key = { "schedule_${it.schedule.id}" }) { sw ->
                        val s = sw.schedule
                        FFCard(Modifier.fillMaxWidth(), onClick = { onEdit(s.pondId, s.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        TimeUtils.formatTime(s.hour, s.minute),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FF.PrimaryDark
                                    )
                                    Text(sw.pondName, fontSize = 12.sp, color = FF.TextMuted)
                                    if (s.note.isNotBlank()) {
                                        Text(s.note, fontSize = 12.sp, color = FF.TextMuted, maxLines = 2)
                                    }
                                }
                                Switch(
                                    checked = s.reminderEnabled,
                                    onCheckedChange = { vm.toggle(s, it) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = FF.Primary)
                                )
                                IconButton(onClick = { toDelete = s }) {
                                    Icon(Icons.Filled.Delete, "Hapus jadwal", tint = FF.NotFed)
                                }
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Aktifkan saklar agar alarm dan notifikasi berbunyi saat waktu pakan tiba.",
                        fontSize = 12.sp,
                        color = FF.TextMuted
                    )
                }
            }
            if (ponds.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { onAdd(filter ?: 0L) },
                    containerColor = FF.Primary,
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
                ) { Icon(Icons.Filled.Add, contentDescription = "Tambah jadwal") }
            }
        }
    }

    toDelete?.let { s ->
        ConfirmDialog(
            title = "Hapus jadwal?",
            message = "Jadwal \"${s.name}\" akan dihapus. Riwayat yang sudah tercatat tetap tersimpan.",
            confirmLabel = "Hapus",
            onConfirm = {
                vm.delete(s)
                toDelete = null
            },
            onDismiss = { toDelete = null }
        )
    }
}

/**
 * Halaman Tambah/Edit Jadwal: kolam tujuan, waktu pakan, keterangan (opsional), status pengingat.
 * @param pondId kolam awal; 0 = belum dipilih (pilih lewat dropdown).
 */
@Composable
fun ScheduleFormScreen(
    repo: FeedingRepository,
    pondId: Long,
    scheduleId: Long,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val ponds by repo.observePonds().collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedPond by rememberSaveable { mutableLongStateOf(pondId) }
    var hour by rememberSaveable { mutableIntStateOf(8) }
    var minute by rememberSaveable { mutableIntStateOf(0) }
    var note by rememberSaveable { mutableStateOf("") }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var createdAt by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var loaded by rememberSaveable { mutableStateOf(scheduleId == 0L) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(scheduleId) {
        if (!loaded) {
            repo.getSchedule(scheduleId)?.let {
                selectedPond = it.pondId
                hour = it.hour
                minute = it.minute
                note = it.note
                enabled = it.reminderEnabled
                createdAt = it.createdAt
            }
            loaded = true
        }
    }
    // Jika belum ada kolam yang dipilih, pilih kolam pertama.
    LaunchedEffect(ponds) {
        if (selectedPond == 0L && ponds.isNotEmpty()) selectedPond = ponds.first().id
    }

    val pondLabel = ponds.firstOrNull { it.id == selectedPond }?.name ?: "Pilih Kolam"

    Scaffold(
        containerColor = FF.Background,
        topBar = {
            FFTopBar(if (scheduleId == 0L) "Tambah Jadwal" else "Ubah Jadwal", onBack = onBack)
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Pilih Kolam", fontWeight = FontWeight.SemiBold, color = FF.TextDark)
            DropdownSelector(pondLabel, ponds.map { it.name to it.id }, { selectedPond = it })

            Text("Waktu Pakan", fontWeight = FontWeight.SemiBold, color = FF.TextDark)
            WheelTimePicker(
                hour = hour,
                minute = minute,
                onHourChange = { hour = it },
                onMinuteChange = { minute = it }
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(120) },
                label = { Text("Keterangan (Opsional)") },
                placeholder = { Text("Contoh: Pakan pelet terapung, 2 kg") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                Modifier.fillMaxWidth().clickable { enabled = !enabled },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Status Pengingat", fontWeight = FontWeight.SemiBold)
                    Text("Bunyikan alarm dan notifikasi saat waktu pakan tiba", fontSize = 12.sp, color = FF.TextMuted)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = FF.Primary)
                )
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                "Simpan",
                enabled = selectedPond != 0L && loaded && !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        repo.saveSchedule(
                            FeedSchedule(
                                id = scheduleId,
                                pondId = selectedPond,
                                name = "Pakan ${TimeUtils.periodLabel(hour)}",
                                hour = hour,
                                minute = minute,
                                reminderEnabled = enabled,
                                note = note.trim(),
                                createdAt = createdAt
                            )
                        )
                        onBack()
                    }
                }
            )
        }
    }
}

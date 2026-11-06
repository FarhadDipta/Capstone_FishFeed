package com.fishfeed.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fishfeed.data.AlarmInfo
import com.fishfeed.data.FeedingRepository
import com.fishfeed.data.PendingFeeding
import com.fishfeed.R
import com.fishfeed.ui.theme.FF
import com.fishfeed.reminder.AlarmSoundService
import com.fishfeed.util.TimeUtils
import kotlinx.coroutines.launch

/**
 * Halaman Alarm Pakan (layar penuh): "Waktunya Kasih Makan!" dengan tombol
 * Sudah Diberi Pakan, Jeda (membuka bottom sheet), dan Tidak Diberi Pakan (hanya setelah jeda).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(
    repo: FeedingRepository,
    scheduleId: Long,
    openSnooze: Boolean,
    testMode: Boolean = false,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<AlarmInfo?>(null) }
    var pending by remember { mutableStateOf<PendingFeeding?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(openSnooze) }

    LaunchedEffect(scheduleId) {
        info = runCatching {
            repo.getAlarmInfo(scheduleId)
        }.getOrNull()
    }
    LaunchedEffect(scheduleId) {
        repo.observePending(scheduleId).collect {
            pending = it
            loaded = true
        }
    }

    val p = pending
    val i = info
    val afterSnooze = (p?.snoozeCount ?: 0) > 0

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FF.PrimaryDeep, FF.Primary)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
        ) {
            Text("FishFeed", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)

            Box(
                Modifier.size(120.dp).background(Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(84.dp).background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = FF.Primary, modifier = Modifier.size(44.dp))
                }
            }

            if (loaded && (p == null || i == null) && !testMode) {
                Text(
                    stringResource(R.string.alarm_finished),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FF.PrimaryDeep)
                ) { Text(stringResource(R.string.alarm_close), fontWeight = FontWeight.Bold) }
            } else {
                Text(
                    stringResource(if (afterSnooze) R.string.alarm_snooze_ended else R.string.alarm_due),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (i != null) {
                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(i.pondName, color = Color.White, style = MaterialTheme.typography.titleLarge)
                            Text(
                                TimeUtils.formatTime(i.schedule.hour, i.schedule.minute),
                                color = Color.White,
                                style = MaterialTheme.typography.displayMedium
                            )
                            Text(
                                i.schedule.name,
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (i.schedule.note.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    i.schedule.note,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (testMode) {
                            AlarmSoundService.stop(context)
                            onClose()
                        } else {
                            scope.launch {
                                repo.confirm(scheduleId)
                                AlarmSoundService.stop(context)
                                onClose()
                            }
                        }
                    },
                    enabled = p != null || testMode,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FF.PrimaryDeep)
                    ) { Text(stringResource(R.string.alarm_confirm), style = MaterialTheme.typography.titleMedium) }

                if (!afterSnooze) {
                    OutlinedButton(
                        onClick = { showSheet = true },
                        enabled = p != null || testMode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            stringResource(R.string.alarm_snooze),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                if (afterSnooze) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                repo.markNotFed(scheduleId)
                                AlarmSoundService.stop(context)
                                onClose()
                            }
                        }
                    ) { Text(stringResource(R.string.alarm_not_fed), color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold) }
                }
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.alarm_close_repeat), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showSheet && (p != null || testMode)) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = FF.Surface
        ) {
            SnoozeSheetContent(
                pondName = i?.pondName ?: "",
                onActivate = { minutes ->
                    if (testMode) {
                        AlarmSoundService.stop(context)
                        onClose()
                    } else {
                        scope.launch {
                            repo.snooze(scheduleId, minutes)
                            AlarmSoundService.stop(context)
                            onClose()
                        }
                    }
                },
                onCancel = { showSheet = false }
            )
        }
    }
}

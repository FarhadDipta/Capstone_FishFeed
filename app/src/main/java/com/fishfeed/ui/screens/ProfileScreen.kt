package com.fishfeed.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fishfeed.data.FeedingRepository
import com.fishfeed.R
import com.fishfeed.ui.components.*
import com.fishfeed.reminder.PermissionHelper
import com.fishfeed.ui.theme.FF
import com.fishfeed.ui.theme.ThemeMode
import com.fishfeed.ui.theme.ThemePrefs
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    repo: FeedingRepository,
    onSelectTab: (String) -> Unit,
    onManagePonds: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val notificationsReady = remember(permissionRefresh) { PermissionHelper.notificationsGranted(context) }
    val exactAlarmReady = remember(permissionRefresh) { PermissionHelper.exactAlarmGranted(context) }
    val fullScreenReady = remember(permissionRefresh) { PermissionHelper.fullScreenIntentGranted(context) }
    val batteryReady = remember(permissionRefresh) { PermissionHelper.batteryOptimizationIgnored(context) }
    val userFlow = remember(repo) { repo.observeUser() }
    val user by userFlow.collectAsStateWithLifecycle(initialValue = null)
    val name = user?.name ?: ""
    var editing by remember { mutableStateOf(false) }

    TabScaffold("Akun", ROUTE_PROFILE, onSelectTab) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FFCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = FF.PrimaryLight, shape = androidx.compose.foundation.shape.CircleShape) {
                        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                            Text(
                                name.take(1).uppercase(),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = FF.PrimaryDark
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Pemilik / peternak", color = FF.TextMuted, fontSize = 13.sp)
                    }
                    IconButton(onClick = { editing = true }) {
                        Icon(Icons.Filled.Edit, "Ubah nama", tint = FF.Primary)
                    }
                }
            }

            PrimaryButton("Kelola Kolam", onClick = onManagePonds)

            SectionTitle("Tampilan")
            FFCard(Modifier.fillMaxWidth()) {
                Text("Mode gelap / terang", fontWeight = FontWeight.SemiBold)
                Text(
                    "Pilih tema aplikasi. \"Ikuti Sistem\" mengikuti pengaturan tema HP.",
                    color = FF.TextMuted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.values().forEach { m ->
                        val selected = ThemePrefs.mode == m
                        Box(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) FF.PrimaryLight else FF.Surface)
                                .border(
                                    BorderStroke(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) FF.Primary else FF.Border
                                    ),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { ThemePrefs.set(m) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                m.label,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) FF.PrimaryDark else FF.TextDark
                            )
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.reminder_check_title))
            PermissionBanner()
            FFCard(Modifier.fillMaxWidth()) {
                ReminderPermissionRow(R.string.reminder_notifications, notificationsReady) {
                    openNotificationSettings(context)
                }
                ReminderPermissionRow(R.string.reminder_exact_alarm, exactAlarmReady) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                ReminderPermissionRow(R.string.reminder_full_screen, fullScreenReady) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                ReminderPermissionRow(R.string.reminder_battery, batteryReady) {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                Text(
                    stringResource(R.string.reminder_status_source),
                    color = FF.TextMuted,
                    fontSize = 13.sp
                )
                PrimaryButton("Uji pengingat sekarang", onClick = {
                    scope.launch {
                        val scheduleId = repo.getTestScheduleId()
                        if (scheduleId == null) {
                            Toast.makeText(context, context.getString(R.string.reminder_test_no_schedule), Toast.LENGTH_SHORT).show()
                        } else {
                            repo.scheduleReminderTest(scheduleId)
                            Toast.makeText(context, context.getString(R.string.reminder_test_delay), Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }

            FFCard(Modifier.fillMaxWidth()) {
                Text("Tentang FishFeed", fontWeight = FontWeight.Bold)
                Text(
                    "FishFeed adalah alat bantu pengingat dan pencatat pemberian pakan lele untuk satu pemilik " +
                        "pada satu smartphone. Semua data tersimpan di perangkat dan aplikasi berjalan offline. " +
                        "Jika aplikasi bermasalah, pemberian pakan tetap dilakukan secara manual.",
                    color = FF.TextMuted,
                    fontSize = 13.sp
                )
            }
        }
    }

    if (editing) {
        var text by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Ubah nama") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    singleLine = true,
                    label = { Text("Nama Pengguna") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        scope.launch { repo.saveUserName(text) }
                        editing = false
                    }
                ) { Text("Simpan", color = FF.Primary) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun ReminderPermissionRow(label: Int, ready: Boolean, onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            if (ready) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = if (ready) "Siap" else "Perlu tindakan",
            tint = if (ready) FF.OnTime else FF.Late
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), fontWeight = FontWeight.Medium)
            Text(
                stringResource(if (ready) R.string.reminder_status_ready else R.string.reminder_status_action),
                color = FF.TextMuted,
                fontSize = 13.sp
            )
        }
        if (!ready) {
            TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.reminder_settings_action))
            }
        }
    }
}

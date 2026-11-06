package com.fishfeed.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fishfeed.data.FeedStatus
import com.fishfeed.reminder.PermissionHelper
import com.fishfeed.R
import com.fishfeed.ui.theme.FF
import com.fishfeed.ui.theme.ThemeMode
import com.fishfeed.ui.theme.ThemePrefs
import com.fishfeed.util.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val ROUTE_HOME = "home"
const val ROUTE_SCHEDULE = "schedule"
const val ROUTE_HISTORY = "history"
const val ROUTE_STATS = "stats"
const val ROUTE_PROFILE = "profile"

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(ROUTE_HOME, "Beranda", Icons.Filled.Home),
    Tab(ROUTE_SCHEDULE, "Jadwal", Icons.Filled.CalendarMonth),
    Tab(ROUTE_HISTORY, "Riwayat", Icons.Filled.History),
    Tab(ROUTE_STATS, "Statistik", Icons.Filled.BarChart),
    Tab(ROUTE_PROFILE, "Akun", Icons.Filled.Person)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FFTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
            }
        },
        actions = {
            actions()
            // Tombol cepat mode gelap / terang di semua layar
            IconButton(onClick = {
                ThemePrefs.set(if (FF.isDark) ThemeMode.LIGHT else ThemeMode.DARK)
            }) {
                Icon(
                    if (FF.isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = if (FF.isDark) "Ganti ke mode terang" else "Ganti ke mode gelap"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = FF.Primary,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}

/** Kerangka halaman utama dengan menu bawah (Beranda, Jadwal, Riwayat, Statistik, Akun). */
@Composable
fun TabScaffold(
    title: String,
    selectedRoute: String,
    onSelectTab: (String) -> Unit,
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = FF.Background,
        topBar = { FFTopBar(title, actions = topBarActions) },
        bottomBar = {
            NavigationBar(containerColor = FF.Surface) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = tab.route == selectedRoute,
                        onClick = { onSelectTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = FF.Primary,
                            selectedTextColor = FF.Primary,
                            indicatorColor = FF.PrimaryLight,
                            unselectedIconColor = FF.TextMuted,
                            unselectedTextColor = FF.TextMuted
                        )
                    )
                }
            }
        },
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FFCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val colors = CardDefaults.cardColors(containerColor = FF.Surface)
    val border = BorderStroke(1.dp, FF.Border)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(14.dp), content = content)
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(14.dp), content = content)
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = FF.Primary)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusChip(text: String, fg: Color, bg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Warna dan label untuk status riwayat. Mengembalikan Triple(label, warna teks, warna latar). */
fun historyStatusStyle(status: String): Triple<String, Color, Color> = when (status) {
    FeedStatus.TEPAT_WAKTU -> Triple("Tepat Waktu", FF.OnTime, FF.OnTimeBg)
    FeedStatus.TERLAMBAT -> Triple("Terlambat", FF.Late, FF.LateBg)
    FeedStatus.TIDAK_DIBERI -> Triple("Tidak Diberi Pakan", FF.NotFed, FF.NotFedBg)
    else -> Triple(status, FF.Neutral, FF.NeutralBg)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = FF.TextDark
    )
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = FF.TextMuted, fontSize = 14.sp)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = FF.NotFed) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/** Dropdown sederhana: tombol yang menampilkan pilihan saat ditekan. */
@Composable
fun <T> DropdownSelector(
    value: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = FF.Surface, contentColor = FF.TextDark)
        ) {
            Text(value, maxLines = 1, modifier = Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (label, item) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    }
                )
            }
        }
    }
}

/**
 * Banner izin: muncul hanya jika izin notifikasi atau alarm tepat waktu belum diberikan,
 * karena tanpa izin tersebut reminder tidak bisa berfungsi dengan baik.
 */
@Composable
fun PermissionBanner(modifier: Modifier = Modifier, autoRequest: Boolean = false) {
    val context = LocalContext.current
    var notifOk by remember { mutableStateOf(PermissionHelper.notificationsGranted(context)) }
    var alarmOk by remember { mutableStateOf(PermissionHelper.exactAlarmGranted(context)) }
    var fullScreenOk by remember { mutableStateOf(PermissionHelper.fullScreenIntentGranted(context)) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifOk = PermissionHelper.notificationsGranted(context)
                alarmOk = PermissionHelper.exactAlarmGranted(context)
                fullScreenOk = PermissionHelper.fullScreenIntentGranted(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOk = PermissionHelper.notificationsGranted(context)
    }

    // Minta izin notifikasi otomatis satu kali saat halaman pertama kali tampil (Android 13+).
    LaunchedEffect(autoRequest) {
        if (autoRequest && !notifOk && !askedOnce && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askedOnce = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!notifOk) {
            PermissionCard(
                text = context.getString(R.string.permission_notifications_missing),
                button = "Izinkan"
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedOnce) {
                    askedOnce = true
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Sudah pernah diminta/ditolak (atau Android < 13): buka pengaturan notifikasi aplikasi.
                    openNotificationSettings(context)
                }
            }
        }
        if (!alarmOk) {
            PermissionCard(
                text = context.getString(R.string.permission_exact_alarm_missing),
                button = "Aktifkan"
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
        if (!fullScreenOk) {
            PermissionCard(
                text = context.getString(R.string.permission_full_screen_missing),
                button = "Aktifkan"
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }
}

fun openNotificationSettings(context: android.content.Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
private fun PermissionCard(text: String, button: String, onClick: () -> Unit) {
    Surface(
        color = FF.LateBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, FF.Late.copy(alpha = 0.4f))
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = FF.Late)
            Text(text, modifier = Modifier.weight(1f), fontSize = 13.sp, color = FF.TextDark)
            TextButton(onClick = onClick) { Text(button, color = FF.Primary, fontWeight = FontWeight.Bold) }
        }
    }
}


/** Foto kolam; jika belum ada foto tampil ikon tetesan air di atas latar hijau muda. */
@Composable
fun PondPhoto(path: String?, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            try {
                PhotoStorage.load(path, 400)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(FF.PrimaryLight, FF.PhotoEnd))),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "Foto kolam",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = FF.Primary)
        }
    }
}

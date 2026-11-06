package com.fishfeed.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fishfeed.data.FeedingRepository
import com.fishfeed.data.Pond
import com.fishfeed.R
import com.fishfeed.ui.PondsViewModel
import com.fishfeed.ui.components.*
import com.fishfeed.ui.theme.FF
import com.fishfeed.ui.vmFactory
import com.fishfeed.util.PhotoStorage
import com.fishfeed.util.TimeUtils
import com.fishfeed.util.SoundHelper
import kotlinx.coroutines.launch


@Composable
fun PondListScreen(
    repo: FeedingRepository,
    onBack: () -> Unit,
    onOpenPond: (Long) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val vm: PondsViewModel = viewModel(factory = vmFactory { PondsViewModel(repo) })
    val ponds by vm.ponds.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Pond?>(null) }

    Scaffold(
        containerColor = FF.Background,
        topBar = { FFTopBar("Kelola Kolam", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = FF.Primary,
                contentColor = androidx.compose.ui.graphics.Color.White
            ) { Icon(Icons.Filled.Add, contentDescription = "Tambah kolam") }
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FFCard(Modifier.fillMaxWidth()) {
                    Text("Total Kolam", color = FF.TextMuted, fontSize = 12.sp)
                    Text("${ponds.size} Unit", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = FF.PrimaryDark)
                }
            }
            if (ponds.isEmpty()) {
                item { EmptyHint("Belum ada kolam. Tekan tombol + untuk menambahkan.") }
            }
            items(ponds, key = { "pond_${it.pond.id}" }) { p ->
                FFCard(Modifier.fillMaxWidth(), onClick = { onOpenPond(p.pond.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PondPhoto(p.pond.photoPath, Modifier.size(64.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.pond.name, fontWeight = FontWeight.Bold)
                            Text(
                                "Luas: ${p.pond.sizeText?.takeIf { it.isNotBlank() } ?: "-"}",
                                color = FF.TextMuted, fontSize = 13.sp
                            )
                            Text("Jadwal: ${p.scheduleCount}x/hari", color = FF.TextMuted, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            StatusChip("AKTIF", FF.OnTime, FF.OnTimeBg)
                        }
                        IconButton(onClick = { onEdit(p.pond.id) }) {
                            Icon(Icons.Filled.Edit, "Ubah kolam", tint = FF.Primary)
                        }
                        IconButton(onClick = { toDelete = p.pond }) {
                            Icon(Icons.Filled.Delete, "Hapus kolam", tint = FF.NotFed)
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { pond ->
        ConfirmDialog(
            title = "Hapus ${pond.name}?",
            message = "Seluruh jadwal dan riwayat pakan kolam ini ikut terhapus dan tidak dapat dikembalikan.",
            confirmLabel = "Hapus",
            onConfirm = {
                vm.delete(pond)
                toDelete = null
            },
            onDismiss = { toDelete = null }
        )
    }
}

/** Jam pakan bawaan (menit sejak 00:00) untuk setiap pilihan frekuensi per hari. */
private fun defaultTimes(perDay: Int): List<Int> = when (perDay) {
    1 -> listOf(8 * 60)
    2 -> listOf(8 * 60, 17 * 60)
    3 -> listOf(8 * 60, 14 * 60, 20 * 60)
    4 -> listOf(7 * 60, 11 * 60, 15 * 60, 19 * 60)
    5 -> listOf(6 * 60, 9 * 60, 12 * 60, 15 * 60, 18 * 60)
    else -> listOf(6 * 60, 9 * 60, 12 * 60, 15 * 60, 18 * 60, 21 * 60)
}

private fun parseTimes(csv: String): List<Int> =
    csv.split(",").mapNotNull { it.toIntOrNull() }.distinct().sorted()

private fun minutesLabel(total: Int): String = TimeUtils.formatTime(total / 60, total % 60)

private fun validatePondName(value: String): String? {
    val name = value.trim()

    if (name.isBlank()) {
        return "Nama kolam wajib diisi."
    }

    if (name.length < 3) {
        return "Nama kolam minimal 3 karakter."
    }

    if (name.length > 40) {
        return "Nama kolam maksimal 40 karakter."
    }

    return null
}
private fun validatePondSize(value: String): String? {
    if (value.isBlank()) {
        return "Ukuran kolam wajib diisi."
    }

    val dimensions = value
        .lowercase()
        .replace("×", "x")
        .split("x")
        .map { it.trim() }

    if (dimensions.size != 2) {
        return "Gunakan format panjang x lebar, contoh: 4 x 5."
    }

    val length = dimensions[0].replace(",", ".").toDoubleOrNull()
    val width = dimensions[1].replace(",", ".").toDoubleOrNull()

    if (length == null || width == null) {
        return "Panjang dan lebar harus berupa angka."
    }

    if (length <= 0 || width <= 0) {
        return "Panjang dan lebar harus lebih dari 0."
    }

    return null
}

/**
- Form tambah/ubah kolam.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PondFormScreen(
    repo: FeedingRepository,
    pondId: Long,
    first: Boolean,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onOpenSchedules: (Long) -> Unit
) {
    val context = LocalContext.current
    val isNew = pondId == 0L
    var name by rememberSaveable { mutableStateOf("") }
    var sizeText by rememberSaveable { mutableStateOf("") }
    var createdAt by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var originalPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var timesCsv by rememberSaveable { mutableStateOf(defaultTimes(3).joinToString(",")) }
    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var nameTouched by rememberSaveable { mutableStateOf(false) }
    var sizeError by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val times = parseTimes(timesCsv)

    var soundUri by rememberSaveable { mutableStateOf(SoundHelper.SOUND_TYPE_DEFAULT) }

    val newPhotos = remember { mutableListOf<String>() }
    var saved by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            SoundHelper.stopSound()
            if (!saved) newPhotos.forEach { PhotoStorage.delete(it) }
        }
    }

    LaunchedEffect(pondId) {
        if (!loaded) {
            repo.getPond(pondId)?.let {
                name = it.name
                sizeText = it.sizeText ?: ""
                createdAt = it.createdAt
                photoPath = it.photoPath
                originalPhoto = it.photoPath
                soundUri = it.soundUri
            }
            loaded = true
        }
    }

    // Simpan foto yang dipilih / diambil (diperkecil) ke penyimpanan aplikasi
    fun handlePickedPhoto(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val path = PhotoStorage.savePondPhoto(context, uri)
            if (path != null) {
                photoPath?.let { old ->
                    if (old in newPhotos) {
                        PhotoStorage.delete(old)
                        newPhotos.remove(old)
                    }
                }
                newPhotos.add(path)
                photoPath = path
            } else {
                Toast.makeText(context, "Foto gagal dimuat, coba foto lain.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showPhotoSourceDialog by rememberSaveable { mutableStateOf(false) }
    var cameraUriString by rememberSaveable { mutableStateOf<String?>(null) }

    // 1) Galeri
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        handlePickedPhoto(uri)
    }
    // 2) File manager (Files / Downloads / Drive)
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handlePickedPhoto(uri)
    }
    // 3) Kamera (tanpa izin tambahan: foto disimpan sementara lewat FileProvider)
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUriString?.let { Uri.parse(it) }
        if (ok && uri != null) handlePickedPhoto(uri)
        cameraUriString = null
    }
    fun openCamera() {
        try {
            val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
            val file = java.io.File(dir, "foto_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            cameraUriString = uri.toString()
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Kamera tidak dapat dibuka di perangkat ini.", Toast.LENGTH_SHORT).show()
        }
    }

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            containerColor = FF.Surface,
            title = { Text("Foto Kolam", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    @Composable
                    fun Option(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showPhotoSourceDialog = false; onClick() }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, null, tint = FF.Primary)
                            Spacer(Modifier.width(14.dp))
                            Text(label, fontSize = 15.sp, color = FF.TextDark)
                        }
                    }
                    Option(Icons.Filled.CameraAlt, "Ambil foto dengan kamera") { openCamera() }
                    Option(Icons.Filled.PhotoLibrary, "Pilih dari galeri") {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    Option(Icons.Filled.Folder, "Pilih dari file") {
                        filePicker.launch(arrayOf("image/*"))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPhotoSourceDialog = false }) { Text("Batal", color = FF.Primary) } }
        )
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            soundUri = it.toString()
            SoundHelper.playTestSound(context, it.toString())
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                soundUri = uri.toString()
                SoundHelper.playTestSound(context, uri.toString())
            }
        }
    }

    val title = when {
        first -> "Tambah Kolam Pertama"
        isNew -> "Tambah Kolam"
        else -> "Ubah Kolam"
    }

    Scaffold(
        containerColor = FF.Background,
        topBar = { FFTopBar(title, onBack = if (first) null else onBack) },
        bottomBar = {
            Surface(color = FF.Background) {
                Box(Modifier.padding(16.dp)) {
                    PrimaryButton(
                        "Simpan",
                        enabled = loaded && !saving,
                        onClick = {
                            val nameError = validatePondName(name)

                            if (nameError != null) {
                                nameTouched = true
                                Toast.makeText(
                                    context,
                                    nameError,
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@PrimaryButton
                            }

                            val error = validatePondSize(sizeText)

                            if (error != null) {
                                sizeError = error
                                return@PrimaryButton
                            }

                            saving = true
                            SoundHelper.stopSound()

                            scope.launch {
                                repo.savePondWithSchedules(
                                    Pond(
                                        id = pondId,
                                        name = name.trim(),
                                        sizeText = sizeText.trim().ifBlank { null },
                                        createdAt = createdAt,
                                        photoPath = photoPath,
                                        soundUri = soundUri
                                    ),
                                    times.map { it / 60 to it % 60 }
                                )

                                saved = true

                                if (originalPhoto != null && originalPhoto != photoPath) {
                                    PhotoStorage.delete(originalPhoto)
                                }

                                newPhotos.filter { it != photoPath }.forEach {
                                    PhotoStorage.delete(it)
                                }

                                onSaved()
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (first) {
                Text(
                    "Langkah terakhir: tambahkan kolam pertamamu dan atur jam pakannya. " +
                            "Alarm pengingat langsung aktif setelah disimpan.",
                    color = FF.TextMuted
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                PondPhoto(photoPath, Modifier.size(84.dp))
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Foto Kolam (opsional)", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showPhotoSourceDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) { Text(if (photoPath == null) "Tambah Foto" else "Ganti", fontSize = 13.sp) }
                        if (photoPath != null) {
                            TextButton(onClick = {
                                photoPath?.let { cur ->
                                    if (cur in newPhotos) {
                                        PhotoStorage.delete(cur)
                                        newPhotos.remove(cur)
                                    }
                                }
                                photoPath = null
                            }) { Text("Hapus", color = FF.NotFed, fontSize = 13.sp) }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(40)
                    nameTouched = true
                },
                label = { Text("Nama Kolam") },
                placeholder = { Text("Contoh: Kolam 1") },
                singleLine = true,
                isError = nameTouched && validatePondName(name) != null,
                supportingText = {
                    if (nameTouched) {
                        validatePondName(name)?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = sizeText,
                onValueChange = { input ->
                    val allowedPattern = Regex("[0-9.,xX×\\s]*")

                    if (input.length <= 30 && allowedPattern.matches(input)) {
                        sizeText = input
                        sizeError = validatePondSize(input)
                    }
                },
                label = { Text("Ukuran Kolam (m)") },
                placeholder = { Text("Contoh: 4 x 5") },
                supportingText = {
                    if (sizeError != null) {
                        Text(
                            sizeError!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("Masukkan panjang x lebar dalam meter.")
                    }
                },
                isError = sizeError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            FFCard(Modifier.fillMaxWidth()) {
                Text("Pengaturan Suara Alarm Pakan", fontWeight = FontWeight.SemiBold, color = FF.TextDark, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("Pilih variasi suara pengingat, nada dering HP, atau file audio milikmu sendiri:", color = FF.TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))

                val soundOptions = listOf(
                    "Pengumuman 1: \"Waktunya memberi pakan lele!\"" to SoundHelper.SOUND_VOICE_1,
                    "Pengumuman 2: \"Pakan tepat, lele sehat, hasil maksimal!\"" to SoundHelper.SOUND_VOICE_2,
                    "Pengumuman 3: \"Perhatian! Waktunya memberi pakan kolam!\"" to SoundHelper.SOUND_VOICE_3,
                    "Pengumuman 4: \"Halo Peternak, lele di kolam sudah lapar!\"" to SoundHelper.SOUND_VOICE_4,
                    "Pengumuman 5: \"Pengumuman: Segera berikan pakan ikan!\"" to SoundHelper.SOUND_VOICE_5,
                    "Pengumuman 6: \"Kasih pakan tepat waktu biar lele cepat panen!\"" to SoundHelper.SOUND_VOICE_6,
                    "Pengumuman 7: \"Selamat pagi! Jangan lupa kasih pakan lele.\"" to SoundHelper.SOUND_VOICE_7,
                    "Pengumuman 8: \"Waktunya jadwal pakan berikutnya.\"" to SoundHelper.SOUND_VOICE_8,
                    "Pengumuman 9: \"Permisi, lele di kolam sedang menunggu makan.\"" to SoundHelper.SOUND_VOICE_9,
                    "Pengumuman 10: \"Semangat peternak! Waktunya kasih makan lele.\"" to SoundHelper.SOUND_VOICE_10,
                    "Pengumuman 11: \"Waktunya pakan ikan lele!\"" to SoundHelper.SOUND_VOICE_11,
                    "Pengumuman 12: \"Jaga kedisiplinan pakan agar lele optimal.\"" to SoundHelper.SOUND_VOICE_12,
                    "Nada Alarm Bawaan HP" to SoundHelper.SOUND_TYPE_DEFAULT,
                    context.getString(R.string.sound_picker_system) to "RINGTONE_DIALOG",
                    context.getString(R.string.sound_picker_file) to "CUSTOM_FILE"
                )

                val currentLabel = SoundHelper.getSoundLabel(context, soundUri)

                fun openRingtoneDialog() {
                    try {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Pilih Nada Alarm Pakan")
                            val currentUri = try { Uri.parse(soundUri) } catch (_: Exception) { null }
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        }
                        ringtonePickerLauncher.launch(intent)
                    } catch (_: Exception) {
                        audioPickerLauncher.launch(arrayOf("audio/*"))
                    }
                }

                DropdownSelector(
                    value = currentLabel,
                    options = soundOptions,
                    onSelect = { option ->
                        when (option) {
                            "RINGTONE_DIALOG" -> openRingtoneDialog()
                            "CUSTOM_FILE" -> audioPickerLauncher.launch(arrayOf("audio/*"))
                            else -> {
                                soundUri = option
                                SoundHelper.playTestSound(context, option)
                            }
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = { SoundHelper.playTestSound(context, soundUri) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FF.Primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = context.getString(R.string.sound_test))
                    Spacer(Modifier.width(8.dp))
                    Text(context.getString(R.string.sound_test), fontSize = 13.sp)
                }
            }

            if (isNew) {
                var selectedTimeIndex by rememberSaveable { mutableIntStateOf(0) }

                Text("Jadwal Pakan", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = FF.TextDark)
                DropdownSelector(
                    value = if (times.isEmpty()) "Tanpa jadwal" else "${times.size}x sehari",
                    options = (1..6).map { "${it}x sehari" to it },
                    onSelect = { count ->
                        timesCsv = defaultTimes(count).joinToString(",")
                        selectedTimeIndex = 0
                    }
                )

                if (times.isNotEmpty()) {
                    val safeIndex = selectedTimeIndex.coerceIn(0, maxOf(0, times.size - 1))

                    Text(
                        "Ketuk jam pakan untuk memilih, lalu sesuaikan waktunya pada roda di bawah:",
                        color = FF.TextMuted,
                        fontSize = 12.sp
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        times.forEachIndexed { index, t ->
                            val isSelected = index == safeIndex
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isSelected) FF.Primary else FF.PrimaryLight,
                                border = BorderStroke(1.dp, if (isSelected) FF.Primary else FF.Border),
                                modifier = Modifier.clickable { selectedTimeIndex = index }
                            ) {
                                Row(
                                    Modifier.padding(start = 14.dp, end = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Pakan ${index + 1}: ${minutesLabel(t)}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) androidx.compose.ui.graphics.Color.White else FF.PrimaryDark,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                    if (times.size > 1) {
                                        IconButton(
                                            onClick = {
                                                val newTimes = times.toMutableList().apply { removeAt(index) }
                                                timesCsv = newTimes.joinToString(",")
                                                selectedTimeIndex = selectedTimeIndex.coerceIn(0, maxOf(0, newTimes.size - 1))
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                "Hapus jam",
                                                tint = if (isSelected) androidx.compose.ui.graphics.Color.White else FF.PrimaryDark,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (times.size < 6) {
                            OutlinedButton(
                                onClick = {
                                    val nextHour = (times.lastOrNull()?.let { (it / 60 + 3) % 24 } ?: 8) * 60
                                    val newTimes = (times + nextHour).sorted()
                                    timesCsv = newTimes.joinToString(",")
                                    selectedTimeIndex = newTimes.indexOf(nextHour).coerceAtLeast(0)
                                },
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.Add, null, tint = FF.Primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tambah Jam", color = FF.Primary, fontSize = 13.sp)
                            }
                        }
                    }

                    FFCard(Modifier.fillMaxWidth()) {
                        val currentMinutes = times[safeIndex]
                        val currentHour = currentMinutes / 60
                        val currentMinute = currentMinutes % 60

                        Text(
                            "Waktu Pakan ${safeIndex + 1}",
                            fontWeight = FontWeight.SemiBold,
                            color = FF.TextDark,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        WheelTimePicker(
                            hour = currentHour,
                            minute = currentMinute,
                            onHourChange = { newH: Int ->
                                val newTotal = newH * 60 + currentMinute
                                val newTimes = times.toMutableList()
                                newTimes[safeIndex] = newTotal
                                timesCsv = newTimes.joinToString(",")
                            },
                            onMinuteChange = { newM: Int ->
                                val newTotal = currentHour * 60 + newM
                                val newTimes = times.toMutableList()
                                newTimes[safeIndex] = newTotal
                                timesCsv = newTimes.joinToString(",")
                            }
                        )
                    }
                }
            } else {
                Text("Jadwal Pakan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Jam pakan dan status pengingat kolam ini diatur di layar Jadwal Pakan.",
                    color = FF.TextMuted,
                    fontSize = 13.sp
                )
                OutlinedButton(onClick = { onOpenSchedules(pondId) }, shape = RoundedCornerShape(12.dp)) {
                    Text("Atur Jadwal Pakan", color = FF.Primary)
                }
            }
        }
    }
}
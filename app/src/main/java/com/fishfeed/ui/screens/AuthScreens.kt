package com.fishfeed.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fishfeed.ui.components.FFCard
import com.fishfeed.ui.components.PrimaryButton
import com.fishfeed.ui.components.SectionTitle
import com.fishfeed.ui.theme.FF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import com.fishfeed.util.PhotoStorage
import kotlinx.coroutines.launch
import com.fishfeed.ui.components.PondPhoto
import androidx.activity.result.PickVisualMediaRequest

/** Logo ikan sederhana yang digambar dengan Canvas (tanpa file gambar). */
@Composable
fun FishLogo(modifier: Modifier = Modifier, color: Color = FF.Primary) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawOval(color, topLeft = Offset(w * 0.04f, h * 0.22f), size = Size(w * 0.68f, h * 0.56f))
        val tail = Path().apply {
            moveTo(w * 0.64f, h * 0.5f)
            lineTo(w * 0.97f, h * 0.2f)
            lineTo(w * 0.97f, h * 0.8f)
            close()
        }
        drawPath(tail, color)
        drawCircle(Color.White, w * 0.065f, Offset(w * 0.21f, h * 0.43f))
        drawCircle(FF.PrimaryDeep, w * 0.032f, Offset(w * 0.21f, h * 0.43f))
    }
}

private fun validateFullName(value: String): String? {
    val name = value.trim()

    if (name.isBlank()) {
        return "Nama lengkap wajib diisi."
    }

    if (name.length < 3) {
        return "Nama lengkap minimal 3 karakter."
    }

    if (name.length > 40) {
        return "Nama lengkap maksimal 40 karakter."
    }

    if (!Regex("[\\p{L}][\\p{L} .'-]*").matches(name)) {
        return "Nama hanya boleh berisi huruf dan tanda baca yang sesuai."
    }

    return null
}

private fun validatePondName(value: String): String? {
    val pond = value.trim()

    if (pond.isBlank()) {
        return "Nama kolam wajib diisi."
    }

    if (pond.length < 2) {
        return "Nama kolam minimal 2 karakter."
    }

    if (pond.length > 40) {
        return "Nama kolam maksimal 40 karakter."
    }

    if (!Regex("[\\p{L}0-9][\\p{L}0-9 _-]*").matches(pond)) {
        return "Nama kolam hanya boleh berisi huruf, angka, spasi, - dan _."
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

@Composable
fun SplashScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FF.AuthTop, FF.AuthBottom))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FishLogo(Modifier.size(110.dp))
            Spacer(Modifier.height(16.dp))
            Text("FishFeed", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = FF.PrimaryDark)
            Text("Pakan Tepat, Lele Sehat, Hasil Maksimal", fontSize = 14.sp, color = FF.TextMuted)
        }
    }
}

/**
 * Setup awal (satu halaman): Nama Lengkap peternak + Kolam Pertama (nama dan ukuran).
 * Jadwal awal 06.00, 12.00, dan 18.00 dibuat otomatis (bisa diubah di menu Jadwal).
 * Tidak ada login/akun.
 */
@Composable
fun SetupScreen(
    onSave: (
        name: String,
        pondName: String,
        pondSize: String,
        photoPath: String?,
        onComplete: () -> Unit
    ) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var pondName by rememberSaveable { mutableStateOf("") }
    var pondSize by rememberSaveable { mutableStateOf("") }

    var photoPath by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var showPhotoSourceDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val newPhotos = remember { mutableListOf<String>() }
    var saved by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (!saved) {
                newPhotos.forEach { PhotoStorage.delete(it) }
            }
        }
    }

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
                Toast.makeText(
                    context,
                    "Foto gagal dimuat, coba foto lain.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        handlePickedPhoto(uri)
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        handlePickedPhoto(uri)
    }

    var cameraUriString by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraUriString?.let { Uri.parse(it) }

        if (success && uri != null) {
            handlePickedPhoto(uri)
        }

        cameraUriString = null
    }

    fun openCamera() {
        try {
            val dir = java.io.File(context.cacheDir, "camera").apply {
                mkdirs()
            }

            val file = java.io.File(
                dir,
                "foto_${System.currentTimeMillis()}.jpg"
            )

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            cameraUriString = uri.toString()
            cameraLauncher.launch(uri)

        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Kamera tidak dapat dibuka.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = {
                showPhotoSourceDialog = false
            },
            title = {
                Text("Foto Kolam")
            },
            text = {
                Column {
                    @Composable
                    fun PhotoOption(
                        icon: ImageVector,
                        label: String,
                        onClick: () -> Unit
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPhotoSourceDialog = false
                                    onClick()
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null)
                            Spacer(Modifier.width(14.dp))
                            Text(label)
                        }
                    }

                    PhotoOption(
                        Icons.Filled.CameraAlt,
                        "Ambil foto dengan kamera"
                    ) {
                        openCamera()
                    }

                    PhotoOption(
                        Icons.Filled.PhotoLibrary,
                        "Pilih dari galeri"
                    ) {
                        picker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }

                    PhotoOption(
                        Icons.Filled.Folder,
                        "Pilih dari file"
                    ) {
                        filePicker.launch(arrayOf("image/*"))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showPhotoSourceDialog = false
                    }
                ) {
                    Text("Batal")
                }
            }
        )
    }

    val nameError = validateFullName(name)
    val pondNameError = validatePondName(pondName)
    val pondSizeError = validatePondSize(pondSize)

    val canSave = nameError == null &&
            pondNameError == null &&
            pondSizeError == null

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FF.AuthTop, FF.AuthBottomSetup))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FishLogo(Modifier.size(64.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                "Selamat Datang!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = FF.TextDark,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Mulai mengelola jadwal pakan ikan lele dengan mudah. Isi data dasar di bawah ini.",
                color = FF.TextMuted,
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(20.dp))

            FFCard(Modifier.fillMaxWidth()) {
                SectionTitle("Identitas Peternak")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(40)
                    },
                    label = { Text("Nama Lengkap") },
                    placeholder = { Text("Contoh: Budi Santoso") },
                    supportingText = {
                        if (name.isNotBlank() && nameError != null) {
                            Text(nameError, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    isError = name.isNotBlank() && nameError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next,
                        keyboardType = KeyboardType.Text
                    )
                )
            }

            Spacer(Modifier.height(12.dp))
            FFCard(Modifier.fillMaxWidth()) {
                SectionTitle("Kolam Pertama Anda")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pondName,
                    onValueChange = {
                        pondName = it.take(40)
                    },
                    label = { Text("Nama Kolam") },
                    placeholder = { Text("Contoh: Kolam 1") },
                    supportingText = {
                        if (pondName.isNotBlank() && pondNameError != null) {
                            Text(pondNameError, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    isError = pondName.isNotBlank() && pondNameError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next,
                        keyboardType = KeyboardType.Text
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pondSize,
                    onValueChange = { input ->
                        val allowedPattern = Regex("[0-9.,xX×\\s]*")

                        if (input.length <= 30 && allowedPattern.matches(input)) {
                            pondSize = input
                        }
                    },
                    label = { Text("Ukuran Kolam (m)") },
                    placeholder = { Text("Contoh: 4 x 5") },
                    supportingText = {
                        if (pondSize.isNotBlank() && pondSizeError != null) {
                            Text(pondSizeError, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Masukkan panjang dan lebar, contoh: 4 x 5")
                        }
                    },
                    isError = pondSize.isNotBlank() && pondSizeError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Text
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canSave) {
                                onSave(
                                    name.trim(),
                                    pondName.trim(),
                                    pondSize.trim(),
                                    photoPath
                                ) {
                                    saved = true
                                }
                            }
                        }
                    )
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Foto Kolam (Opsional)",
                    fontWeight = FontWeight.SemiBold,
                    color = FF.TextDark,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PondPhoto(
                        photoPath,
                        Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showPhotoSourceDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (photoPath == null) "Tambah Foto"
                            else "Ganti Foto"
                        )
                    }

                    if (photoPath != null) {
                        OutlinedButton(
                            onClick = {
                                photoPath?.let { current ->
                                    if (current in newPhotos) {
                                        PhotoStorage.delete(current)
                                        newPhotos.remove(current)
                                    }
                                }

                                photoPath = null
                            }
                        ) {
                            Text("Hapus")
                        }
                    }
                }

            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Jadwal pakan awal dibuat otomatis pukul 06.00, 12.00, dan 18.00. " +
                    "Kamu bisa mengubahnya kapan saja di menu Jadwal.",
                color = FF.TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                "Simpan dan Lanjutkan",
                onClick = {
                    if (canSave) {
                        onSave(
                            name.trim(),
                            pondName.trim(),
                            pondSize.trim(),
                            photoPath
                        ) {
                            saved = true
                        }
                    }
                },
                enabled = canSave
            )
        }
    }
}

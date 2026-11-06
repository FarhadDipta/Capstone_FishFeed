
package com.fishfeed.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fishfeed.ui.components.StatusChip
import com.fishfeed.ui.theme.FF

private const val MIN_SNOOZE = 1
private const val MAX_SNOOZE = 180

@Composable
fun SnoozeSheetContent(
    pondName: String,
    onActivate: (Int) -> Unit,
    onCancel: () -> Unit
) {
    var minutesText by rememberSaveable {
        mutableStateOf("10")
    }

    val parsedMinutes = minutesText.toIntOrNull()
    val isValid = parsedMinutes != null &&
            parsedMinutes in MIN_SNOOZE..MAX_SNOOZE

    val minutes = parsedMinutes ?: MIN_SNOOZE

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Tunda Pemberian Pakan",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = FF.TextDark
                )
                Text(
                    "Alarm akan berbunyi kembali sesuai durasi jeda yang Anda tentukan.",
                    fontSize = 12.sp,
                    color = FF.TextMuted
                )
            }

            if (pondName.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                StatusChip(
                    pondName,
                    FF.PrimaryDark,
                    FF.PrimaryLight
                )
            }
        }

        Text(
            "PILIHAN CEPAT JEDA",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = FF.TextMuted
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30).forEach { value ->
                val selected = parsedMinutes == value

                Column(
                    Modifier
                        .weight(1f)
                        .height(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) FF.PrimaryLight
                            else FF.Surface
                        )
                        .border(
                            BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) FF.Primary else FF.Border
                            ),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            minutesText = value.toString()
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "$value",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = FF.TextDark
                    )
                    Text(
                        "Menit",
                        fontSize = 11.sp,
                        color = FF.TextMuted
                    )
                }
            }
        }

        Surface(
            color = FF.Surface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, FF.Border)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Kustom Waktu Jeda",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = FF.TextDark
                )

                Text(
                    "Masukkan durasi antara 1 sampai 180 menit.",
                    fontSize = 11.sp,
                    color = FF.TextMuted
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = {
                            if (isValid && minutes > MIN_SNOOZE) {
                                minutesText = (minutes - 1).toString()
                            }
                        },
                        enabled = isValid && minutes > MIN_SNOOZE
                    ) {
                        Icon(
                            Icons.Filled.Remove,
                            contentDescription = "Kurangi menit",
                            tint = FF.Primary
                        )
                    }

                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } &&
                                input.length <= 3
                            ) {
                                minutesText = input
                            }
                        },
                        modifier = Modifier.width(120.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        isError = !isValid,
                        suffix = {
                            Text("mnt")
                        }
                    )

                    IconButton(
                        onClick = {
                            if (isValid && minutes < MAX_SNOOZE) {
                                minutesText = (minutes + 1).toString()
                            }
                        },
                        enabled = isValid && minutes < MAX_SNOOZE
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Tambah menit",
                            tint = FF.Primary
                        )
                    }
                }

                if (!isValid) {
                    Text(
                        "Masukkan angka 1-180.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Surface(
            color = FF.LateBg,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.dp,
                FF.Late.copy(alpha = 0.4f)
            )
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    "Alur Pengingat Lanjutan:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = FF.Late
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    "Saat jeda berakhir, alarm berbunyi lagi dengan pilihan Sudah Diberi Pakan (status Terlambat) atau Tidak Diberi Pakan.",
                    fontSize = 12.sp,
                    color = FF.TextDark
                )
            }
        }

        Button(
            onClick = {
                if (isValid) {
                    onActivate(minutes)
                }
            },
            enabled = isValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FF.Primary
            )
        ) {
            Text(
                "Aktifkan Jeda ($minutes Menit)",
                fontWeight = FontWeight.SemiBold
            )
        }

        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Kembali ke Pengingat",
                color = FF.TextMuted
            )
        }
    }
}

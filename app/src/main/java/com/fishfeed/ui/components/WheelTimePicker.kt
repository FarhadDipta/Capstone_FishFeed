package com.fishfeed.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fishfeed.ui.theme.FF

private val ItemHeight = 48.dp
private const val VisibleItems = 5

/** Dialog pemilih jam dengan roda geser (disamakan dengan pemilih jam di kelola jadwal). */
@Composable
fun WheelTimePickerDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hour by remember { mutableIntStateOf(initialMinutes / 60) }
    var minute by remember { mutableIntStateOf(initialMinutes % 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Jam Pakan", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = FF.TextDark) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WheelTimePicker(
                    hour = hour,
                    minute = minute,
                    onHourChange = { hour = it },
                    onMinuteChange = { minute = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(hour * 60 + minute) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FF.Primary)
            ) {
                Text("Pilih", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = FF.TextMuted)
            }
        },
        containerColor = FF.Surface,
        shape = RoundedCornerShape(20.dp)
    )
}

/** Pemilih jam bentuk roda (geser atas/bawah): roda jam 00-23 dan roda menit 00-59. */
@Composable
fun WheelTimePicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(ItemHeight * VisibleItems)
            .background(FF.Surface, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Pita sorotan di tengah
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(ItemHeight)
                .background(FF.PrimaryLight, RoundedCornerShape(12.dp))
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            WheelColumn(24, hour, onHourChange, Modifier.width(100.dp))
            Text(":", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = FF.PrimaryDark)
            WheelColumn(60, minute, onMinuteChange, Modifier.width(100.dp))
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    count: Int,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeSelected = selected.coerceIn(0, count - 1)

    val itemPx = with(LocalDensity.current) {
        ItemHeight.toPx()
    }

    // Posisi awal di tengah daftar agar roda bisa berputar
    // berkali-kali ke atas maupun ke bawah.
    val middleIndex = Int.MAX_VALUE / 2
    val initialIndex =
        middleIndex - (middleIndex % count) + safeSelected

    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex
    )

    val fling = rememberSnapFlingBehavior(
        lazyListState = state
    )

    val centered by remember {
        derivedStateOf {
            val extra =
                if (state.firstVisibleItemScrollOffset > itemPx / 2) 1
                else 0

            val actualIndex =
                state.firstVisibleItemIndex + extra

            actualIndex % count
        }
    }

    // Ketika roda digeser, kirim angka yang dipilih.
    LaunchedEffect(centered) {
        if (centered != safeSelected) {
            onSelected(centered)
        }
    }

    // Jika nilai berubah dari luar, geser ke angka tersebut
    // dengan mengambil jarak putar terpendek.
    LaunchedEffect(safeSelected) {
        if (!state.isScrollInProgress && centered != safeSelected) {
            val difference =
                (safeSelected - centered + count / 2) % count -
                        count / 2

            val targetIndex =
                state.firstVisibleItemIndex + difference

            state.scrollToItem(targetIndex.coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(
            vertical = ItemHeight * 2
        ),
        modifier = modifier.height(
            ItemHeight * VisibleItems
        )
    ) {
        items(Int.MAX_VALUE) { index ->

            val value = index % count
            val isCenter = value == centered

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ItemHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "%02d".format(value),
                    fontSize = if (isCenter) 28.sp else 20.sp,
                    fontWeight = if (isCenter) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                    color = if (isCenter) {
                        FF.PrimaryDark
                    } else {
                        FF.TextMuted
                    }
                )
            }
        }
    }
}

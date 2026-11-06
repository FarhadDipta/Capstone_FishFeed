package com.fishfeed.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** Pilihan tampilan aplikasi. */
enum class ThemeMode(val label: String) {
    SYSTEM("Ikuti Sistem"),
    LIGHT("Terang"),
    DARK("Gelap")
}

/** Menyimpan pilihan tema di SharedPreferences (tetap tersimpan setelah aplikasi ditutup). */
object ThemePrefs {
    private const val FILE = "fishfeed_ui"
    private const val KEY = "theme_mode"
    private var prefs: SharedPreferences? = null

    /** Pilihan saat ini. Berupa state Compose, jadi seluruh layar otomatis berganti saat diubah. */
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        mode = try {
            ThemeMode.valueOf(p.getString(KEY, null) ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun set(newMode: ThemeMode) {
        mode = newMode
        prefs?.edit()?.putString(KEY, newMode.name)?.apply()
    }
}

/**
 * Palet warna FishFeed (hijau/teal). Setiap warna berganti otomatis antara versi terang dan gelap
 * mengikuti [isDark] yang diatur oleh [FishFeedTheme].
 */
object FF {
    /** true = mode gelap sedang aktif. Diatur oleh FishFeedTheme. */
    var isDark by mutableStateOf(false)

    private fun pick(light: Long, dark: Long): Color = Color(if (isDark) dark else light)

    val Primary: Color get() = pick(0xFF1B7F6B, 0xFF23917B)
    /** Hijau tua tetap (untuk latar gradien yang selalu berteks putih). */
    val PrimaryDeep: Color = Color(0xFF0F5C4D)
    /** Warna teks/ikon hijau pada permukaan (terang: hijau tua, gelap: hijau muda). */
    val PrimaryDark: Color get() = pick(0xFF0F5C4D, 0xFFA8E6D8)
    val PrimaryLight: Color get() = pick(0xFFD6EFE9, 0xFF1E3B35)
    val Background: Color get() = pick(0xFFF2FAF9, 0xFF0E1513)
    val Surface: Color get() = pick(0xFFFFFFFF, 0xFF18211F)
    val Border: Color get() = pick(0xFFD3E8E4, 0xFF2B3A37)
    val TextDark: Color get() = pick(0xFF12332D, 0xFFE6F2EF)
    val TextMuted: Color get() = pick(0xFF5E7A75, 0xFF9DB5B0)

    val OnTime: Color get() = pick(0xFF1F9A62, 0xFF4CCB8A)
    val OnTimeBg: Color get() = pick(0xFFDDF4E8, 0xFF17382A)
    val Late: Color get() = pick(0xFFD9772B, 0xFFF0A05A)
    val LateBg: Color get() = pick(0xFFFCE9D6, 0xFF3D2B18)
    val NotFed: Color get() = pick(0xFFD64545, 0xFFF27C7C)
    val NotFedBg: Color get() = pick(0xFFFBE0E0, 0xFF3F1E1E)
    val Neutral: Color get() = pick(0xFF5E7A75, 0xFF9DB5B0)
    val NeutralBg: Color get() = pick(0xFFE6EFED, 0xFF24302D)

    /** Gradien latar layar Splash / Setup Awal. */
    val AuthTop: Color get() = pick(0xFFEAF8F6, 0xFF0F1C1A)
    val AuthBottom: Color get() = pick(0xFFB9E3EA, 0xFF16312F)
    val AuthBottomSetup: Color get() = pick(0xFFD3EEF2, 0xFF14292A)
    /** Ujung gradien foto kolam kosong. */
    val PhotoEnd: Color get() = pick(0xFFBFE3EA, 0xFF1B3A3F)
}

private val FishFeedTypography = Typography(
    displayMedium = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
)

private val FishFeedShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
)

private fun buildScheme(dark: Boolean) =
    if (dark) {
        darkColorScheme(
            primary = FF.Primary,
            onPrimary = Color.White,
            primaryContainer = FF.PrimaryLight,
            onPrimaryContainer = FF.PrimaryDark,
            secondary = FF.PrimaryDark,
            background = FF.Background,
            onBackground = FF.TextDark,
            surface = FF.Surface,
            onSurface = FF.TextDark,
            surfaceVariant = FF.NeutralBg,
            onSurfaceVariant = FF.TextMuted,
            outline = FF.Border,
            error = FF.NotFed
        )
    } else {
        lightColorScheme(
            primary = FF.Primary,
            onPrimary = Color.White,
            primaryContainer = FF.PrimaryLight,
            onPrimaryContainer = FF.PrimaryDark,
            secondary = FF.PrimaryDark,
            background = FF.Background,
            onBackground = FF.TextDark,
            surface = FF.Surface,
            onSurface = FF.TextDark,
            surfaceVariant = FF.NeutralBg,
            onSurfaceVariant = FF.TextMuted,
            outline = FF.Border,
            error = FF.NotFed
        )
    }

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun FishFeedTheme(content: @Composable () -> Unit) {
    val dark = when (ThemePrefs.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // Atur dulu sebelum warna dibaca oleh layar di bawahnya.
    if (FF.isDark != dark) FF.isDark = dark
    val scheme = buildScheme(dark)

    // Warna bilah navigasi sistem ikut berganti (ikon terang/gelap menyesuaikan).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window
            if (window != null) {
                window.navigationBarColor = scheme.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = FishFeedTypography,
        shapes = FishFeedShapes,
        content = content
    )
}

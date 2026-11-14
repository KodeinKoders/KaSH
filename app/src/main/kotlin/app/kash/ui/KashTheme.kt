package app.kash.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.lightWithLightHeader
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.styling.TitleBarStyle
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme

/** The colors of the design (`--bg`, `--panel`… of its artboards), for what Jewel's components do not draw themselves. */
class KashColors(
    val background: Color,
    val panel: Color,
    val card: Color,
    val separator: Color,
    val line: Color,
    val hover: Color,
    val rowHover: Color,
    val chip: Color,
    val divider: Color,
    val control: Color,
    val dashed: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val selection: Color,
    val link: Color,
    val field: Color,
    val neutral: Color,
    val error: Color,
) {
    /** The red of the unread badges. */
    val badge: Color = Color(0xFFD9363E)

    /** The blue of the selection: the rail's indicator and ring, the focus. */
    val accent: Color = Color(0xFF3574F0)

    /** The blue of the counts in the Unread tab. */
    val count: Color = Color(0xFF2F64D6)

    companion object {
        val Dark = KashColors(
            background = Color(0xFF1E1F22), panel = Color(0xFF2B2D30), card = Color(0xFF2B2D30), separator = Color(0xFF1E1F22),
            line = Color(0xFF393B40), hover = Color(0xFF393B40), rowHover = Color(0xFF34363B), chip = Color(0xFF43454A), divider = Color(0xFF43454A),
            control = Color(0xFF4E5157), dashed = Color(0xFF5A5D63), text = Color(0xFFDFE1E5), muted = Color(0xFF9DA0A8), faint = Color(0xFF868A91),
            selection = Color(0xFF2E436E), link = Color(0xFF548AF7), field = Color(0xFF2B2D30), neutral = Color(0xFF393B40), error = Color(0xFFF2777E),
        )

        val Light = KashColors(
            background = Color(0xFFFFFFFF), panel = Color(0xFFF7F8FA), card = Color(0xFFFFFFFF), separator = Color(0xFFEBECF0),
            line = Color(0xFFEBECF0), hover = Color(0xFFDFE1E5), rowHover = Color(0xFFF5F6F8), chip = Color(0xFFEBECF0), divider = Color(0xFFDFE1E5),
            control = Color(0xFFC9CCD6), dashed = Color(0xFFA8ADBD), text = Color(0xFF1E1F22), muted = Color(0xFF6C707E), faint = Color(0xFF6C707E),
            selection = Color(0xFFD4E2FF), link = Color(0xFF2F64D6), field = Color(0xFFFFFFFF), neutral = Color(0xFFDFE1E5), error = Color(0xFFC42B33),
        )
    }
}

val LocalKashColors = staticCompositionLocalOf { KashColors.Light }

/** The colors of the current theme. */
object Kash {
    val colors: KashColors
        @Composable get() = LocalKashColors.current
}

/**
 * Jewel's Int UI theme, light or dark, with the colors of the design next to it. The title bar's style is Jewel's, which the decorated windows
 * read: the light one has a light header, as the design draws it.
 */
@Composable
fun KashTheme(dark: Boolean, content: @Composable () -> Unit) {
    IntUiTheme(
        theme = if (dark) JewelTheme.darkThemeDefinition() else JewelTheme.lightThemeDefinition(),
        styling = ComponentStyling.default().decoratedWindow(titleBarStyle = if (dark) TitleBarStyle.dark() else TitleBarStyle.lightWithLightHeader()),
    ) {
        CompositionLocalProvider(LocalKashColors provides if (dark) KashColors.Dark else KashColors.Light, content = content)
    }
}

// How often the appearance of the system is read again: what Compose does in its windows.
private const val SYSTEM_THEME_POLL_MS = 1_000L

/**
 * Whether the system is dark, read again every second while [follow] is true, so that KaSH follows macOS when its appearance changes.
 *
 * Compose's `isSystemInDarkTheme()` does that only inside a window, and the theme of the application is decided outside them (as in KaTTY).
 */
@Composable
fun rememberSystemDark(follow: Boolean): State<Boolean> = produceState(currentSystemTheme == SystemTheme.DARK, follow) {
    if (!follow) return@produceState
    while (true) {
        value = withContext(Dispatchers.IO) { currentSystemTheme == SystemTheme.DARK }
        delay(SYSTEM_THEME_POLL_MS)
    }
}

package app.kash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import app.kash.ui.Kash
import app.kash.ui.UiScaled
import app.kash.ui.Strings
import app.kash.ui.strings
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.awt.EventQueue
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val logger = LoggerFactory.getLogger("app.kash.AboutWindow")

/** Where the credits of the About window link to. */
internal const val KODEIN_KODERS_URL = "https://kodein-koders.com"

/** What the About window says of the version: the project's, and the commit it was built from (see `buildConfig` in `app/build.gradle.kts`). */
internal fun versionText(strings: Strings, version: String = BuildConfig.VERSION, commit: String = BuildConfig.GIT_COMMIT): String =
    if (commit.isEmpty() || commit == "unknown") strings.aboutVersion(version) else strings.aboutVersion("$version ($commit)")

/**
 * What the About window says of the branch it was built on (see `buildConfig` in `app/build.gradle.kts`): `Branch feature/x`, and `null` for main,
 * which is what is distributed, and for a build of no branch, which is not shown.
 */
internal fun branchText(strings: Strings, branch: String = BuildConfig.GIT_BRANCH): String? =
    if (branch.isEmpty() || branch == "main") null else strings.aboutBranch(branch)

/**
 * What the About window says of when the commit it was built from was made (see `buildConfig` in `app/build.gradle.kts`), in the user's time zone,
 * written the same in every language (`2026-11-03 19:43`); `null` when the date cannot be read, and it is not shown.
 */
internal fun commitDateText(strings: Strings, date: String = BuildConfig.GIT_COMMIT_DATE, zone: ZoneId = ZoneId.systemDefault()): String? = try {
    strings.aboutCommitted(COMMIT_DATE_FORMAT.format(OffsetDateTime.parse(date).atZoneSameInstant(zone)))
} catch (e: DateTimeParseException) {
    null
}

private val COMMIT_DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")

/**
 * Makes the About item of the application menu of macOS call [onAbout], so that it shows [AboutWindow] and not the system's panel, which for a
 * Java program says Java. Nothing is done elsewhere, and a failure leaves the system's panel.
 */
internal fun takeAboutItem(onAbout: () -> Unit) {
    if (!System.getProperty("os.name").startsWith("Mac")) return
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_ABOUT)) {
            Desktop.getDesktop().setAboutHandler { onAbout() }
        }
    } catch (e: Throwable) {
        logger.warn("Could not take the About item of the application menu", e)
    }
}

/**
 * Makes the Settings item of the application menu of macOS (⌘, there), after the About item, call [onSettings], which opens Settings, as KaTTY's
 * does. Nothing is done elsewhere, and a failure leaves the item disabled, as it was.
 */
internal fun takeSettingsItem(onSettings: () -> Unit) {
    if (!System.getProperty("os.name").startsWith("Mac")) return
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_PREFERENCES)) {
            Desktop.getDesktop().setPreferencesHandler { EventQueue.invokeLater(onSettings) }
        }
    } catch (e: Throwable) {
        logger.warn("Could not take the Settings item of the application menu", e)
    }
}

/**
 * The About window: the logo, the name, the version and the commit, and who made it, with a link. A window of its own, which is closed with its
 * button, Escape or ⌘W; it does not keep anything.
 */
@Composable
internal fun AboutWindow(onClose: () -> Unit) {
    val title = strings.aboutTitle(APP_NAME)
    val state = rememberWindowState(position = WindowPosition(Alignment.Center), size = DpSize(ABOUT_SIZE.width, ABOUT_SIZE.height + TITLE_BAR_HEIGHT))
    DecoratedWindow(
        onCloseRequest = onClose,
        title = title,
        icon = remember { AppIcon.painter() },
        state = state,
        resizable = false,
        onPreviewKeyEvent = { event ->
            val close = event.type == KeyEventType.KeyDown &&
                (event.key == Key.Escape || event.isMetaPressed && !event.isShiftPressed && !event.isAltPressed && event.key == Key.W)
            if (close) onClose()
            close
        },
    ) {
        // The size of the text that Settings › Appearance gives, inside the window (UiScaled).
        UiScaled {
            TitleBar { Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.CenterHorizontally)) }
            AboutContent()
        }
    }
}

/** The size of what the About window shows under its title bar. */
internal val ABOUT_SIZE = DpSize(360.dp, 340.dp)

// The height of Jewel's title bar.
private val TITLE_BAR_HEIGHT = 40.dp

/** What the About window shows, apart from the window; [branch] is the one it was built on. */
@Composable
internal fun AboutContent(modifier: Modifier = Modifier, branch: String = BuildConfig.GIT_BRANCH) {
    val colors = Kash.colors
    val strings = strings
    val logo = remember { AppIcon.painter() }
    Column(
        modifier.fillMaxSize().background(colors.panel).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (logo != null) Image(logo, contentDescription = strings.aboutLogo(APP_NAME), modifier = Modifier.size(120.dp))
        Spacer(Modifier.height(12.dp))
        Text(APP_NAME, color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(versionText(strings), color = colors.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        branchText(strings, branch)?.let { text ->
            Spacer(Modifier.height(2.dp))
            // A branch can have a name of any length: it does not push what follows out of the window.
            Text(text, color = colors.muted, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        commitDateText(strings)?.let { committed ->
            Spacer(Modifier.height(2.dp))
            Text(committed, color = colors.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            buildAnnotatedString {
                append(strings.aboutMadeBy)
                withLink(
                    LinkAnnotation.Url(
                        KODEIN_KODERS_URL,
                        TextLinkStyles(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)),
                    ),
                ) { append("Kodein Koders") }
            },
            color = colors.text,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

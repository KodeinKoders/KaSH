package app.kash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.kash.browser.BrowserEngine
import app.kash.browser.BrowserShortcut
import app.kash.browser.ColorScheme
import app.kash.core.Inbox
import app.kash.core.KashDirectories
import app.kash.core.PageLanguages
import app.kash.core.Plugin
import app.kash.core.Service
import app.kash.core.StateFile
import app.kash.core.ThemeMode
import app.kash.core.WindowFile
import app.kash.core.WindowGeometry
import app.kash.core.deleteClosedProfiles
import app.kash.macos.inAppBundle
import app.kash.ui.KashTheme
import app.kash.ui.LocalUiScale
import app.kash.ui.UiScaled
import app.kash.core.UiScales
import app.kash.ui.LocalAwtWindow
import app.kash.ui.LocalStrings
import app.kash.ui.rememberSystemDark
import app.kash.ui.stringsOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.awt.Taskbar
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.swing.JOptionPane
import kotlin.system.exitProcess
import java.nio.file.Paths

private val logger = LoggerFactory.getLogger("app.kash.Main")

/** The system property that opens Chromium's remote debugging on that port: set by `./gradlew :app:run -Pkash.inspect`, for development only. */
private const val INSPECT_PROPERTY = "kash.inspect.port"

// How long quitting may take before the process is stopped: closing each profile waits 5 s at most for its cookies, and Chromium 20 s at most.
private val QUIT_TIMEOUT = 60.seconds

fun main() {
    // Before anything uses AWT: the menu bar of macOS is named by what this says when it starts.
    nameApplication()
    logger.info("KaSH runs on {} {} in {}", System.getProperty("java.vendor"), System.getProperty("java.runtime.version"), System.getProperty("java.home"))
    // The icon of the Dock and of the application switcher, instead of the one of Java (KaSH.app has it from its bundle).
    AppIcon.installInDock()
    val directories = KashDirectories.default()
    val file = StateFile(directories.stateFile)
    // One KaSH for one support directory: Chromium locks its profiles for the first one, and a second one would show services that are not the
    // logged-in ones, and overwrite the first one's state.
    if (!lockSupport(directories)) {
        logger.error("KaSH already runs with {}", directories.support)
        JOptionPane.showMessageDialog(null, stringsOf(file.load().settings.language).alreadyRunning, "KaSH", JOptionPane.WARNING_MESSAGE)
        exitProcess(1)
    }
    // KaSH sometimes could not be quit, which could not be made to happen again: what the threads do is written when Swing's thread is stuck.
    watchEventThread(directories.hangs)
    // The profiles of the services that were deleted or erased are deleted before Chromium starts, which could still hold their files.
    val state = deleteClosedProfiles(file.load(), directories).also(file::save)
    // On this thread, not Swing's: JCEF initializes on Swing's thread, and this waits for it.
    // Chromium's own menus and error pages are in KaSH's language when it starts (they change at the next launch); the pages' languages are
    // given by KashModel.
    val engine = BrowserEngine.start(jcefBundle(), directories.profiles, inspectPort(), PageLanguages.locale(stringsOf(state.settings.language).code))
    val shim = resource("notification-shim.js")
    // The built-in plugins, then those that the user installed (a built-in plugin's id cannot be installed).
    val builtInPlugins = BuiltInPlugins.plugins()
    val installer = PluginInstaller(directories, builtInPlugins.map { it.id }.toSet())
    val installedPlugins = installer.plugins()
    // Every page gets the notifications; a plugin's service also gets its plugin's script, with the API that it reads the page with.
    val scriptsFor = { service: Service, plugin: Plugin?, language: String ->
        listOf(shim) + listOfNotNull(plugin?.let { PluginScripts.of(it, service.options, language) })
    }
    // The main window opens where it was, as big and in the mode it was in when KaSH was closed, on a screen that is still there.
    val windowFile = WindowFile(directories.windowFile)
    val geometry = windowFile.load().sanitized(screens())
    // The pages that are not shown yet are laid out at the window's size, so that their plugins have something to read.
    engine.setHiddenPageSize(geometry.width.toInt(), geometry.height.toInt())
    application {
        val model = remember {
            KashModel(state, file, directories, engine, builtInPlugins, installer, installedPlugins, scriptsFor, initialColorScheme(state.settings.theme))
        }
        val icons = remember {
            ServiceIcons(directories) { model.plugins }.also { icons ->
                model.onIcon = icons::report
                model.onPluginChanged = icons::forgetPluginIcon
            }
        }
        // KaSH's shortcuts in a page, which the page does not get (PageShortcuts).
        DisposableEffect(Unit) {
            engine.onShortcut = { model.shortcut(it, ShortcutSource.PAGE) }
            onDispose { engine.onShortcut = {} }
        }
        Kash(engine, model, icons, windowFile, geometry, directories.hangs)
    }
}

/** The lock of the support directory, held as long as the process runs: the system releases it when it ends, however it ends. */
private var supportLock: FileLock? = null

/** Takes the lock of the support directory: false when another KaSH holds it. */
private fun lockSupport(directories: KashDirectories): Boolean {
    Files.createDirectories(directories.support)
    val channel = FileChannel.open(directories.support.resolve("kash.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    supportLock = try {
        channel.tryLock()
    } catch (e: OverlappingFileLockException) {
        null
    }
    if (supportLock == null) channel.close()
    return supportLock != null
}

/** The color scheme of [theme] when KaSH starts: given to the pages before they are loaded, so that they never show another one. */
private fun initialColorScheme(theme: ThemeMode): ColorScheme {
    val dark = when (theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> currentSystemTheme == SystemTheme.DARK
    }
    return if (dark) ColorScheme.DARK else ColorScheme.LIGHT
}

/** The text of the resource [name] of `app/kash/`. */
private fun resource(name: String): String =
    requireNotNull(object {}.javaClass.getResource("/app/kash/$name")) { "$name is not in the resources" }.readText()

/**
 * The port of Chromium's remote debugging, when the run task asked for it ([INSPECT_PROPERTY]). Never in KaSH.app: remote debugging lets any
 * program of the machine read and drive the logged-in services.
 */
private fun inspectPort(): Int? {
    val port = System.getProperty(INSPECT_PROPERTY)?.toIntOrNull() ?: return null
    if (inAppBundle) {
        logger.error("Remote debugging is for development only: {} is ignored in the application bundle", INSPECT_PROPERTY)
        return null
    }
    return port
}

/**
 * Where the JCEF native bundle is: in the resources of the application (Compose sets `compose.application.resources.dir` for the packaged
 * application and for the run task), or where `kash.jcef.bundle` says.
 */
private fun jcefBundle(): Path {
    System.getProperty("kash.jcef.bundle")?.takeIf { it.isNotBlank() }?.let { return Paths.get(it) }
    val resources = System.getProperty("compose.application.resources.dir")
        ?: error("No JCEF bundle: run KaSH with `./gradlew :app:run`, or give its path with -Dkash.jcef.bundle")
    return Paths.get(resources, "jcef")
}

@Composable
private fun ApplicationScope.Kash(engine: BrowserEngine, model: KashModel, icons: ServiceIcons, windowFile: WindowFile, geometry: WindowGeometry, hangs: Path) {
    val windowState = rememberWindowState(placement = geometry.mode.toPlacement(), position = geometry.toPosition(), size = geometry.toSize())
    // Where the window is, how big and in which mode is kept as it changes, and a last time when KaSH quits.
    val flushWindow = keepWindowGeometry(windowState, windowFile, geometry)
    val scope = rememberCoroutineScope()
    val notifier = remember { Notifier(model) }
    // Once, whichever asks first: the window's close button, or the system's Quit (⌘Q, the Dock, logging out).
    val quitting = remember { booleanArrayOf(false) }
    val quit: () -> Unit = {
        if (!quitting[0]) {
            quitting[0] = true
            logger.info("Quitting")
            // A quit that does not end is stopped, rather than leave KaSH that can only be forced to quit by the system.
            haltIfNotQuitIn(QUIT_TIMEOUT, hangs)
            // Each step goes on when the one before fails: one that threw left KaSH idle and unable to quit, every next Quit being ignored.
            runCatching { model.save() }.onFailure { logger.error("The state could not be saved", it) }
            runCatching { flushWindow() }.onFailure { logger.error("The window could not be saved", it) }
            scope.launch {
                try {
                    // Chromium writes what the profiles still hold, then stops: off Swing's thread, which JCEF finishes the shutdown on.
                    withContext(Dispatchers.IO) { engine.shutdown() }
                } catch (e: Throwable) {
                    logger.error("Chromium could not be stopped", e)
                }
                runCatching { model.close() }.onFailure { logger.error("The model could not be closed", it) }
                logger.info("Closing the windows")
                exitApplication()
            }
        }
    }
    DisposableEffect(Unit) {
        // The system's Quit would end the process without stopping Chromium, which can lose the last writes of the profiles: it quits as the
        // close button does instead.
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            Desktop.getDesktop().setQuitHandler { _, response ->
                response.cancelQuit()
                quit()
            }
        }
        onDispose { }
    }
    // The About window, from the About item of the application menu of macOS, which would show the system's panel (Java's), and from Settings.
    DisposableEffect(Unit) {
        takeAboutItem { java.awt.EventQueue.invokeLater { model.aboutOpen = true } }
        onDispose { }
    }
    // Settings, from the Settings item of the application menu of macOS (⌘,), as the rail's gear opens it.
    DisposableEffect(Unit) {
        takeSettingsItem { model.settingsOpen = true }
        onDispose { }
    }

    val settings = model.state.settings
    val systemDark by rememberSystemDark(follow = settings.theme == ThemeMode.SYSTEM)
    val dark = when (settings.theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    // The pages of the services see KaSH's theme as the system's.
    LaunchedEffect(dark) { model.colorScheme = if (dark) ColorScheme.DARK else ColorScheme.LIGHT }

    // The badge of the Dock's icon: the total of the tabs' badges, a dot, or nothing.
    val dockBadge = Inbox.dockBadge(Inbox.badges(model.state.services, model.unread, settings), settings)
    LaunchedEffect(dockBadge) {
        val supported = Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_BADGE_TEXT)
        logger.debug("Dock badge: {}{}", dockBadge, if (supported) "" else " (not supported)")
        if (supported) Taskbar.getTaskbar().setIconBadge(dockBadge)
    }

    CompositionLocalProvider(LocalStrings provides stringsOf(settings.language), LocalUiScale provides UiScales.nearest(settings.uiScale) / 100f) {
        KashTheme(dark) {
            DecoratedWindow(
                onCloseRequest = quit,
                title = "KaSH",
                icon = remember { AppIcon.painter() },
                state = windowState,
                // KaSH's shortcuts while its own views have the focus (the Unread tab, the rail): a page's go to engine.onShortcut.
                onPreviewKeyEvent = { event ->
                    val shortcut = windowShortcutOf(event)
                    shortcut?.let(model::shortcut)
                    shortcut != null
                },
            ) {
                // The size of the text that Settings › Appearance gives, inside the window (UiScaled).
                UiScaled {
                    DisposableEffect(window) {
                        model.activateWindow = {
                            window.isVisible = true
                            // toFront does not activate KaSH when another application is in front: a click on one of KaSH's own notifications,
                            // which do not activate it (ToastWindow), has to.
                            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                                Desktop.getDesktop().requestForeground(true)
                            }
                            window.toFront()
                            window.requestFocus()
                        }
                        onDispose { model.activateWindow = {} }
                    }
                    CompositionLocalProvider(LocalAwtWindow provides window) {
                        MainWindowContent(model, icons, systemDark)
                    }
                    if (model.searchOpen) {
                        SearchWindow(
                            window, model.searchEntries, icons,
                            onOpen = { entry ->
                                model.dismissSearch()
                                model.openConversation(entry.service, entry.conversation)
                            },
                            onDismiss = model::dismissSearch,
                            priority = PriorityActions(
                                isPriority = { entry -> model.isPriority(entry.service.id, entry.conversation) },
                                onToggle = { entry -> model.togglePriority(entry.service.id, entry.conversation, entry.name) },
                            ),
                        )
                    }
                }
            }
            ToastWindow(notifier, icons)
            if (model.addServiceOpen) {
                val editing = model.editing?.let(model::service)
                AddServiceWindow(
                    editing = editing,
                    icons = icons,
                    onClose = { model.addServiceOpen = false; model.editing = null },
                    onDone = { service ->
                        if (editing == null) model.add(service) else model.replace(service)
                        model.addServiceOpen = false
                        model.editing = null
                    },
                )
            }
            if (model.aboutOpen) AboutWindow(onClose = { model.aboutOpen = false })
            if (model.settingsOpen) {
                SettingsWindow(model, notifier, icons, onClose = { model.settingsOpen = false; model.cacheReset.clear() })
            }
            model.confirmation?.let { confirmation ->
                ConfirmDialog(
                    confirmation,
                    onConfirm = {
                        when (confirmation) {
                            is Confirmation.Delete -> model.delete(confirmation.service.id)
                            is Confirmation.Erase -> model.eraseData(confirmation.service.id)
                        }
                        model.confirmation = null
                    },
                    onDismiss = { model.confirmation = null },
                )
            }
            model.pluginUpdateFailure?.let { failure -> PluginUpdateFailureDialog(failure, onDismiss = { model.pluginUpdateFailure = null }) }
        }
    }
}

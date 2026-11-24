package app.kash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import app.kash.core.InstalledPlugin
import app.kash.core.Plugin
import app.kash.core.PluginPackage
import app.kash.core.PluginPackageException
import app.kash.core.PluginSource
import app.kash.ui.Hint
import app.kash.ui.UiScaled
import app.kash.ui.Kash
import app.kash.ui.LocalUiScale
import app.kash.ui.LocalAwtWindow
import app.kash.ui.strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.slf4j.LoggerFactory
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI

private val logger = LoggerFactory.getLogger("app.kash.PluginsPage")

/** Where the installation of a plugin is: what is asked, read, confirmed, or what went wrong. */
private sealed interface PluginFlow {
    /** The address of a plugin is asked. */
    data object AskAddress : PluginFlow

    /** Its source is being read (a download can take a while). */
    data object Reading : PluginFlow

    /** It was read: its installation is confirmed, [installed] being the version that it replaces. */
    data class Confirm(val plugin: PluginPackage, val source: PluginSource, val installed: InstalledPlugin?) : PluginFlow

    /** It cannot be installed (or updated), for [message]. */
    data class Failed(val message: String) : PluginFlow

    /** Its uninstallation is confirmed. */
    data class Uninstall(val plugin: Plugin) : PluginFlow
}

/**
 * Settings › Plugins: the plugins of KaSH and those that the user installed (from a zip, a folder or an address), whose sources KaSH may watch
 * for a new version, which it offers here. What is done here is done at once: these are no settings, which Apply would apply.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PluginsPage(model: KashModel, icons: ServiceIcons) {
    var flow by remember { mutableStateOf<PluginFlow?>(null) }
    val scope = rememberCoroutineScope()
    val window = LocalAwtWindow.current
    val language = model.language

    // Reads [source], then asks to confirm its installation; a built-in plugin's id is refused before.
    fun read(source: PluginSource) {
        flow = PluginFlow.Reading
        scope.launch {
            flow = try {
                val plugin = model.readPlugin(source)
                val installed = model.installedPlugins.firstOrNull { it.id == plugin.id }
                if (installed == null && model.plugins[plugin.id] != null) PluginFlow.Failed("'${plugin.id}' is the id of a plugin of KaSH")
                else PluginFlow.Confirm(plugin, source, installed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PluginFlow.Failed(messageOf(e))
            }
        }
    }

    // Runs [action] (an installation, an update, an uninstallation): what goes wrong is said.
    fun run(action: suspend () -> Unit) {
        flow = PluginFlow.Reading
        scope.launch {
            flow = try {
                action()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PluginFlow.Failed(messageOf(e))
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint(strings.pluginsHint)
        // Read here: the texts are the composition's.
        val chooseZip = strings.chooseZip
        val chooseFolder = strings.chooseFolder
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { choose(window, chooseZip, folder = false)?.let { read(PluginSource.Zip(it)) } }) { Text(strings.installZip) }
            OutlinedButton(onClick = { choose(window, chooseFolder, folder = true)?.let { read(PluginSource.Folder(it)) } }) { Text(strings.installFolder) }
            OutlinedButton(onClick = { flow = PluginFlow.AskAddress }) { Text(strings.installAddress) }
        }
        val shape = RoundedCornerShape(8.dp)
        Column(Modifier.fillMaxWidth().background(Kash.colors.line, shape).border(1.dp, Kash.colors.line, shape), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            for (plugin in model.plugins.all) {
                key(plugin.id) {
                    val installed = model.installedPlugins.firstOrNull { it.id == plugin.id }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).background(Kash.colors.card).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Tile(tileContent(plugin.id, plugin.manifest.url, app.kash.core.ServiceIcon.Plugin, icons), 28.dp, Modifier.padding(top = 2.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(plugin.manifest.name.of(language), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Tooltip({ Text(strings.versionCode(plugin.manifest.versionCode)) }) {
                                    Text(plugin.manifest.version, fontSize = 12.sp, color = Kash.colors.muted, maxLines = 1)
                                }
                            }
                            Text(
                                installed?.let { sourceLabel(it.source) } ?: strings.builtIn,
                                fontFamily = if (installed != null && installed.source !is PluginSource.Zip) FontFamily.Monospace else FontFamily.Default,
                                fontSize = 11.sp, color = Kash.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            if (installed != null) {
                                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (installed.source.watchable) {
                                        Tooltip({ Text(if (installed.source is PluginSource.Folder) strings.watchFolderHint else strings.watchAddressHint) }) {
                                            CheckboxRow(strings.watch, installed.watch, { model.setPluginWatch(plugin.id, it) })
                                        }
                                    }
                                    // As the rail's arrow and the title bar's button do (startPluginUpdate): once at a time, its failure in a dialog.
                                    model.pluginUpdates[plugin.id]?.let { update ->
                                        val updating = plugin.id in model.updatingPlugins
                                        DefaultButton(onClick = { model.startPluginUpdate(plugin.id) }, enabled = !updating) {
                                            Text(if (updating) strings.updating else strings.updateTo(update.version))
                                        }
                                    }
                                    Box(Modifier.weight(1f))
                                    Link(strings.uninstall, onClick = { flow = PluginFlow.Uninstall(plugin) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (val current = flow) {
        null -> Unit
        PluginFlow.AskAddress -> AddressDialog(onRead = { read(PluginSource.Url(it)) }, onDismiss = { flow = null })
        PluginFlow.Reading -> PluginDialog(strings.readingPlugin, onDismiss = {}) { Text(strings.readingPlugin, color = Kash.colors.muted) }
        is PluginFlow.Failed -> PluginDialog(strings.pluginError, onDismiss = { flow = null }, actions = {
            DefaultButton(onClick = { flow = null }) { Text(strings.ok) }
        }) {
            Text(current.message, color = Kash.colors.muted)
        }
        is PluginFlow.Confirm -> {
            val manifest = current.plugin.manifest
            val name = manifest.name.of(language)
            val title = current.installed?.let { strings.replaceTitle(name, it.version, manifest.version) } ?: strings.installTitle(name, manifest.version)
            var watch by remember(current) { mutableStateOf(current.installed?.watch ?: true) }
            PluginDialog(title, onDismiss = { flow = null }, height = 340, actions = {
                OutlinedButton(onClick = { flow = null }) { Text(strings.cancel) }
                DefaultButton(onClick = { run { model.installPlugin(current.plugin, current.source, watch) } }) {
                    Text(if (current.installed != null) strings.replace else strings.install)
                }
            }) {
                Text("${manifest.id} · ${strings.versionCode(manifest.versionCode)}", fontSize = 12.sp, color = Kash.colors.muted)
                Text(strings.pluginPages)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    for (match in manifest.matches) Text(match, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                if (current.source.watchable) {
                    CheckboxRow(strings.watch, watch, { watch = it })
                    Hint(if (current.source is PluginSource.Folder) strings.watchFolderHint else strings.watchAddressHint)
                }
            }
        }
        is PluginFlow.Uninstall -> {
            val users = model.services.count { it.plugin == current.plugin.id }
            PluginDialog(strings.uninstallTitle(current.plugin.manifest.name.of(language)), onDismiss = { flow = null }, actions = {
                OutlinedButton(onClick = { flow = null }) { Text(strings.cancel) }
                DefaultButton(onClick = { run { model.uninstallPlugin(current.plugin.id) } }) { Text(strings.uninstall.removeSuffix("…")) }
            }) {
                Text(strings.uninstallBody(users), color = Kash.colors.muted)
            }
        }
    }
}

/** Where an installed plugin comes from, as its row says it. */
@Composable
private fun sourceLabel(source: PluginSource): String = when (source) {
    is PluginSource.Zip -> strings.sourceZip(File(source.path).name)
    is PluginSource.Folder -> strings.sourceFolder(source.path.replaceFirst(System.getProperty("user.home"), "~"))
    is PluginSource.Url -> strings.sourceAddress(runCatching { URI(source.url).let { "${it.host}${it.path}" } }.getOrDefault(source.url))
}

/** What the user is told when a plugin cannot be read or installed: the first field that is wrong, or why it could not be read. */
private fun messageOf(error: Exception): String {
    // Its kind only: its message (and its stack's) says the source's path or address, which may say who the user is. The user is told the message.
    if (error !is PluginPackageException) logger.warn("A plugin could not be installed: {}", error.javaClass.simpleName)
    return error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
}

/**
 * The path of a zip ([folder] false) or of a folder that the user chooses in macOS's own dialog, or null. A folder is chosen with the dialog of
 * files, told to take folders (`apple.awt.fileDialogForDirectories`).
 */
private fun choose(window: java.awt.Window?, title: String, folder: Boolean): String? {
    val dialog = FileDialog(window as? Frame, title, FileDialog.LOAD)
    if (!folder) dialog.setFilenameFilter { _, name -> name.endsWith(".zip", ignoreCase = true) }
    val property = "apple.awt.fileDialogForDirectories"
    if (folder) System.setProperty(property, "true")
    try {
        dialog.isVisible = true
    } finally {
        if (folder) System.clearProperty(property)
    }
    val name = dialog.file ?: return null
    return File(dialog.directory, name).absolutePath
}

/** The dialog that asks for a plugin's address. */
@Composable
private fun AddressDialog(onRead: (String) -> Unit, onDismiss: () -> Unit) {
    val address = rememberTextFieldState()
    val valid = address.text.trim().let { it.startsWith("https://") || it.startsWith("http://localhost") || it.startsWith("http://127.0.0.1") }
    PluginDialog(strings.installAddress.removeSuffix("…"), onDismiss = onDismiss, actions = {
        OutlinedButton(onClick = onDismiss) { Text(strings.cancel) }
        DefaultButton(onClick = { onRead(address.text.trim().toString()) }, enabled = valid) { Text(strings.readPlugin) }
    }) {
        Text(strings.pluginAddress)
        TextField(state = address, modifier = Modifier.fillMaxWidth(), placeholder = { Text("https://example.com/my-plugin.zip", color = Kash.colors.faint) })
        Hint(strings.pluginAddressHint)
    }
}

/**
 * A dialog of Settings › Plugins, in a window of its own as KaSH's other dialogs ([ConfirmDialog]): its [title], its [content], and its
 * [actions] at the bottom right.
 */
@Composable
private fun PluginDialog(
    title: String,
    onDismiss: () -> Unit,
    height: Int = 220,
    actions: @Composable () -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    // As big as the text is (LocalUiScale, outside the window's UiScaled): its content and its buttons stay in it.
    val scale = LocalUiScale.current
    DialogWindow(onCloseRequest = onDismiss, state = rememberDialogState(size = DpSize(480.dp * scale, height.dp * scale)), title = title, resizable = false) {
        // The size of the text that Settings › Appearance gives, inside the window (UiScaled).
        UiScaled {
            Box(Modifier.fillMaxSize().background(Kash.colors.panel).padding(20.dp)) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) { actions() }
                }
            }
        }
    }
}

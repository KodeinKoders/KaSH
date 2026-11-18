package app.kash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kash.browser.BrowserTab
import app.kash.core.PluginManifest
import app.kash.core.Service
import app.kash.ui.Chip
import app.kash.ui.IconOnlyButton
import app.kash.ui.Kash
import app.kash.ui.KashIcon
import app.kash.ui.KashIcons
import app.kash.ui.MenuAnchor
import app.kash.ui.MenuState
import app.kash.ui.WindowMenu
import app.kash.ui.menuAnchor
import app.kash.ui.strings
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.separator
import java.awt.Component
import java.awt.Dimension
import java.net.URI
import javax.swing.JPanel

/** What the "…" menu of a service does: the same actions as Settings › Services, and the DevTools. */
class ServiceActions(
    /** Installs the new version of the service's plugin (see [KashModel.pluginUpdates]). */
    val onUpdatePlugin: () -> Unit,
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onReload: () -> Unit,
    val onToggleMute: () -> Unit,
    val onDisable: () -> Unit,
    val onDevTools: () -> Unit,
    val onEdit: () -> Unit,
    val onResetCache: () -> Unit,
    val onErase: () -> Unit,
    val onDelete: () -> Unit,
)

/**
 * What the title bar shows of the selected service, on its left: its tile, its title, its address and its count. The service's header is in the
 * window's title bar, so that its page has the whole height of the window under it.
 */
@Composable
fun ServiceTitle(service: Service, tab: BrowserTab?, unread: Int, icons: ServiceIcons, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ServiceTile(service, icons, 20.dp)
        Text(service.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
        Text(
            displayAddress(tab?.url?.collectAsState()?.value ?: service.url),
            fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Kash.colors.muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp),
        )
        if (unread > 0) Chip(strings.unread(unread))
    }
}

/**
 * The buttons of the selected service, on the right of the title bar: Update when its plugin has a new version ([update], [updating] while it is
 * installed), back, forward, reload, mute, and its menu.
 */
@Composable
fun ServiceButtons(service: Service, tab: BrowserTab?, onToggleMute: () -> Unit, actions: ServiceActions, update: PluginManifest?, updating: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (update != null) {
            UpdateButton(strings.pluginUpdateTitle(update.name.of(strings.code), update.version), updating, actions.onUpdatePlugin)
            Box(Modifier.width(4.dp))
        }
        val canGoBack = tab?.canGoBack?.collectAsState()?.value ?: false
        val canGoForward = tab?.canGoForward?.collectAsState()?.value ?: false
        IconOnlyButton(KashIcons.ChevronLeft, strings.back, { tab?.back() }, size = 28.dp, enabled = canGoBack)
        IconOnlyButton(KashIcons.ChevronRight, strings.forward, { tab?.forward() }, size = 28.dp, enabled = canGoForward)
        IconOnlyButton(KashIcons.Refresh, strings.reload, { tab?.reload() }, size = 28.dp, enabled = tab != null)
        IconOnlyButton(
            if (service.muted) KashIcons.BellOff else KashIcons.Bell,
            if (service.muted) strings.unmuteService else strings.muteService,
            onToggleMute,
            size = 28.dp,
            tint = if (service.muted) Kash.colors.text else Kash.colors.muted,
        )
        ServiceMenuButton(service, actions, devToolsEnabled = tab != null, buttonSize = 28.dp)
    }
}

/** The title bar's button that installs the new version of the selected service's plugin: [description] says which; disabled while [updating]. */
@Composable
private fun UpdateButton(description: String, updating: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Tooltip({ Text(description) }) {
        Row(
            Modifier.height(24.dp)
                .background(Kash.colors.accent.copy(alpha = if (updating) 0.5f else 1f), shape)
                .clickable(enabled = !updating, onClick = onClick)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            KashIcon(KashIcons.ArrowUp, Color.White, size = 12.dp)
            Text(if (updating) strings.updating else strings.update, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/**
 * The page of the selected service, on the whole height under the title bar, once Chromium created it (see [BrowserTab.created]: a service just
 * added). One panel for every service ([PageHolder]): going from a service to another changes the page in it.
 */
@Composable
fun ServicePage(tab: BrowserTab?, modifier: Modifier) {
    val created = tab?.created?.collectAsState()?.value == true
    val holder = remember { PageHolder() }
    SwingPanel(
        background = Kash.colors.background,
        factory = { holder },
        modifier = modifier,
        update = { it.show(tab?.takeIf { created }?.component) },
    )
}

/**
 * The panel of the selected service's page: the page keeps running when it is removed, and is added again when its service is selected.
 *
 * A page is given its size before it is added, the one of the page before: added to a panel that has none yet (a new panel for each service, as
 * it was), a page was resized to nothing and back, and Chromium laid it out again and waited for a new frame of it, while it showed nothing.
 */
internal class PageHolder : JPanel(null) {
    private var page: Component? = null
    private var lastSize: Dimension? = null

    /** Shows [next] in place of the page before (null: none). */
    fun show(next: Component?) {
        if (next == page) return
        page?.let(::remove)
        page = next
        if (next != null) {
            pageSize()?.let { next.setBounds(0, 0, it.width, it.height) }
            add(next)
        }
        revalidate()
        repaint()
    }

    override fun doLayout() {
        val size = pageSize() ?: return
        page?.takeIf { it.width != size.width || it.height != size.height }?.setBounds(0, 0, size.width, size.height)
    }

    // The panel's size, or its last one while it has none (the Unread tab is shown).
    private fun pageSize(): Dimension? = if (width > 0 && height > 0) Dimension(width, height).also { lastSize = it } else lastSize
}

/** The "…" button of a service and its menu. */
@Composable
fun ServiceMenuButton(service: Service, actions: ServiceActions, devToolsEnabled: Boolean, buttonSize: androidx.compose.ui.unit.Dp = 32.dp) {
    val menu = remember { MenuState() }
    val anchor = remember { MenuAnchor() }
    Box(Modifier.menuAnchor(anchor)) {
        IconOnlyButton(KashIcons.More, strings.moreOptions, menu::toggle, size = buttonSize, active = menu.open)
    }
    if (menu.open) {
        // Wide enough for the longest item in French ("Effacer toutes les données…").
        WindowMenu(anchor, width = 260.dp, alignEnd = true, onDismiss = menu::dismiss) {
            serviceMenu(service, actions, devToolsEnabled, menu::dismiss)
        }
    }
}

/** The items of a service's menu, which [close] closes. */
fun MenuScope.serviceMenu(service: Service, actions: ServiceActions, devToolsEnabled: Boolean, close: () -> Unit) {
    selectableItem(selected = false, onClick = { close(); actions.onDevTools() }, enabled = devToolsEnabled) {
        MenuRow(KashIcons.Code, StringsKey.DevTools)
    }
    selectableItem(selected = false, onClick = { close(); actions.onEdit() }) { MenuRow(KashIcons.Pencil, StringsKey.Edit) }
    selectableItem(selected = false, onClick = { close(); actions.onResetCache() }) { MenuRow(KashIcons.Refresh, StringsKey.ResetCache) }
    selectableItem(selected = false, onClick = { close(); actions.onErase() }) { MenuRow(KashIcons.Eraser, StringsKey.Erase) }
    separator()
    selectableItem(selected = false, onClick = { close(); actions.onDelete() }) { MenuRow(KashIcons.Trash, StringsKey.Delete, destructive = true) }
}

/**
 * The items of a service's context menu (a right click on its tile in the rail), which [close] closes: updating its plugin when it has a new
 * version ([update]), what its header's buttons do on its page ([tab], null while it is not loaded), muting it, what its "…" menu does, and
 * disabling it.
 */
fun MenuScope.serviceContextMenu(service: Service, tab: BrowserTab?, actions: ServiceActions, update: PluginManifest?, close: () -> Unit) {
    if (update != null) {
        selectableItem(selected = false, onClick = { close(); actions.onUpdatePlugin() }) {
            MenuRow(KashIcons.ArrowUp, StringsKey.UpdatePlugin, argument = update.version)
        }
        separator()
    }
    // Read when the menu opens: what the page can do then.
    val canGoBack = tab?.canGoBack?.value == true
    val canGoForward = tab?.canGoForward?.value == true
    selectableItem(selected = false, onClick = { close(); actions.onBack() }, enabled = canGoBack) {
        MenuRow(KashIcons.ChevronLeft, StringsKey.Back, enabled = canGoBack)
    }
    selectableItem(selected = false, onClick = { close(); actions.onForward() }, enabled = canGoForward) {
        MenuRow(KashIcons.ChevronRight, StringsKey.Forward, enabled = canGoForward)
    }
    selectableItem(selected = false, onClick = { close(); actions.onReload() }, enabled = tab != null) {
        MenuRow(KashIcons.Refresh, StringsKey.Reload, enabled = tab != null)
    }
    separator()
    selectableItem(selected = false, onClick = { close(); actions.onToggleMute() }) {
        if (service.muted) MenuRow(KashIcons.Bell, StringsKey.Unmute) else MenuRow(KashIcons.BellOff, StringsKey.Mute)
    }
    separator()
    selectableItem(selected = false, onClick = { close(); actions.onDevTools() }, enabled = tab != null) {
        MenuRow(KashIcons.Code, StringsKey.DevTools, enabled = tab != null)
    }
    selectableItem(selected = false, onClick = { close(); actions.onEdit() }) { MenuRow(KashIcons.Pencil, StringsKey.Edit) }
    selectableItem(selected = false, onClick = { close(); actions.onResetCache() }) { MenuRow(KashIcons.Refresh, StringsKey.ResetCache) }
    selectableItem(selected = false, onClick = { close(); actions.onErase() }) { MenuRow(KashIcons.Eraser, StringsKey.Erase) }
    separator()
    selectableItem(selected = false, onClick = { close(); actions.onDisable() }) { MenuRow(KashIcons.Close, StringsKey.Disable) }
    selectableItem(selected = false, onClick = { close(); actions.onDelete() }) { MenuRow(KashIcons.Trash, StringsKey.Delete, destructive = true) }
}

/** Which text of [strings] a menu row shows: the items are built outside a composition, their text inside. */
enum class StringsKey { UpdatePlugin, Back, Forward, Reload, Mute, Unmute, DevTools, Edit, ResetCache, Erase, Disable, Delete, OpenConversation, MarkRead, MarkReadByOpening, MarkPriority, UnmarkPriority }

@Composable
internal fun MenuRow(icon: ImageVector, key: StringsKey, destructive: Boolean = false, argument: String = "", enabled: Boolean = true) {
    val text = when (key) {
        StringsKey.UpdatePlugin -> strings.updatePlugin(argument)
        StringsKey.Back -> strings.back
        StringsKey.Forward -> strings.forward
        StringsKey.Reload -> strings.reload
        StringsKey.Mute -> strings.muteNotifications
        StringsKey.Unmute -> strings.unmuteNotifications
        StringsKey.Disable -> strings.disableService
        StringsKey.DevTools -> strings.devTools
        StringsKey.Edit -> strings.edit
        StringsKey.ResetCache -> strings.resetCache
        StringsKey.Erase -> strings.eraseData
        StringsKey.Delete -> strings.delete
        StringsKey.OpenConversation -> strings.openConversation
        StringsKey.MarkRead -> strings.markRead
        StringsKey.MarkReadByOpening -> strings.markReadByOpening
        StringsKey.MarkPriority -> strings.markPriority
        StringsKey.UnmarkPriority -> strings.unmarkPriority
    }
    // Its own color, which Jewel's item does not gray out when it is disabled: KaSH does.
    val color: Color = when {
        !enabled -> Kash.colors.faint
        destructive -> Kash.colors.error
        else -> Kash.colors.text
    }
    Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KashIcon(icon, if (!enabled) Kash.colors.faint else if (destructive) Kash.colors.error else Kash.colors.muted)
        // On one line: a row is one line high, and a second line was cut off ("Effacer toutes les").
        Text(text, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** An address without its scheme and its trailing slash, as the header shows it: `web.whatsapp.com`. */
fun displayAddress(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    val host = uri.host ?: return url
    val path = uri.rawPath.orEmpty().trimEnd('/')
    return host + path
}

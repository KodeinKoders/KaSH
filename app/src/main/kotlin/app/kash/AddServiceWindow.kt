package app.kash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberWindowState
import app.kash.core.OptionError
import app.kash.core.PluginOption
import app.kash.core.PluginOptions
import app.kash.core.ProfileId
import app.kash.core.Service
import app.kash.core.ServiceIcon
import app.kash.core.ServiceId
import app.kash.core.SquareColors
import app.kash.core.WebApplications
import app.kash.ui.FieldLabel
import app.kash.ui.UiScaled
import app.kash.ui.Hint
import app.kash.ui.Kash
import app.kash.ui.SectionTitle
import app.kash.ui.QuietHoursEditor
import app.kash.ui.KashIcon
import app.kash.ui.KashIcons
import app.kash.ui.strings
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import java.awt.FileDialog
import java.awt.Frame
import java.net.URI
import java.nio.file.Paths

/** Which icon the draft has. */
private enum class IconMode { PLUGIN, FAVICON, IMAGE, SQUARE }

/**
 * The window that adds a service, or edits [editing]: the catalog (the plugins, a web application), the service's options (its plugin's, which
 * its manifest describes), and the icon of its tab, with a preview.
 */
@Composable
fun AddServiceWindow(editing: Service?, icons: ServiceIcons, onClose: () -> Unit, onDone: (Service) -> Unit) {
    val title = if (editing == null) strings.addService else strings.editService
    DecoratedWindow(onCloseRequest = onClose, state = rememberWindowState(size = DpSize(720.dp, 900.dp)), title = title, icon = remember { AppIcon.painter() }) {
        // The size of the text that Settings › Appearance gives, inside the window (UiScaled).
        UiScaled {
            TitleBar { Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.CenterHorizontally)) }
            AddServiceContent(editing, icons, onClose, onDone)
        }
    }
}

@Composable
private fun AddServiceContent(editing: Service?, icons: ServiceIcons, onClose: () -> Unit, onDone: (Service) -> Unit) {
    val plugins = icons.plugins
    val language = strings.code
    // The plugin of the service (null: a web application); a service that is edited keeps its own.
    var pluginId by remember { mutableStateOf(if (editing != null) editing.plugin else plugins.all.firstOrNull()?.id) }
    val plugin = plugins[pluginId]
    val name = rememberTextFieldState(if (editing != null && editing.plugin == null) editing.name else "")
    val url = rememberTextFieldState(if (editing != null && editing.plugin == null) editing.url else "")
    val label = rememberTextFieldState(editing?.label.orEmpty())
    var showBadge by remember { mutableStateOf(editing?.showBadge ?: true) }
    var includeInInbox by remember { mutableStateOf(editing?.includeInInbox ?: true) }
    var notifications by remember { mutableStateOf(editing?.desktopNotifications ?: true) }
    var quietHours by remember { mutableStateOf(editing?.quietHours.orEmpty()) }
    val icon = editing?.icon
    // A new service of a plugin that has an icon takes it.
    var iconMode by remember {
        mutableStateOf(
            when (icon) {
                is ServiceIcon.Image -> IconMode.IMAGE
                is ServiceIcon.Square -> IconMode.SQUARE
                ServiceIcon.Plugin -> IconMode.PLUGIN
                ServiceIcon.Favicon -> IconMode.FAVICON
                null -> if (plugin?.icon != null) IconMode.PLUGIN else IconMode.FAVICON
            },
        )
    }
    var imageFile by remember { mutableStateOf((icon as? ServiceIcon.Image)?.file) }
    var squareColor by remember { mutableStateOf((icon as? ServiceIcon.Square)?.color) }
    val hex = rememberTextFieldState((icon as? ServiceIcon.Square)?.color?.removePrefix("#").orEmpty())
    val squareText = rememberTextFieldState((icon as? ServiceIcon.Square)?.text.orEmpty())

    // The values of the plugin's options: the edited service's, else their defaults. A text is typed in a field, and normalized as it is saved.
    val draftOptions = remember(pluginId) {
        val manifest = plugin?.manifest
        val initial = if (manifest == null) emptyMap() else PluginOptions.values(manifest, editing?.takeIf { it.plugin == pluginId }?.options.orEmpty())
        OptionsDraft(
            texts = manifest?.options.orEmpty().filterIsInstance<PluginOption.Text>().associate { it.id to TextFieldState(initial[it.id].orEmpty()) },
            others = mutableStateMapOf<String, String>().apply { putAll(initial.filterKeys { id -> manifest?.options?.none { it.id == id && it is PluginOption.Text } ?: false }) },
        )
    }
    val optionValues = plugin?.manifest?.options.orEmpty().associate { option ->
        option.id to when (option) {
            is PluginOption.Text -> PluginOptions.normalize(option, draftOptions.texts[option.id]?.text?.toString().orEmpty())
            else -> draftOptions.others[option.id].orEmpty()
        }
    }

    val square = plugin?.manifest?.square
    val defaultColor = square?.color ?: WebApplications.COLOR
    val defaultText = square?.text ?: WebApplications.monoOf(name.text.toString())
    val hexText = hex.text.toString()
    val hexInvalid = hexText.isNotEmpty() && SquareColors.parse(hexText) == null
    val color = SquareColors.parse(hexText) ?: squareColor ?: defaultColor
    val text = squareText.text.toString().take(3).ifEmpty { defaultText }

    val customName = name.text.toString().trim()
    val customUrl = url.text.toString().trim()
    val urlValid = runCatching { URI(customUrl) }.getOrNull()?.let { (it.scheme == "https" || it.scheme == "http") && !it.host.isNullOrBlank() } ?: false
    // The address of the service: the one the user typed, or the plugin's with the values of its options (null while one is not accepted).
    val serviceUrl = if (plugin == null) customUrl.takeIf { urlValid } else PluginOptions.resolveUrl(plugin.manifest, optionValues)
    val valid = (if (plugin == null) customName.isNotEmpty() && urlValid else serviceUrl != null) &&
        (iconMode != IconMode.IMAGE || imageFile != null) && !(iconMode == IconMode.SQUARE && hexInvalid)

    val draftIcon = when (iconMode) {
        IconMode.PLUGIN -> ServiceIcon.Plugin
        IconMode.FAVICON -> ServiceIcon.Favicon
        IconMode.IMAGE -> imageFile?.let { ServiceIcon.Image(it) } ?: ServiceIcon.Favicon
        IconMode.SQUARE -> ServiceIcon.Square(color, text)
    }

    Column(Modifier.fillMaxSize().background(Kash.colors.panel)) {
        VerticallyScrollableContainer(Modifier.weight(1f), scrollState = rememberScrollState()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                // The catalog: the plugins, then a web application. A service that is edited keeps its plugin: only its own tile is shown,
                // which cannot be picked (the user's choice: the others, grayed out, looked like they could).
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.service, fontWeight = FontWeight.SemiBold)
                    val tiles: List<Pair<String?, String>> = if (editing != null) {
                        listOf(editing.plugin to (plugins[editing.plugin]?.manifest?.name?.of(language) ?: if (editing.plugin == null) strings.custom else editing.name))
                    } else {
                        plugins.all.map { it.id to it.manifest.name.of(language) } + (null to strings.custom)
                    }
                    for (row in tiles.chunked(3)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((tilePlugin, tileName) in row) {
                                CatalogTile(
                                    content = if (tilePlugin == null) null else tileContent(tilePlugin, "", ServiceIcon.Plugin, icons), name = tileName,
                                    selected = pluginId == tilePlugin, enabled = editing == null, modifier = Modifier.weight(1f),
                                ) {
                                    // The icon of the plugin picked, when it has one; the site's otherwise.
                                    val picked = plugins[tilePlugin]
                                    if (picked?.icon != null && iconMode == IconMode.FAVICON) iconMode = IconMode.PLUGIN
                                    if (picked?.icon == null && iconMode == IconMode.PLUGIN) iconMode = IconMode.FAVICON
                                    pluginId = tilePlugin; squareColor = null; hex.edit { replace(0, length, "") }; squareText.edit { replace(0, length, "") }
                                }
                            }
                            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }

                if (plugin == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(strings.customApplication, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LabeledField(strings.name, name, strings.namePlaceholder, Modifier.weight(1f))
                            LabeledField(strings.url, url, strings.urlPlaceholder, Modifier.weight(2f), monospace = true, error = strings.urlInvalid.takeIf { customUrl.isNotEmpty() && !urlValid })
                        }
                        LabeledField(strings.labelOptional, label, strings.labelPlaceholder, Modifier.widthIn(max = 320.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CheckboxRow(strings.showBadge, showBadge, { showBadge = it })
                            Hint(strings.customBadgeHint, Modifier.padding(start = 24.dp))
                        }
                    }
                } else {
                    val manifest = plugin.manifest
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(strings.configOf(manifest.name.of(language)), fontWeight = FontWeight.SemiBold)
                        manifest.description?.let { Hint(it.of(language)) }
                        // The plugin's options, as its manifest describes them.
                        for (option in manifest.options) {
                            OptionField(option, draftOptions, optionValues[option.id].orEmpty(), language)
                        }
                        Column(Modifier.widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LabeledField(strings.labelOptional, label, strings.labelPlaceholder, Modifier.fillMaxWidth())
                            Hint(strings.labelHint)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CheckboxRow(strings.showBadge, showBadge, { showBadge = it })
                            CheckboxRow(strings.includeInInbox, includeInInbox, { includeInInbox = it })
                            CheckboxRow(strings.desktopNotifications, notifications, { notifications = it })
                        }
                    }
                    // Its own hours of Do Not Disturb, when its notifications are on.
                    if (notifications) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionTitle(strings.quietHours)
                            Hint(strings.serviceQuietHoursHint)
                            QuietHoursEditor(quietHours) { quietHours = it }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.tabIcon, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (plugin?.icon != null) {
                                RadioButtonRow(strings.iconOf(plugin.manifest.name.of(language)), iconMode == IconMode.PLUGIN, { iconMode = IconMode.PLUGIN })
                            }
                            RadioButtonRow(strings.iconFavicon, iconMode == IconMode.FAVICON, { iconMode = IconMode.FAVICON })
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RadioButtonRow(strings.iconImage, iconMode == IconMode.IMAGE, { iconMode = IconMode.IMAGE })
                                OutlinedButton(onClick = { chooseImage(icons)?.let { imageFile = it } }, enabled = iconMode == IconMode.IMAGE) {
                                    Text(strings.chooseFile)
                                }
                            }
                            RadioButtonRow(strings.iconSquare, iconMode == IconMode.SQUARE, { iconMode = IconMode.SQUARE })
                            if (iconMode == IconMode.SQUARE) {
                                SquareEditor(color, hex, hexInvalid, squareText, defaultText) { picked ->
                                    squareColor = picked
                                    hex.edit { replace(0, length, picked.removePrefix("#")) }
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(strings.preview, fontSize = 12.sp, color = Kash.colors.muted)
                            Column(
                                Modifier.width(72.dp).background(Kash.colors.panel, RoundedCornerShape(8.dp))
                                    .border(1.dp, Kash.colors.divider, RoundedCornerShape(8.dp)).padding(top = 10.dp, bottom = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Tile(tileContent(pluginId, serviceUrl.orEmpty(), draftIcon, icons, editing?.takeIf { it.plugin == pluginId && it.url == serviceUrl }?.id), 40.dp)
                                val previewLabel = label.text.toString().trim()
                                if (previewLabel.isNotEmpty()) {
                                    Text(previewLabel, fontSize = 10.5.sp, color = Kash.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kash.colors.line))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            OutlinedButton(onClick = onClose) { Text(strings.cancel) }
            DefaultButton(
                enabled = valid,
                onClick = {
                    // A plugin's service takes its name and its address from its plugin (KashModel refreshes them).
                    val service = (editing ?: Service(ServiceId.random(), name = "", url = "", profile = ProfileId.random())).copy(
                        plugin = pluginId,
                        options = optionValues,
                        name = plugin?.manifest?.name?.of(language) ?: customName,
                        url = serviceUrl!!,
                        label = label.text.toString().trim(),
                        showBadge = showBadge,
                        includeInInbox = includeInInbox,
                        desktopNotifications = notifications,
                        quietHours = quietHours,
                        icon = draftIcon,
                    )
                    onDone(service)
                },
            ) { Text(if (editing == null) strings.add else strings.save) }
        }
    }
}

/** What the fields of a plugin's options hold: the texts as they are typed, the choices and the booleans as their values. */
private class OptionsDraft(val texts: Map<String, TextFieldState>, val others: MutableMap<String, String>)

/** The field of [option], as its manifest describes it; [value] is its value, normalized. */
@Composable
private fun OptionField(option: PluginOption, draft: OptionsDraft, value: String, language: String) {
    when (option) {
        is PluginOption.Text -> {
            val state = draft.texts[option.id] ?: return
            // An error once something is typed: an empty required field only keeps the button disabled.
            val error = PluginOptions.error(option, value).takeIf { state.text.isNotBlank() }?.let {
                when (it) {
                    OptionError.MISSING -> strings.optionRequired
                    OptionError.TOO_LONG -> strings.optionTooLong
                    OptionError.PATTERN -> option.patternError?.of(language) ?: strings.optionInvalid
                }
            }
            Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabeledField(
                    option.label.of(language), state, option.placeholder?.of(language).orEmpty(), Modifier.fillMaxWidth(),
                    // A field with what an address has around it is a part of an address.
                    monospace = option.prefix != null || option.suffix != null,
                    error = error, prefix = option.prefix, suffix = option.suffix,
                )
                option.hint?.let { Hint(it.of(language)) }
            }
        }
        is PluginOption.Choice -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(option.label.of(language))
            // A few choices side by side, more in a column.
            if (option.choices.size <= 4) {
                SegmentedControl(option.choices.map { choice ->
                    SegmentedControlButtonData(choice.value == value, { Text(choice.label.of(language)) }, { draft.others[option.id] = choice.value })
                })
            } else {
                for (choice in option.choices) {
                    RadioButtonRow(choice.label.of(language), choice.value == value, { draft.others[option.id] = choice.value })
                }
            }
            option.hint?.let { Hint(it.of(language)) }
        }
        is PluginOption.Flag -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CheckboxRow(option.label.of(language), value == "true", { draft.others[option.id] = it.toString() })
            option.hint?.let { Hint(it.of(language), Modifier.padding(start = 24.dp)) }
        }
    }
}

@Composable
private fun CatalogTile(content: TileContent?, name: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier.heightIn(min = 56.dp)
            .background(if (selected) Kash.colors.selection else Kash.colors.panel, shape)
            .border(1.dp, if (selected) Kash.colors.accent else Kash.colors.divider, shape)
            .then(if (enabled) Modifier.clickable(onClick = onPick) else Modifier)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (content != null) {
            Tile(content, 32.dp)
        } else {
            Box(Modifier.size(32.dp).background(Kash.colors.neutral, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                KashIcon(KashIcons.Plus, Kash.colors.text)
            }
        }
        Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (enabled || selected) Kash.colors.text else Kash.colors.faint)
    }
}

@Composable
private fun LabeledField(
    label: String,
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier,
    monospace: Boolean = false,
    error: String? = null,
    prefix: String? = null,
    suffix: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        TextField(
            state = state,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            placeholder = { Text(placeholder, color = Kash.colors.faint, fontFamily = if (monospace) FontFamily.Monospace else null) },
            textStyle = if (monospace) org.jetbrains.jewel.foundation.theme.JewelTheme.defaultTextStyle.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            else org.jetbrains.jewel.foundation.theme.JewelTheme.defaultTextStyle,
            // What the address has around what is typed (`https://` and `.slack.com` around a workspace), as the design shows it.
            leadingIcon = prefix?.let { { Affix(it, Modifier.padding(end = 2.dp)) } },
            trailingIcon = suffix?.let { { Affix(it, Modifier.padding(start = 2.dp)) } },
        )
        if (error != null) Text(error, fontSize = 12.sp, color = Kash.colors.error)
    }
}

/** The color square's options: the design's 30 swatches, the hex of the color, the text. */
@Composable
private fun SquareEditor(color: String, hex: TextFieldState, hexInvalid: Boolean, text: TextFieldState, defaultText: String, onPick: (String) -> Unit) {
    Column(Modifier.padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(strings.color)
            for (row in SquareColors.SWATCHES.chunked(10)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (swatch in row) {
                        val on = swatch == color
                        Box(
                            Modifier.size(24.dp)
                                .then(if (on) Modifier.border(2.dp, Kash.colors.accent, RoundedCornerShape(8.dp)).padding(3.dp) else Modifier)
                                .background(Color(0xFF000000 or SquareColors.rgb(swatch).toLong()), RoundedCornerShape(if (on) 4.dp else 6.dp))
                                .clickable { onPick(swatch) }
                                .semantics { contentDescription = swatch; selected = on },
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(strings.hexColor)
                TextField(
                    state = hex,
                    modifier = Modifier.width(132.dp),
                    placeholder = { Text("3058CF", color = Kash.colors.faint, fontFamily = FontFamily.Monospace) },
                    leadingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(14.dp).background(Color(0xFF000000 or SquareColors.rgb(color).toLong()), RoundedCornerShape(3.dp)))
                            Text("#", color = Kash.colors.muted, fontFamily = FontFamily.Monospace)
                        }
                    },
                    textStyle = org.jetbrains.jewel.foundation.theme.JewelTheme.defaultTextStyle.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    inputTransformation = { if (length > 6) revertAllChanges() },
                    outline = if (hexInvalid) org.jetbrains.jewel.ui.Outline.Error else org.jetbrains.jewel.ui.Outline.None,
                )
                if (hexInvalid) Text(strings.hexError, fontSize = 12.sp, color = Kash.colors.error)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(strings.squareText)
                TextField(
                    state = text,
                    modifier = Modifier.width(72.dp),
                    placeholder = { Text(defaultText, color = Kash.colors.faint) },
                    inputTransformation = { if (length > 3) revertAllChanges() },
                )
            }
        }
    }
}

/** Asks for an image with the system's file dialog, and copies it in KaSH's icons. Null when none was chosen or it is not an image. */
private fun chooseImage(icons: ServiceIcons): String? {
    val dialog = FileDialog(null as Frame?, "", FileDialog.LOAD)
    dialog.setFilenameFilter { _, file -> file.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp", "ico", "bmp") }
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return icons.import(Paths.get(dialog.directory, file))
}

@Composable
private fun Affix(text: String, modifier: Modifier) {
    Text(text, modifier, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Kash.colors.muted)
}

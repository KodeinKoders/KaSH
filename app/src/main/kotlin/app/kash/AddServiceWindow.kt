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
import app.kash.core.ProfileId
import app.kash.core.Service
import app.kash.core.ServiceCatalog
import app.kash.core.ServiceIcon
import app.kash.core.ServiceId
import app.kash.core.ServiceKind
import app.kash.core.SquareColors
import app.kash.ui.FieldLabel
import app.kash.ui.Hint
import app.kash.ui.Kash
import app.kash.ui.KashIcon
import app.kash.ui.KashIcons
import app.kash.ui.strings
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
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
private enum class IconMode { FAVICON, IMAGE, SQUARE }

/**
 * The window that adds a service, or edits [editing]: the catalog (WhatsApp, a custom application), the service's options, and the icon of its
 * tab, with a preview.
 */
@Composable
fun AddServiceWindow(editing: Service?, icons: ServiceIcons, onClose: () -> Unit, onDone: (Service) -> Unit) {
    val title = if (editing == null) strings.addService else strings.editService
    DecoratedWindow(onCloseRequest = onClose, state = rememberWindowState(size = DpSize(720.dp, 900.dp)), title = title, icon = remember { AppIcon.painter() }) {
        TitleBar { Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.CenterHorizontally)) }
        AddServiceContent(editing, icons, onClose, onDone)
    }
}

@Composable
private fun AddServiceContent(editing: Service?, icons: ServiceIcons, onClose: () -> Unit, onDone: (Service) -> Unit) {
    var kind by remember { mutableStateOf(editing?.kind ?: ServiceKind.WHATSAPP) }
    val name = rememberTextFieldState(if (editing?.kind == ServiceKind.CUSTOM) editing.name else "")
    val url = rememberTextFieldState(if (editing?.kind == ServiceKind.CUSTOM) editing.url else "")
    val label = rememberTextFieldState(editing?.label.orEmpty())
    var showBadge by remember { mutableStateOf(editing?.showBadge ?: true) }
    var includeInInbox by remember { mutableStateOf(editing?.includeInInbox ?: true) }
    var notifications by remember { mutableStateOf(editing?.desktopNotifications ?: true) }
    val icon = editing?.icon
    var iconMode by remember {
        mutableStateOf(when (icon) { is ServiceIcon.Image -> IconMode.IMAGE; is ServiceIcon.Square -> IconMode.SQUARE; else -> IconMode.FAVICON })
    }
    var imageFile by remember { mutableStateOf((icon as? ServiceIcon.Image)?.file) }
    var squareColor by remember { mutableStateOf((icon as? ServiceIcon.Square)?.color) }
    val hex = rememberTextFieldState((icon as? ServiceIcon.Square)?.color?.removePrefix("#").orEmpty())
    val squareText = rememberTextFieldState((icon as? ServiceIcon.Square)?.text.orEmpty())

    val preset = ServiceCatalog.preset(kind)
    val defaultColor = preset?.color ?: ServiceCatalog.CUSTOM_COLOR
    val defaultText = preset?.mono ?: ServiceCatalog.monoOf(name.text.toString())
    val hexText = hex.text.toString()
    val hexInvalid = hexText.isNotEmpty() && SquareColors.parse(hexText) == null
    val color = SquareColors.parse(hexText) ?: squareColor ?: defaultColor
    val text = squareText.text.toString().take(3).ifEmpty { defaultText }

    val customName = name.text.toString().trim()
    val customUrl = url.text.toString().trim()
    val urlValid = runCatching { URI(customUrl) }.getOrNull()?.let { (it.scheme == "https" || it.scheme == "http") && !it.host.isNullOrBlank() } ?: false
    val valid = (kind != ServiceKind.CUSTOM || (customName.isNotEmpty() && urlValid)) &&
        (iconMode != IconMode.IMAGE || imageFile != null) && !(iconMode == IconMode.SQUARE && hexInvalid)

    val draftIcon = when (iconMode) {
        IconMode.FAVICON -> ServiceIcon.Favicon
        IconMode.IMAGE -> imageFile?.let { ServiceIcon.Image(it) } ?: ServiceIcon.Favicon
        IconMode.SQUARE -> ServiceIcon.Square(color, text)
    }

    Column(Modifier.fillMaxSize().background(Kash.colors.panel)) {
        VerticallyScrollableContainer(rememberScrollState(), Modifier.weight(1f)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                // The catalog: a service that is edited keeps its kind.
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.service, fontWeight = FontWeight.SemiBold)
                    val tiles = ServiceCatalog.presets.map { it.kind to it.name } + (ServiceKind.CUSTOM to strings.custom)
                    for (row in tiles.chunked(3)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((tileKind, tileName) in row) {
                                CatalogTile(
                                    kind = tileKind, name = tileName, selected = kind == tileKind, enabled = editing == null || editing.kind == tileKind,
                                    modifier = Modifier.weight(1f),
                                ) { kind = tileKind; squareColor = null; hex.edit { replace(0, length, "") }; squareText.edit { replace(0, length, "") } }
                            }
                            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }

                if (kind == ServiceKind.CUSTOM) {
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
                } else if (preset != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(strings.configOf(preset.name), fontWeight = FontWeight.SemiBold)
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
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.tabIcon, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                val previewUrl = if (kind == ServiceKind.CUSTOM) customUrl.takeIf { urlValid }.orEmpty() else preset?.url.orEmpty()
                                Tile(tileContent(kind, customName, previewUrl, draftIcon, icons), 40.dp)
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
                    val service = (editing ?: Service(ServiceId.random(), kind, "", "", profile = ProfileId.random())).copy(
                        name = if (kind == ServiceKind.CUSTOM) customName else preset!!.name,
                        url = if (kind == ServiceKind.CUSTOM) customUrl else preset!!.url,
                        label = label.text.toString().trim(),
                        showBadge = showBadge,
                        includeInInbox = includeInInbox,
                        desktopNotifications = notifications,
                        icon = draftIcon,
                    )
                    onDone(service)
                },
            ) { Text(if (editing == null) strings.add else strings.save) }
        }
    }
}

@Composable
private fun CatalogTile(kind: ServiceKind, name: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier.height(56.dp)
            .background(if (selected) Kash.colors.selection else Kash.colors.panel, shape)
            .border(1.dp, if (selected) Kash.colors.accent else Kash.colors.divider, shape)
            .then(if (enabled) Modifier.clickable(onClick = onPick) else Modifier)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val preset = ServiceCatalog.preset(kind)
        if (preset != null) {
            Tile(TileContent.Square(preset.color, preset.mono), 32.dp)
        } else {
            Box(Modifier.size(32.dp).background(Kash.colors.neutral, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                KashIcon(KashIcons.Plus, Kash.colors.text)
            }
        }
        Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (enabled) Kash.colors.text else Kash.colors.faint)
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
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        TextField(
            state = state,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            placeholder = { Text(placeholder, color = Kash.colors.faint, fontFamily = if (monospace) FontFamily.Monospace else null) },
            textStyle = if (monospace) org.jetbrains.jewel.foundation.theme.JewelTheme.defaultTextStyle.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            else org.jetbrains.jewel.foundation.theme.JewelTheme.defaultTextStyle,
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

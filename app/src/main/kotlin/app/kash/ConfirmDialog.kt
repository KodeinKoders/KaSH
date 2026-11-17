package app.kash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import app.kash.ui.Kash
import app.kash.ui.strings
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

/**
 * The confirmation of what cannot be undone (deleting a service, erasing its data), in a dialog of its own: a page of a service is a native view,
 * which would be drawn over anything shown on it.
 */
@Composable
fun ConfirmDialog(confirmation: Confirmation, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val title = when (confirmation) {
        is Confirmation.Delete -> strings.deleteTitle(confirmation.service.title)
        is Confirmation.Erase -> strings.eraseTitle(confirmation.service.title)
    }
    val body = when (confirmation) {
        is Confirmation.Delete -> strings.deleteBody
        is Confirmation.Erase -> strings.eraseBody
    }
    val action = when (confirmation) {
        is Confirmation.Delete -> strings.delete.removeSuffix("…")
        is Confirmation.Erase -> strings.erase
    }
    MessageDialog(title, body, onDismiss) {
        OutlinedButton(onClick = onDismiss) { Text(strings.cancel) }
        DefaultButton(onClick = onConfirm) { Text(action) }
    }
}

/** A failed update of a plugin, which the rail or the title bar started ([KashModel.startPluginUpdate]): its name and why. */
@Composable
fun PluginUpdateFailureDialog(failure: PluginUpdateFailure, onDismiss: () -> Unit) {
    MessageDialog(strings.pluginUpdateFailed(failure.name), failure.message, onDismiss) {
        DefaultButton(onClick = onDismiss) { Text(strings.ok) }
    }
}

// A dialog of its own (not drawn over the page): its title, its text, and its buttons at the bottom right.
@Composable
private fun MessageDialog(title: String, body: String, onDismiss: () -> Unit, buttons: @Composable () -> Unit) {
    DialogWindow(onCloseRequest = onDismiss, state = rememberDialogState(size = DpSize(440.dp, 200.dp)), title = title, resizable = false) {
        Box(Modifier.fillMaxSize().background(Kash.colors.panel).padding(20.dp)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(body, color = Kash.colors.muted)
                Box(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) { buttons() }
            }
        }
    }
}

package app.kash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kash.core.QuietRange
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

/** A time of a range, "08:00", from minutes since midnight. */
internal fun timeText(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

/** The minutes since midnight of a time that the user wrote ("8:00", "08:00", "8h00", "8h", "8"); null when it is not one. */
internal fun parseTime(text: String): Int? {
    val match = Regex("""^\s*(\d{1,2})\s*(?:[:hH.]\s*(\d{2})?)?\s*$""").matchEntire(text) ?: return null
    val hours = match.groupValues[1].toInt()
    val minutes = match.groupValues[2].ifEmpty { "0" }.toInt()
    return if (hours in 0..23 && minutes in 0..59) hours * 60 + minutes else null
}

/** The range that "Add a range" makes: the evenings of the week, from 19:00 to 08:00 the next day. */
internal val NEW_QUIET_RANGE = QuietRange(setOf(1, 2, 3, 4, 5), 19 * 60, 8 * 60)

/**
 * The ranges of Do Not Disturb ([QuietRange]), which [onChange] is given as they are edited: a line per range (its days, toggled, its start and
 * its end, written, a time that is not one framed as an error and not kept), and a button that adds one.
 */
@Composable
fun QuietHoursEditor(ranges: List<QuietRange>, onChange: (List<QuietRange>) -> Unit) {
    val latest by rememberUpdatedState(ranges)
    val change by rememberUpdatedState(onChange)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ranges.forEachIndexed { index, range ->
            // A line per place: its fields keep what is written while the ranges change.
            key(index) {
                QuietRangeRow(range, onChange = { updated -> change(latest.toMutableList().also { it[index] = updated }) }, onRemove = {
                    change(latest.filterIndexed { i, _ -> i != index })
                })
            }
        }
        OutlinedButton(onClick = { change(latest + NEW_QUIET_RANGE) }) { Text(strings.addQuietRange) }
        Hint(strings.quietRangeHelp)
    }
}

@Composable
private fun QuietRangeRow(range: QuietRange, onChange: (QuietRange) -> Unit, onRemove: () -> Unit) {
    val current by rememberUpdatedState(range)
    val update by rememberUpdatedState(onChange)
    val s = strings
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (day in 1..7) {
                DayToggle(s.dayLetters[day - 1], s.dayNames[day - 1], day in range.days) {
                    update(current.copy(days = if (day in current.days) current.days - day else current.days + day))
                }
            }
        }
        Text(s.quietFrom, color = Kash.colors.muted)
        TimeField(range.start, s.quietFrom) { update(current.copy(start = it)) }
        Text(s.quietTo, color = Kash.colors.muted)
        TimeField(range.end, s.quietTo) { update(current.copy(end = it)) }
        IconOnlyButton(KashIcons.Close, s.removeQuietRange, onRemove, size = 26.dp, iconSize = 14.dp)
    }
}

@Composable
private fun DayToggle(letter: String, name: String, on: Boolean, onToggle: () -> Unit) {
    val colors = Kash.colors
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.size(26.dp)
            .background(if (on) colors.accent else Color.Transparent, shape)
            .border(1.dp, if (on) colors.accent else colors.control, shape)
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = name
                role = Role.Checkbox
                selected = on
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = if (on) Color.White else colors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

// A time, written as the user wants: one that is a time is given to [onTime], one that is not is framed as an error and not kept.
@Composable
private fun TimeField(minutes: Int, label: String, onTime: (Int) -> Unit) {
    val state = rememberTextFieldState(timeText(minutes))
    var invalid by remember { mutableStateOf(false) }
    val give by rememberUpdatedState(onTime)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text ->
            val parsed = parseTime(text)
            invalid = parsed == null
            if (parsed != null) give(parsed)
        }
    }
    // A time that changes from outside (the range of this line removed, the next one in its place; Settings' draft following a change): the
    // field shows it. Not while it is written: a text that is this time already, or not a time yet, is the user's.
    LaunchedEffect(minutes) {
        val written = parseTime(state.text.toString())
        if (written != null && written != minutes) state.setTextAndPlaceCursorAtEnd(timeText(minutes))
    }
    TextField(
        state = state,
        modifier = Modifier.width(64.dp).semantics { contentDescription = label },
        textStyle = JewelTheme.defaultTextStyle.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
        inputTransformation = { if (length > 5) revertAllChanges() },
        outline = if (invalid) Outline.Error else Outline.None,
    )
}

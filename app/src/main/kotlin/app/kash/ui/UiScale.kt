package app.kash.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** How big the text of KaSH's windows is, as a multiple of its usual size (Settings › Appearance › Text size): provided at the application's root. */
val LocalUiScale = staticCompositionLocalOf { 1f }

/** The density before [UiScaled] made the text bigger or smaller, null where it did not. */
val LocalUnscaledDensity = staticCompositionLocalOf<Density?> { null }

/**
 * Makes the text of [content] [LocalUiScale] times as big as it is: the `sp` of every text, and nothing that is in `dp`, so that the layout stays
 * what it is and only what is written grows (KaTTY's `UiScale`). It has to be **inside the content of each window**: a window gives its own
 * density to what it shows, which hides one that is given outside it. It is always there, even for 1, so that changing the size does not make
 * what is under it start again.
 */
@Composable
fun UiScaled(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalUnscaledDensity provides (LocalUnscaledDensity.current ?: density),
        LocalDensity provides Density(density.density, density.fontScale * LocalUiScale.current),
        content = content,
    )
}

/**
 * How much bigger than usual the text is where this is called: 1 when it is not. What holds text and has a size of its own (a notification, a
 * field) takes it, so that a bigger text does not overflow a box that was made for the usual one.
 */
@Composable
fun uiScaleFactor(): Float {
    val unscaled = LocalUnscaledDensity.current ?: return 1f
    return LocalDensity.current.fontScale / unscaled.fontScale
}

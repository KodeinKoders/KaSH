package app.kash.core

import kotlin.math.pow

/** The colors of the squares that stand for services. */
public object SquareColors {
    /** The design's swatches: 3 rows of 10 hues, light, medium and dark. */
    public val SWATCHES: List<String> = listOf(
        "#EB8E8E", "#EBB58E", "#EBD48E", "#BDEB8E", "#8EEBBD", "#8EEBE3", "#8ECCEB", "#8EA5EB", "#B58EEB", "#EB8ECC",
        "#CF3030", "#AB5F28", "#8A7020", "#4F801E", "#1F8250", "#1E8078", "#277BA5", "#3058CF", "#7230CF", "#CC3098",
        "#631717", "#633717", "#635017", "#3D6317", "#17633D", "#17635D", "#174A63", "#172A63", "#371763", "#63174A",
    )

    private val HEX = Regex("^#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")

    /** [input] as `#RRGGBB` (3 or 6 hexadecimal digits, with or without `#`), or null when it is not a color. */
    public fun parse(input: String): String? {
        val digits = HEX.find(input.trim())?.groupValues?.get(1) ?: return null
        val six = if (digits.length == 3) digits.map { "$it$it" }.joinToString("") else digits
        return "#${six.uppercase()}"
    }

    /** The RGB of a `#RRGGBB` color as an Int. */
    public fun rgb(color: String): Int = color.removePrefix("#").toInt(16)

    /**
     * Whether the text on [color] is white rather than dark: the one with the most contrast (WCAG relative luminance), as the design's `textOn`
     * chooses it, so that a color typed by hand stays readable.
     */
    public fun whiteTextOn(color: String): Boolean {
        val n = rgb(color)
        val channels = listOf((n shr 16) and 255, (n shr 8) and 255, n and 255).map {
            val c = it / 255.0
            if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val luminance = 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
        return 1.05 / (luminance + 0.05) >= (luminance + 0.05) / 0.05
    }
}

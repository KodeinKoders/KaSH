package app.kash.core

/**
 * Whether a plugin's script can be the body of the function that KaSH runs it in (`PluginScripts`: `function (kash) {\n<script>\n}`), after the
 * API's checks (the top frame, `matches`): a script that closes that function (`}); anything(); (function () {`) ran its code outside of it, in
 * every document and frame of the tab, the sign-in pages included.
 *
 * A careful lexical reading, without a JavaScript engine: it follows the strings, the comments, the template literals (and their `${…}`, nested)
 * and the regular expressions, and counts the brackets. Where `/` could be a division or a regular expression and the previous token does not tell
 * (after `)`, `]`, `}`, a name, a number, `++`, `--`), it reads a division: what follows is code, whose brackets count. It can then refuse a valid
 * script (seen at its installation), never let through a bracket that JavaScript would take for one that closes the function.
 */
public object PluginScriptCheck {
    // The words after which a `/` starts a regular expression (not a division), when they are not a property's name (after `.`).
    private val REGEX_AFTER_WORDS = setOf(
        "return", "typeof", "instanceof", "in", "of", "new", "delete", "void", "throw", "case", "do", "else", "yield", "await",
    )

    private val CLOSING = mapOf('}' to '{', ')' to '(', ']' to '[')

    // The mark of a template literal's `${` on the stack of brackets: its `}` goes back to the literal.
    private const val TEMPLATE = '`'

    /** Why [source] cannot be a plugin's script, or null when it can. */
    public fun check(source: String): String? = Reader(source).read()

    private class Reader(val s: String) {
        val stack = ArrayDeque<Char>()
        var i = 0
        var regexAllowed = true
        var lastSignificant = ' '

        fun read(): String? {
            while (i < s.length) {
                val c = s[i]
                val next = s.getOrNull(i + 1)
                when {
                    c.isWhitespace() -> i++
                    c == '/' && next == '/' -> skipLine()
                    c == '/' && next == '*' -> {
                        val end = s.indexOf("*/", i + 2)
                        if (end < 0) return "a comment is not closed"
                        i = end + 2
                    }
                    c == '/' && regexAllowed -> readRegex() ?: return "a regular expression is not closed"
                    c == '\'' || c == '"' -> readString(c) ?: return "a string is not closed"
                    c == '`' -> {
                        i++
                        readTemplate() ?: return "a template literal is not closed"
                    }
                    c == '{' || c == '(' || c == '[' -> {
                        stack.addLast(c)
                        regexAllowed = true
                        significant(c)
                    }
                    c == '}' || c == ')' || c == ']' -> {
                        val open = stack.removeLastOrNull() ?: return "the script closes the function that runs it"
                        if (c == '}' && open == TEMPLATE) {
                            // Back in the template literal of this `${`.
                            i++
                            readTemplate() ?: return "a template literal is not closed"
                            continue
                        }
                        if (open != CLOSING.getValue(c)) return "a bracket closes another one ('$open' by '$c')"
                        // A division after it, whatever it closes: the careful reading.
                        regexAllowed = false
                        significant(c)
                    }
                    (c == '+' || c == '-') && next == c -> {
                        // `++` or `--`, maybe postfix: a division after it.
                        i += 2
                        regexAllowed = false
                        lastSignificant = c
                    }
                    c.isLetterOrDigit() || c == '_' || c == '$' || c == '\\' -> readWord()
                    else -> {
                        regexAllowed = true
                        significant(c)
                    }
                }
            }
            if (stack.isNotEmpty()) return "a bracket is not closed ('${stack.last()}')"
            return null
        }

        private fun significant(c: Char) {
            lastSignificant = c
            i++
        }

        private fun isLineTerminator(c: Char) = c == '\n' || c == '\r' || c == ' ' || c == ' '

        private fun skipLine() {
            while (i < s.length && !isLineTerminator(s[i])) i++
        }

        private fun readWord() {
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == '$' || s[i] == '\\' || s[i] == '.' && s[start].isDigit())) i++
            val word = s.substring(start, i)
            regexAllowed = lastSignificant != '.' && word in REGEX_AFTER_WORDS
            lastSignificant = 'a'
        }

        private fun readString(quote: Char): Unit? {
            i++
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '\\' -> i += 2
                    c == quote -> {
                        i++
                        regexAllowed = false
                        lastSignificant = 'a'
                        return Unit
                    }
                    isLineTerminator(c) -> return null
                    else -> i++
                }
            }
            return null
        }

        // From after the opening `/`: to after its flags; null when it is not closed on its line.
        private fun readRegex(): Unit? {
            i++
            var inClass = false
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '\\' -> i += 2
                    isLineTerminator(c) -> return null
                    c == '[' -> { inClass = true; i++ }
                    c == ']' -> { inClass = false; i++ }
                    c == '/' && !inClass -> {
                        i++
                        while (i < s.length && s[i].isLetter()) i++
                        regexAllowed = false
                        lastSignificant = 'a'
                        return Unit
                    }
                    else -> i++
                }
            }
            return null
        }

        // In a template literal, after its opening `` ` `` or the `}` of one of its `${…}`: to after its closing `` ` ``, or after a `${`, whose
        // code is read as code until its `}`; null when it is not closed.
        private fun readTemplate(): Unit? {
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '\\' -> i += 2
                    c == '`' -> {
                        i++
                        regexAllowed = false
                        lastSignificant = 'a'
                        return Unit
                    }
                    c == '$' && s.getOrNull(i + 1) == '{' -> {
                        stack.addLast(TEMPLATE)
                        i += 2
                        regexAllowed = true
                        lastSignificant = '{'
                        return Unit
                    }
                    else -> i++
                }
            }
            return null
        }
    }
}

package app.kash.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PluginScriptCheckTest {
    @Test
    fun aScriptThatClosesItsFunctionIsRefused() {
        for (escape in listOf(
            "}); evil(); (function () {",
            "kash.watch(() => []);\n}) ; evil(); //",
            // A division that a careless reading would take for a regular expression, hiding what follows.
            "let a = 1; a++ / 1 }); evil(); (function () { /1",
            "const x = {}; x.return / 1 }); evil(); (function () { /1",
            "const y = (1) / 2 }); evil(); (function () { /2",
        )) {
            assertEquals("the script closes the function that runs it", PluginScriptCheck.check(escape), escape)
        }
    }

    @Test
    fun bracketsInStringsCommentsRegularExpressionsAndTemplatesAreNotCode() {
        for (fine in listOf(
            "const s = '}); evil(); (function () {'; const d = \"})\";",
            "// }); evil();\n/* }) */ kash.watch(() => []);",
            "const r = /[{}]/; const q = x.match(/\\}\\)/); if (/}/.test(s)) {}",
            "const t = `\${ {a: 1}.a } and \${ `\${ ')' }` } }`;",
            "return /re}/.test(x);",
            "const half = width / 2; const f = (a) => a / 2;",
        )) {
            assertNull(PluginScriptCheck.check(fine), fine)
        }
    }

    @Test
    fun whatIsNotClosedIsRefused() {
        assertNotNull(PluginScriptCheck.check("/* not closed"))
        assertNotNull(PluginScriptCheck.check("const t = `not closed"))
        assertNotNull(PluginScriptCheck.check("const s = 'not closed"))
        assertNotNull(PluginScriptCheck.check("kash.watch(() => {"))
        assertNotNull(PluginScriptCheck.check("const a = [1, 2);"))
    }
}

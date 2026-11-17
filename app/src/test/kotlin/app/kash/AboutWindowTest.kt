@file:OptIn(ExperimentalTestApi::class)

package app.kash

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import app.kash.ui.English
import app.kash.ui.French
import app.kash.ui.KashTheme
import app.kash.ui.LocalStrings
import java.io.File
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AboutWindowTest {
    @Test
    fun theWindowShowsTheLogoTheNameTheVersionAndTheCredits() = runComposeUiTest {
        setContent { KashTheme(false) { AboutContent() } }

        onNodeWithContentDescription("KaSH logo").assertIsDisplayed()
        onNodeWithText("KaSH").assertIsDisplayed()
        onNodeWithText(versionText(English)).assertIsDisplayed()
        onNodeWithText(commitDateText(English)!!).assertIsDisplayed()
        onNodeWithText("Made with love by Kodein Koders").assertIsDisplayed()
    }

    @Test
    fun itIsInKashsLanguage() = runComposeUiTest {
        setContent { CompositionLocalProvider(LocalStrings provides French) { KashTheme(false) { AboutContent() } } }

        onNodeWithContentDescription("Logo de KaSH").assertIsDisplayed()
        onNodeWithText("Fait avec amour par Kodein Koders").assertIsDisplayed()
    }

    @Test
    fun aBuildOfAnotherBranchThanMainSaysWhichOne() = runComposeUiTest {
        var branch by mutableStateOf("feature/rate-limit")
        setContent { KashTheme(false) { AboutContent(branch = branch) } }

        onNodeWithText("Branch feature/rate-limit").assertIsDisplayed()

        branch = "main"
        onNodeWithText("Branch", substring = true).assertDoesNotExist()
    }

    @Test
    fun theBranchIsSaidUnlessItIsMain() {
        assertEquals("Branch feature/rate-limit", branchText(English, "feature/rate-limit"))
        assertEquals("Branche feature/rate-limit", branchText(French, "feature/rate-limit"))
        assertNull(branchText(English, "main"))
        // A detached HEAD, or a build outside a checkout, has none.
        assertNull(branchText(English, ""))
    }

    @Test
    fun theVersionIsFollowedByTheCommit() {
        assertEquals("Version 1.2.3 (abc1234)", versionText(English, "1.2.3", "abc1234"))
        assertEquals("Version 1.2.3", versionText(English, "1.2.3", "unknown"))
        assertEquals("Version 1.2.3", versionText(English, "1.2.3", ""))
    }

    // The checkout that the test runs in, which the build was made from.
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    private fun git(vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git") + arguments).directory(root).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        return process.inputStream.readBytes().decodeToString().trim().also { process.waitFor() }
    }

    @Test
    fun theBuildKnowsTheProjectVersionAndTheCommit() {
        val (major, minor, patch) = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(BuildConfig.VERSION)?.destructured ?: error("Not a version: ${BuildConfig.VERSION}")
        // The build script and the test cannot share a value, so the root script is read: the two cannot drift.
        val declared = Regex("""version = "(\d+\.\d+)\.""").find(File(root, "build.gradle.kts").readText())?.groupValues?.get(1)
        assertEquals(declared, "$major.$minor")
        // The patch is the number of commits that the one it was built from comes after, plus one while the working tree has changes that are not
        // committed: the build of those changes has the version of the commit that they will be.
        val dirty = git("status", "--porcelain", "--ignore-submodules=dirty").isNotEmpty()
        assertEquals((git("rev-list", "--count", "HEAD").toIntOrNull() ?: 1) - 1 + (if (dirty) 1 else 0), patch.toInt())
        assertTrue(Regex("""[0-9a-f]{7,40}|unknown""").matches(BuildConfig.GIT_COMMIT), BuildConfig.GIT_COMMIT)
        // The branch it was built on, none for a detached HEAD.
        assertEquals(git("branch", "--show-current"), BuildConfig.GIT_BRANCH)
        // When the commit was made.
        assertEquals(git("log", "-1", "--format=%cI", "HEAD"), BuildConfig.GIT_COMMIT_DATE)
    }

    @Test
    fun theTimeOfTheCommitIsWrittenInTheUsersZone() {
        val commit = "2026-11-03T14:22:10+01:00"
        assertEquals("Committed: 2026-11-03 14:22", commitDateText(English, commit, ZoneId.of("Europe/Paris")))
        assertEquals("Committed: 2026-11-03 08:22", commitDateText(English, commit, ZoneId.of("America/New_York")))
        assertEquals("Commit du 2026-11-03 14:22", commitDateText(French, commit, ZoneId.of("Europe/Paris")))
        // The day too is the user's.
        assertEquals("Committed: 2026-11-02 18:30", commitDateText(English, "2026-11-03T00:30:00+01:00", ZoneId.of("America/New_York")))
        // A date that cannot be read, or none (outside a git checkout), is not shown.
        assertNull(commitDateText(English, "unknown", ZoneId.of("Europe/Paris")))
        assertNull(commitDateText(English, "", ZoneId.of("Europe/Paris")))
    }
}

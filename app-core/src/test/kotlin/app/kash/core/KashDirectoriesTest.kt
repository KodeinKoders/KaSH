package app.kash.core

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KashDirectoriesTest {
    @Test
    fun profileIsADirectChildOfTheProfilesRoot() {
        val directories = KashDirectories(Paths.get("/tmp/kash"))
        val id = ProfileId.random()
        assertEquals(directories.profiles, directories.profile(id).parent)
        assertEquals(id.value, directories.profile(id).fileName.toString())
    }

    @Test
    fun anIdCannotNameAnotherDirectory() {
        assertFailsWith<IllegalArgumentException> { ProfileId("Default") }
        assertFailsWith<IllegalArgumentException> { ProfileId("env-../x") }
        assertFailsWith<IllegalArgumentException> { ProfileId("envs/env-00000000-0000-0000-0000-000000000000") }
    }
}

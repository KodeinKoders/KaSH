package app.kash.core

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

private val logger = LoggerFactory.getLogger("app.kash.core.ProfileCleaner")

/**
 * Deletes the [KashState.deletedProfiles], and returns the state without them: what could not be deleted stays in the list, to be tried again at
 * the next launch.
 *
 * It must run before the browser engine starts (see [KashState.deletedProfiles]).
 */
@OptIn(ExperimentalPathApi::class)
public fun deleteClosedProfiles(state: KashState, directories: KashDirectories): KashState {
    val remaining = state.deletedProfiles.filter { id ->
        val profile: Path = directories.profile(id)
        try {
            if (Files.exists(profile)) profile.deleteRecursively()
            false
        } catch (e: Exception) {
            logger.warn("Cannot delete the profile {}, trying again at the next launch", profile, e)
            true
        }
    }
    return state.copy(deletedProfiles = remaining)
}

package app.kash.core

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Where KaSH keeps what it writes: the services and the settings, the browser profile of each service, and their icons.
 *
 * @param support The directory of the application's files. It is created when something is written in it.
 */
public class KashDirectories(public val support: Path) {
    /** What is saved of the services and the settings. */
    public val stateFile: Path get() = support.resolve("state.json")

    /**
     * The root of the browser profiles: Chromium's `root_cache_path`, which also holds what is not a profile's (the components it downloads,
     * the GPU caches), and the lock that keeps a second KaSH from using the same profiles.
     */
    public val profiles: Path get() = support.resolve("profiles")

    /** The images that the user chose as the icons of services ([ServiceIcon.Image]). */
    public val icons: Path get() = support.resolve("icons")

    /** The sites' icons, fetched once and kept ([ServiceIcon.Favicon]). */
    public val favicons: Path get() = support.resolve("favicons")

    /**
     * The profile [profile]: its cookies, cache, IndexedDB, local storage, service workers.
     *
     * It is a **direct child** of [profiles]. Chromium creates a profile only there: given `profiles/envs/<id>`, it logs "Cannot create profile at
     * path" and uses a profile that is not written to disk, while the request context still reports the path it was given.
     */
    public fun profile(profile: ProfileId): Path = profiles.resolve(profile.value)

    public companion object {
        /** The system property that changes the directory, for tests and for running a second copy of the application. */
        public const val PROPERTY: String = "kash.support"

        /**
         * `~/Library/Application Support/KaSH` on macOS, `$XDG_DATA_HOME/KaSH` or `~/.local/share/KaSH` elsewhere, unless [PROPERTY] says
         * another one.
         */
        public fun default(): KashDirectories {
            System.getProperty(PROPERTY)?.takeIf { it.isNotBlank() }?.let { return KashDirectories(Paths.get(it)) }
            val home = Paths.get(System.getProperty("user.home"))
            val support = if (System.getProperty("os.name").startsWith("Mac")) {
                home.resolve("Library").resolve("Application Support").resolve("KaSH")
            } else {
                (System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let { Paths.get(it) } ?: home.resolve(".local").resolve("share")).resolve("KaSH")
            }
            return KashDirectories(support)
        }
    }
}

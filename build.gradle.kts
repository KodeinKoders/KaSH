plugins {
    alias(libs.plugins.buildconfig) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
}

// The patch number of the version is the number of commits that the current one comes after in the history, as KaTTY's: each commit of a branch has
// its own, greater than the one before, and it stays a number, which the version of a macOS bundle must be. It is 0 outside a git checkout, and a
// shallow clone counts only what it fetched: a build that is distributed needs the whole history.
val commitsBefore = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { (it.trim().toIntOrNull() ?: 1) - 1 }

// A working tree with changes that are not committed (modified, staged or new files that git does not ignore) is the next commit: its patch number
// is one more, so that a build before the commit and one after it have the same version. What changes inside the jcef submodule is not a change of
// this repository (its build writes there); moving it to another commit is. Only "clean" or "dirty" is printed, so that editing a file that was
// already changed does not make the configuration cache miss: only a tree that becomes clean or dirty does.
val uncommitted = providers.exec {
    commandLine("sh", "-c", """[ -z "$(git status --porcelain --ignore-submodules=dirty 2>/dev/null)" ] && echo clean || echo dirty""")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() == "dirty" }

allprojects {
    group = "app.kash"
    // macOS does not accept a bundle version whose first number is 0.
    version = "1.0.${commitsBefore.get() + if (uncommitted.get()) 1 else 0}"
}

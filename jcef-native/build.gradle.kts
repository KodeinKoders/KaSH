import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(findProperty("jvm.toolchain")!!.toString().toInt())
}

// JetBrains' JCEF is built from the `jcef` submodule, whose commit says which one (`git submodule status`): JetBrains publishes the jar of each
// build, but not the native libraries, and the jar calls them through JNI, so both have to come from the same commit. The commit is one of the
// `dev` branch that has `CefRequestContext.createContext(CefRequestContextSettings, …)`, which gives each environment its own profile on disk.
val jcefDirectory = rootProject.layout.projectDirectory.dir("jcef")

// Apache Ant, which JCEF's CMake build runs to compile and package the Java side. Downloaded and checked here rather than asked of the machine:
// Homebrew's `ant` pulls a JDK of its own.
abstract class DownloadAnt : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    /** The SHA-512 that Apache publishes next to the archive (`apache-ant-<version>-bin.tar.gz.sha512`). */
    @get:Input
    abstract val sha512: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val files: FileSystemOperations

    @get:Inject
    abstract val archives: ArchiveOperations

    @TaskAction
    fun download() {
        val name = "apache-ant-${version.get()}-bin.tar.gz"
        val archive = temporaryDir.resolve(name)
        URI("https://archive.apache.org/dist/ant/binaries/$name").toURL().openStream().use { input ->
            archive.outputStream().use { input.copyTo(it) }
        }
        val actual = MessageDigest.getInstance("SHA-512").digest(archive.readBytes()).joinToString("") { "%02x".format(it) }
        check(actual == sha512.get()) { "$name has the SHA-512 $actual, not the expected ${sha512.get()}" }
        files.sync {
            from(archives.tarTree(archive)) {
                // The archive has everything in `apache-ant-<version>/`: the bin directory is at the root of the output.
                eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
                includeEmptyDirs = false
            }
            into(outputDirectory)
        }
    }
}

val downloadAnt = tasks.register<DownloadAnt>("downloadAnt") {
    group = "build"
    description = "Downloads and checks Apache Ant, which the build of JCEF runs."
    version = libs.versions.ant
    sha512 = "d78427aff207592c024ff1552dc04f7b57065a195c42d398fcffe7a0145e8d00cd46786f5aa52e77ab0fdf81334f065eb8011eecd2b48f7228e97ff4cb20d16c"
    outputDirectory = layout.buildDirectory.dir("tools/ant")
}

// KaSH's fixes to JetBrains' JCEF: the files of `patches/` (made with `git -C jcef diff`), applied to the submodule's checkout before it is built,
// in the order of their names. One that is already applied (`git apply --reverse --check`) is left as it is; one that does not apply any more
// (after the submodule moved) fails the build, and has to be made again or dropped when JetBrains fixed it.
abstract class PatchJcef @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val patches: ConfigurableFileCollection

    /** The submodule's checkout, which the patches change. */
    @get:Internal
    abstract val sourceDirectory: DirectoryProperty

    @TaskAction
    fun patch() {
        val checkout = sourceDirectory.get().asFile.absolutePath
        for (patch in patches.files.sortedBy { it.name }) {
            val apply = { arguments: List<String> ->
                val errors = ByteArrayOutputStream()
                val result = exec.exec {
                    commandLine(listOf("git", "-C", checkout, "apply") + arguments + patch.absolutePath)
                    errorOutput = errors
                    isIgnoreExitValue = true
                }
                result.exitValue to errors.toString()
            }
            if (apply(listOf("--reverse", "--check")).first == 0) continue
            val (exit, errors) = apply(emptyList())
            check(exit == 0) { "${patch.name} does not apply to the jcef submodule:\n$errors" }
            logger.lifecycle("Applied ${patch.name} to the jcef submodule")
        }
    }
}

val patchJcef = tasks.register<PatchJcef>("patchJcef") {
    group = "build"
    description = "Applies KaSH's fixes (jcef-native/patches) to the jcef submodule."
    patches.from(layout.projectDirectory.dir("patches").asFileTree.matching { include("*.patch") })
    sourceDirectory = jcefDirectory
    // Nothing to compare: whether a patch is applied is read from the checkout each time.
    outputs.upToDateWhen { false }
}

// Configures and builds JCEF with CMake and Ninja, as JetBrains' `jb/tools/mac/build.sh` does with Xcode: Ninja only needs the Command Line Tools,
// not Xcode. The CMake build downloads the CEF distribution into the submodule (`third_party/cef`) and builds the dependencies of the
// out-of-process server with vcpkg (`third_party/vcpkg`, `third_party/thrift_compiler`), which the submodule ignores (`ignore = untracked` in
// .gitmodules): the first build takes several minutes, the next ones only rebuild what changed.
abstract class BuildJcef @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    /** What JCEF is built from: the submodule, without what its build downloads and generates in it. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** The submodule's checkout, which CMake is pointed to. Not an input as a whole: [sources] is. */
    @get:Internal
    abstract val sourceDirectory: DirectoryProperty

    /** The JDK that compiles the Java side and that the JNI headers are taken from: the toolchain's. */
    @get:Internal
    abstract val javaHome: DirectoryProperty

    /** Where [DownloadAnt] put Ant. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val antDirectory: DirectoryProperty

    /** Where `sitecustomize.py` is, which makes JCEF's tools take the submodule for a checkout. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pythonPath: DirectoryProperty

    /** The CPU of the build: `arm64` or `x86_64`. */
    @get:Input
    abstract val architecture: Property<String>

    /** The CMake build directory: `native/Release` has libjcef, the helpers and `jcef.jar`, `remote/Release` the out-of-process server. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun build() {
        // What Homebrew installs, and the bison that it does not link (macOS has a bison 2.3, and vcpkg needs 2.7 or newer). The PATH that Gradle
        // has does not always have Homebrew's directory: it is not the one of a shell when the IDE starts Gradle.
        val path = listOf(
            antDirectory.get().dir("bin").asFile.absolutePath,
            "/opt/homebrew/opt/bison/bin",
            "/usr/local/opt/bison/bin",
            "/opt/homebrew/bin",
            "/usr/local/bin",
            System.getenv("PATH") ?: "/usr/bin:/bin",
        ).joinToString(":")
        val missing = listOf("cmake", "ninja", "pkg-config", "bison").filter { tool ->
            path.split(":").none { File(it, tool).canExecute() }
        }
        check(missing.isEmpty()) { "Building JCEF needs ${missing.joinToString()}: brew install cmake ninja pkgconf bison" }
        val environment = mapOf(
            "PATH" to path,
            "JAVA_HOME" to javaHome.get().asFile.absolutePath,
            "PYTHONPATH" to pythonPath.get().asFile.absolutePath,
            // Keeps tar from adding the `._` files of macOS's extended attributes, as JetBrains' build script does.
            "COPYFILE_DISABLE" to "1",
        )
        val buildDirectory = outputDirectory.get().asFile
        exec.exec {
            environment(environment)
            commandLine(
                "cmake", "-G", "Ninja",
                "-S", sourceDirectory.get().asFile.absolutePath,
                "-B", buildDirectory.absolutePath,
                "-DCMAKE_BUILD_TYPE=Release",
                // The oldest macOS that JetBrains builds it for.
                "-DCMAKE_OSX_DEPLOYMENT_TARGET=13.0",
                "-DPROJECT_ARCH=${architecture.get()}",
            )
        }
        exec.exec {
            environment(environment)
            commandLine("ninja", "-C", buildDirectory.absolutePath)
        }
    }
}

val buildJcef = tasks.register<BuildJcef>("buildJcef") {
    group = "build"
    description = "Builds JetBrains' JCEF (libjcef, its helpers, jcef.jar) from the jcef submodule, with CMake and Ninja."
    sourceDirectory = jcefDirectory
    // Its sources with KaSH's fixes.
    dependsOn(patchJcef)
    sources.from(jcefDirectory.asFileTree.matching {
        // What the build downloads and generates in the submodule, which would make the task run again each time.
        exclude("third_party/cef/cef_binary_*", "third_party/cef/cef_binary_*/**", "third_party/vcpkg/**", "third_party/thrift_compiler/**")
        exclude("native/jcef_version.h", "jcef_build/**", "out/**", "*.tar.gz")
    })
    javaHome = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(findProperty("jvm.toolchain")!!.toString().toInt())
        vendor = JvmVendorSpec.JETBRAINS
    }.map { it.metadata.installationPath }
    antDirectory = downloadAnt.flatMap { it.outputDirectory }
    pythonPath = layout.projectDirectory.dir("python")
    architecture = when (val arch = System.getProperty("os.arch")) {
        "aarch64", "arm64" -> "arm64"
        "x86_64", "amd64" -> "x86_64"
        else -> error("JCEF is not built for $arch")
    }
    outputDirectory = layout.buildDirectory.dir("cmake")
}

// The native bundle that JCefAppConfig.getInstance(path, false) loads JCEF from, for CEF in the process: libjcef.dylib, and in `Frameworks/` the
// helpers and the CEF framework, **next to each other**: a helper loads the framework from `../../../Chromium Embedded Framework.framework`, and
// Chromium aborts ("GPU process isn't usable") when it is not there.
// JetBrains' `jcef_native_bundle_mac_*` (`jb/tools/mac/create_bundle.sh`) has the framework inside `Frameworks/cef_server.app` and a symbolic link
// to it next to the helpers. Compose's copy of the resources would follow that link and copy the 220 MB framework twice, so the out-of-process
// server is left out and the framework is where the helpers look: BrowserEngine gives CEF that path (`--framework-dir-path`).
val jcefBundle = tasks.register<Sync>("jcefBundle") {
    group = "build"
    description = "Lays out the JCEF native bundle that the application loads JCEF from."
    val release = buildJcef.flatMap { it.outputDirectory }
    from(release.map { it.file("native/Release/libjcef.dylib") })
    from(release.map { it.dir("native/Release") }) {
        include("jcef Helper*.app/**")
        into("Frameworks")
    }
    from(release.map { it.dir("native/Release/jcef_app.app/Contents/Frameworks/Chromium Embedded Framework.framework") }) {
        into("Frameworks/Chromium Embedded Framework.framework")
    }
    into(layout.buildDirectory.dir("bundle/jcef"))
}

// The bundle, for the application: it is in its resources, from which it is loaded at run time.
configurations.consumable("jcefBundle") {
    outgoing.artifact(jcefBundle.map { it.destinationDir }) { builtBy(jcefBundle) }
}

dependencies {
    // JCEF's Java side, built with the native libraries: `org.cef` is part of the API of the modules that use it.
    api(files(buildJcef.flatMap { it.outputDirectory.file("native/Release/jcef.jar") }).builtBy(buildJcef))
}

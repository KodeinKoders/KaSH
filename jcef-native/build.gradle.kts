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

// CEF, built with the proprietary codecs (H.264, AAC: the MP4 videos of the services) by github.com/KodeinKoders/BoCEFwPC. The builds that JCEF's
// CMake downloads (JetBrains' or Spotify's) do not have them, so this distribution is put where that CMake looks for it, the directory
// `third_party/cef/cef_binary_<version>_<platform>_minimal` of the submodule, before it runs: CMake downloads nothing when it is there.
// It is only put there whole (extracted next to it, then moved), as CMake would take a half-extracted one for a complete one.
abstract class DownloadCef @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    /** CEF's version, as in the names of the official archives. */
    @get:Input
    abstract val version: Property<String>

    /** CEF's name of the platform: `macosarm64`. */
    @get:Input
    abstract val platform: Property<String>

    /** The SHA-256 of the archive, which BoCEFwPC publishes next to it (`….tar.bz2.sha256`). */
    @get:Input
    abstract val sha256: Property<String>

    /** Where CMake looks for the distribution: `third_party/cef/cef_binary_<version>_<platform>_minimal` in the submodule. */
    @get:OutputDirectory
    abstract val distributionDirectory: DirectoryProperty

    @TaskAction
    fun download() {
        val distribution = distributionDirectory.get().asFile
        val name = "${distribution.name}.tar.bz2"
        // The tag of the release is the version, and `+` has to be escaped in the URL.
        val url = "https://github.com/KodeinKoders/BoCEFwPC/releases/download/${version.get()}/$name".replace("+", "%2B")
        val archive = temporaryDir.resolve(name)
        logger.lifecycle("Downloading $url")
        URI(url).toURL().openStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
        val digest = MessageDigest.getInstance("SHA-256")
        archive.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual == sha256.get()) { "$name has the SHA-256 $actual, not the expected ${sha256.get()}" }
        // macOS's tar, which keeps the modes and the links of the framework. The archive has everything in `<its name>/`. What is deleted here is
        // deleted with `rm -rf`, never deleteRecursively, which follows the links (see AGENTS.md › Running the app).
        val extracted = temporaryDir.resolve("extracted")
        exec.exec { commandLine("rm", "-rf", extracted.absolutePath) }
        extracted.mkdirs()
        exec.exec { commandLine("tar", "-xjf", archive.absolutePath, "-C", extracted.absolutePath) }
        val root = extracted.resolve(distribution.name)
        check(root.resolve("Release/Chromium Embedded Framework.framework").isDirectory) { "$name has no Release/Chromium Embedded Framework.framework" }
        // Gradle makes the output directory before the task runs (empty), and an earlier extraction may have left one.
        if (distribution.exists()) exec.exec { commandLine("rm", "-rf", distribution.absolutePath) }
        check(root.renameTo(distribution)) { "Could not move $root to $distribution" }
        archive.delete()
        extracted.delete()
    }
}

val downloadCef = tasks.register<DownloadCef>("downloadCef") {
    group = "build"
    description = "Downloads CEF with the proprietary codecs (KodeinKoders/BoCEFwPC) where the build of JCEF takes it."
    version = libs.versions.cef
    platform = when (val arch = System.getProperty("os.arch")) {
        "aarch64", "arm64" -> "macosarm64"
        // BoCEFwPC has no other platform yet: add it there (platforms.json) first, then its SHA-256 below.
        else -> error("KodeinKoders/BoCEFwPC has no build of CEF for macOS on $arch")
    }
    // By platform, as BoCEFwPC's release of the version publishes them. To change with the version.
    sha256 = platform.map {
        mapOf(
            "macosarm64" to "f3fd539c86b7bd0fd42220169b1573532ec1c11451e66714e28352d543486e1d",
        ).getValue(it)
    }
    distributionDirectory = jcefDirectory.dir(version.zip(platform) { version, platform -> "third_party/cef/cef_binary_${version}_${platform}_minimal" })
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

    /** The JDK's path, which makes the task run again when the JDK changes: [javaHome] is not an input as a whole, which would hash the JDK. */
    @get:Input
    abstract val javaHomePath: Property<String>

    /** Where [DownloadAnt] put Ant. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val antDirectory: DirectoryProperty

    /** Where `sitecustomize.py` is, which makes JCEF's tools take the submodule for a checkout. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pythonPath: DirectoryProperty

    /** The CEF distribution that [DownloadCef] put where CMake looks for it. Not an input as a whole (hundreds of MB to hash): [cefVersion] is. */
    @get:Internal
    abstract val cefDistribution: DirectoryProperty

    /** CEF's version, which CMake is given (`CEF_VERSION`, which says which distribution it takes). */
    @get:Input
    abstract val cefVersion: Property<String>

    /** The CPU of the build: `arm64` or `x86_64`. */
    @get:Input
    abstract val architecture: Property<String>

    /** The CMake build directory: `native/Release` has libjcef, the helpers and `jcef.jar`, `remote/Release` the out-of-process server. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun build() {
        // The toolchain's JDK must not have JCEF: its `jcef` module would win over the `jcef.jar` built here (see JbrToolchainPlugin). Gradle keeps
        // using a `jbrsdk_jcef` that an earlier build downloaded, as it satisfies "the JetBrains Runtime": it has to be deleted to get the right one.
        val jdk = javaHome.get().asFile
        val modules = jdk.resolve("release").takeIf { it.isFile }?.readLines()?.firstOrNull { it.startsWith("MODULES=") }.orEmpty()
        check(!Regex("""[ "]jcef[ "]""").containsMatchIn(modules)) {
            "$jdk has JCEF, and KaSH needs a JetBrains Runtime without it. Delete ${jdk.parentFile.parentFile.parentFile} " +
                "(rm -rf), and Gradle downloads the right one."
        }
        // CMake keeps the paths of the JDK it was configured with (`Java_JAVA_EXECUTABLE`, the JNI headers and libraries…) and fails when it is not
        // there any more ("Java version check failed"), as after a new JetBrains Runtime. The whole file goes, never some of its lines: CMake
        // writes it again, and Ninja rebuilds only what changed.
        val cache = outputDirectory.get().asFile.resolve("CMakeCache.txt")
        val configuredJava = cache.takeIf { it.isFile }
            ?.useLines { lines -> lines.firstOrNull { it.startsWith("Java_JAVA_EXECUTABLE:") } }
            ?.substringAfter('=')
        if (configuredJava != null && !configuredJava.startsWith(jdk.absolutePath)) {
            logger.lifecycle("Deleted the CMake cache of another JDK: $configuredJava")
            check(cache.delete()) { "Could not delete $cache" }
        }
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
                // Not the one that JCEF's CMakeLists.txt pins: the distribution with the codecs, which is there (see DownloadCef).
                "-DCEF_VERSION=${cefVersion.get()}",
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
    // Its sources with KaSH's fixes, and CEF where CMake takes it (cefDistribution is internal: no dependency is inferred from it).
    dependsOn(patchJcef, downloadCef)
    sources.from(jcefDirectory.asFileTree.matching {
        // What the build downloads and generates in the submodule, which would make the task run again each time.
        exclude("third_party/cef/cef_binary_*", "third_party/cef/cef_binary_*/**", "third_party/vcpkg/**", "third_party/thrift_compiler/**")
        exclude("native/jcef_version.h", "jcef_build/**", "out/**", "*.tar.gz")
    })
    javaHome = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(findProperty("jvm.toolchain")!!.toString().toInt())
        vendor = JvmVendorSpec.JETBRAINS
    }.map { it.metadata.installationPath }
    javaHomePath = javaHome.asFile.map { it.absolutePath }
    antDirectory = downloadAnt.flatMap { it.outputDirectory }
    cefDistribution = downloadCef.flatMap { it.distributionDirectory }
    cefVersion = downloadCef.flatMap { it.version }
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

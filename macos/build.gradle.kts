import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(findProperty("jvm.toolchain")!!.toString().toInt())
    explicitApi()
}

// Compiles the Objective-C of native/ into a dynamic library, with the clang of the Command Line Tools (which JCEF's build needs too): the
// Notification Center and the login items have no Java API.
abstract class BuildMacosLibrary @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** The CPU of the build: `arm64` or `x86_64`. */
    @get:Input
    abstract val architecture: Property<String>

    @get:OutputFile
    abstract val library: RegularFileProperty

    @TaskAction
    fun build() {
        exec.exec {
            commandLine(
                listOf(
                    "clang", "-dynamiclib", "-fobjc-arc", "-O2",
                    "-arch", architecture.get(),
                    // SMAppService is macOS 13's.
                    "-mmacosx-version-min=13.0",
                    "-framework", "Foundation", "-framework", "UserNotifications", "-framework", "ServiceManagement",
                    "-o", library.get().asFile.absolutePath,
                ) + sources.files.map { it.absolutePath },
            )
        }
    }
}

val buildMacosLibrary = tasks.register<BuildMacosLibrary>("buildMacosLibrary") {
    group = "build"
    description = "Compiles libkash_macos.dylib, the notifications and the login item of KaSH."
    sources.from(layout.projectDirectory.dir("native").asFileTree.matching { include("*.m") })
    architecture = when (val arch = System.getProperty("os.arch")) {
        "aarch64", "arm64" -> "arm64"
        "x86_64", "amd64" -> "x86_64"
        else -> error("KaSH is not built for $arch")
    }
    library = layout.buildDirectory.file("native/libkash_macos.dylib")
}

// The library is in the jar, next to the classes that load it (MacosLibrary extracts it).
tasks.processResources {
    from(buildMacosLibrary) { into("app/kash/macos") }
}

dependencies {
    implementation(libs.slf4j.api)
}

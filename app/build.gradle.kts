import org.gradle.jvm.toolchain.JvmVendorSpec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.io.File
import java.nio.file.Files

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val jvmToolchainVersion = findProperty("jvm.toolchain")!!.toString().toInt()

kotlin {
    // Jewel's decorated window draws the title bar of the window and needs the JetBrains Runtime for it (it is what gives the native
    // buttons of macOS a place in our title bar). It must be one WITHOUT JCEF (not jbr_jcef / jbrsdk_jcef): its own `jcef` module would
    // replace the JCEF that jcef-native builds (BrowserEngine.start checks it).
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(jvmToolchainVersion)
        vendor = JvmVendorSpec.JETBRAINS
    }
    compilerOptions {
        // Jewel's Tooltip is built on Compose's TooltipArea, which is experimental.
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
    }
}

// Jewel's icons depend on IntelliJ's fork of kotlinx-coroutines, which has the same classes under another group: Gradle kept both jars, and which
// one is loaded depends on their order. The packaged application has them in alphabetical order, the fork first, and the code compiled against
// kotlinx-coroutines failed in it (the fork's Job has no `cancel$default`). The icons only use the API of kotlinx-coroutines itself (checked on
// Jewel 0.41 and 0.42), which replaces the fork.
val coroutinesFork = libs.kotlinx.coroutines.intellij.get().module.toString()
val coroutines = libs.kotlinx.coroutines.asProvider().get().let { "${it.module}:${it.versionConstraint.requiredVersion}" }
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module(coroutinesFork))
            .using(module(coroutines))
            .because("IntelliJ's fork has the classes of kotlinx-coroutines, and code compiled against kotlinx-coroutines fails with it")
    }
}

// The JCEF native bundle that jcef-native builds, which the application loads Chromium from.
val jcefBundle = configurations.dependencyScope("jcefBundle")
val jcefBundleFiles = configurations.resolvable("jcefBundleFiles") { extendsFrom(jcefBundle.get()) }

dependencies {
    implementation(projects.appCore)
    implementation(projects.browser)
    implementation(projects.macos)

    jcefBundle(projects.jcefNative) {
        targetConfiguration = "jcefBundle"
    }

    // Jewel has its own components: the Material ones are not wanted.
    implementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }

    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)

    implementation(libs.jewel.decorated.window)
    implementation(libs.jewel.int.ui)

    implementation(libs.kotlinx.coroutines.swing)
    // The messages that the pages send (notification-shim.js) are JSON.
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.slf4j.api)

    // Prints the logs of the libraries that log with SLF4J.
    runtimeOnly(libs.slf4j.simple)
}

// The icon of the application is the logo of the repository on a rounded square of this colour, the shape that macOS wants: since macOS 26 it puts
// the icon of another shape on a grey one. The square, its colour and its shadow are KaTTY's (make-icon.java).
val iconBackground = "#FDDDD6"
val appIconFile = layout.buildDirectory.file("icons/icon.png")
val appIcon = tasks.register<Exec>("appIcon") {
    group = "build"
    description = "Draws the icon of the application (icon.png): the logo on a rounded square."
    val logo = rootProject.file("logo.png")
    inputs.file(logo)
    inputs.file("make-icon.java")
    inputs.property("background", iconBackground)
    outputs.file(appIconFile)
    // In a Java process of its own, without a screen: AWT in the process of Gradle would give it an icon in the Dock.
    val java = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(jvmToolchainVersion)
        vendor = JvmVendorSpec.JETBRAINS
    }.get().executablePath.asFile.absolutePath
    commandLine(java, "-Djava.awt.headless=true", "make-icon.java", logo.absolutePath, iconBackground, appIconFile.get().asFile.absolutePath)
}

// The logo is the icon of the windows, and the icon of the application the one of the Dock while the application runs: they are resources of it.
tasks.processResources {
    from(rootProject.file("logo.png")) { into("app/kash") }
    from(appIcon) { into("app/kash") }
}

// The icon of the application that is packaged (the bundle and its disk image) is made from the same icon, with the tools of macOS: the only
// platform that the application is packaged for.
val macIcon = tasks.register<Exec>("macIcon") {
    group = "build"
    description = "Makes the macOS icon (KaSH.icns) of the packaged application from the icon of the application."
    val icns = layout.buildDirectory.file("icons/KaSH.icns")
    inputs.files(appIcon)
    inputs.file("make-icns.sh")
    outputs.file(icns)
    commandLine("sh", "make-icns.sh", appIconFile.get().asFile.absolutePath, icns.get().asFile.absolutePath)
}

tasks.withType<AbstractJPackageTask>().configureEach {
    dependsOn(macIcon)
}

// The bundle is a resource of the application, in `jcef/`: Compose copies `macos/` into the packaged application, and points the run task to it
// too (`compose.application.resources.dir`). The bundle is built for the machine's CPU only.
val jcefResources = tasks.register<Sync>("jcefResources") {
    group = "build"
    description = "Puts the JCEF native bundle in the resources of the application."
    from(jcefBundleFiles) { into("macos/jcef") }
    into(layout.buildDirectory.dir("jcef-resources"))
}

// `./gradlew :app:run -Pkash.inspect` (or `-Pkash.inspect=<port>`) opens Chromium's remote debugging on 127.0.0.1:9222, which the
// kash-devtools MCP server of .mcp.json connects to, to inspect the pages of the services once they are logged in. Only for the run task: it is
// never given to the packaged application, which refuses it anyway (Main.kt), since it lets any program of the machine drive the services.
val inspectPort = providers.gradleProperty("kash.inspect").map { it.toIntOrNull() ?: 9222 }
// The run task's JVM is started from a small application bundle, `build/dev/KaSH.app`, whose executable is a copy of the toolchain's `java`
// launcher. Started as `java`, macOS takes the process for the JetBrains Runtime's (`com.jetbrains.jbr.java`), and the application switcher (⌘Tab)
// says "java" whatever `-Xdock:name` says. The launcher finds the runtime through the links next to it (`Contents/lib`…): dyld resolves them, so
// `java.home` is the toolchain's. Its identifier is not the application's (`app.kash`), so that the Notification Center and the login items do not
// take it for the installed application.
abstract class DevBundle : DefaultTask() {
    private companion object {
        /** What the bundle links to in the JDK, next to the launcher. */
        val LINKS = listOf("lib", "conf", "legal", "release")
    }

    /** The `Contents/Home` of the toolchain's JDK. */
    @get:Input
    abstract val javaHome: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val icon: RegularFileProperty

    @get:OutputDirectory
    abstract val bundle: DirectoryProperty

    @TaskAction
    fun make() {
        val contents = bundle.get().asFile.resolve("Contents")
        // The links to the JDK's directories are deleted as links, before the rest: File.deleteRecursively follows them, and emptied the JDK's
        // own `lib`, `conf` and `legal` once.
        for (name in LINKS) Files.deleteIfExists(contents.resolve(name).toPath())
        contents.deleteRecursively()
        contents.resolve("MacOS").mkdirs()
        contents.resolve("Resources").mkdirs()
        val home = File(javaHome.get())
        home.resolve("bin/java").copyTo(contents.resolve("MacOS/KaSH"), overwrite = true).setExecutable(true, false)
        for (name in LINKS) {
            Files.createSymbolicLink(contents.resolve(name).toPath(), home.resolve(name).toPath())
        }
        icon.get().asFile.copyTo(contents.resolve("Resources/KaSH.icns"), overwrite = true)
        contents.resolve("Info.plist").writeText(
            """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            |<plist version="1.0">
            |<dict>
            |    <key>CFBundleName</key><string>KaSH</string>
            |    <key>CFBundleDisplayName</key><string>KaSH</string>
            |    <key>CFBundleIdentifier</key><string>app.kash.dev</string>
            |    <key>CFBundleExecutable</key><string>KaSH</string>
            |    <key>CFBundleIconFile</key><string>KaSH.icns</string>
            |    <key>CFBundlePackageType</key><string>APPL</string>
            |    <key>NSHighResolutionCapable</key><true/>
            |</dict>
            |</plist>
            |""".trimMargin(),
        )
    }
}

val devBundle = tasks.register<DevBundle>("devBundle") {
    group = "application"
    description = "Makes build/dev/KaSH.app, the bundle that the run task starts the JVM from, so that macOS names it KaSH."
    javaHome = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(jvmToolchainVersion)
        vendor = JvmVendorSpec.JETBRAINS
    }.map { it.metadata.installationPath.asFile.absolutePath }
    icon = macIcon.map { layout.buildDirectory.file("icons/KaSH.icns").get() }
    bundle = layout.buildDirectory.dir("dev/KaSH.app")
}

// Compose registers `run` after this script, hence `matching` rather than `named`; and it sets the task's jvmArgs itself, replacing any that are
// added here, hence a system property.
tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    inspectPort.orNull?.let { systemProperty("kash.inspect.port", it) }
    dependsOn(devBundle)
    // A launcher of the toolchain's JDK whose executable is the bundle's copy of its `java`, and whose installation is the bundle's `Contents`
    // (where `lib`, `conf` and `release` are the JDK's): Gradle checks that the task's executable is in its launcher's installation.
    val toolchain = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(jvmToolchainVersion)
        vendor = JvmVendorSpec.JETBRAINS
    }
    // The bundle's place, not the task's output: the task runs after the run task is configured (`dependsOn` above).
    val contents = layout.buildDirectory.dir("dev/KaSH.app/Contents")
    javaLauncher.set(
        toolchain.zip(contents) { jdk, bundle ->
            val metadata = jdk.metadata
            object : JavaLauncher {
                override fun getMetadata() = object : JavaInstallationMetadata by metadata {
                    override fun getInstallationPath() = bundle
                }
                override fun getExecutablePath() = bundle.file("MacOS/KaSH")
            }
        },
    )
    // Compose sets the executable to its `javaHome`'s `java` after this: it is set again when the task runs, to the launcher's.
    val executable = contents.get().file("MacOS/KaSH").asFile.absolutePath
    doFirst { (this as JavaExec).setExecutable(executable) }
}

// Compose's task that copies the resources reads the directory, not the task that fills it.
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(jcefResources) }

// jpackage copies the resources into the bundle without their permissions: the helpers of Chromium and its framework lost their executable bit,
// and Chromium could not start its processes. This gives it back to every file that has it in the JCEF bundle. A signature does not cover the
// permissions, so the bundle's stays valid.
abstract class RestoreExecutableBits : DefaultTask() {
    /** The JCEF bundle as it is built, whose files say which are executable. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: DirectoryProperty

    /** The same bundle in the application bundle that jpackage made. */
    @get:Internal
    abstract val target: DirectoryProperty

    @TaskAction
    fun restore() {
        val target = target.get().asFile
        if (!target.isDirectory) return
        source.get().asFileTree.visit {
            if (!isDirectory && file.canExecute()) relativePath.getFile(target).takeIf { it.isFile }?.setExecutable(true, false)
        }
    }
}

val restoreExecutableBits = tasks.register<RestoreExecutableBits>("restoreExecutableBits") {
    group = "build"
    description = "Gives back their executable bit to the JCEF files of the application bundle, which jpackage drops."
    source = jcefResources.map { layout.projectDirectory.dir(it.destinationDir.resolve("macos/jcef").absolutePath) }
    target = layout.buildDirectory.dir("compose/binaries/main/app/KaSH.app/Contents/app/resources/jcef")
}
tasks.matching { it.name == "createDistributable" }.configureEach { finalizedBy(restoreExecutableBits) }

// What JCEF needs of the JDK, given to every JVM that runs the application.
val jcefJvmArgs = listOf(
    // JCEF puts Chromium's native view in the AWT window through the internals of AWT (AWTAccessor, the macOS platform window).
    "--add-exports", "java.desktop/sun.awt=ALL-UNNAMED",
    "--add-exports", "java.desktop/sun.lwawt=ALL-UNNAMED",
    "--add-exports", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
    // JCEF loads its native library with System.load, which is restricted: the JVM warns without this flag.
    "--enable-native-access=ALL-UNNAMED",
)

compose {
    desktop.application {
        mainClass = "app.kash.MainKt"

        // The run task starts the JVM that runs Gradle unless it is told which one: it has to be the toolchain's, which is the JetBrains Runtime.
        javaHome = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(jvmToolchainVersion)
            vendor = JvmVendorSpec.JETBRAINS
        }.get().metadata.installationPath.asFile.absolutePath

        jvmArgs += jcefJvmArgs
        // The name of the process in the Dock and the application switcher (⌘Tab), which is `java` otherwise when it is run by Gradle; the
        // packaged application has its bundle's name. The menu bar's name is set in `main` (AppName.kt).
        jvmArgs += "-Xdock:name=KaSH"

        nativeDistributions {
            appResourcesRootDir.set(layout.buildDirectory.dir("jcef-resources"))
            // What jlink keeps of the JDK beyond Compose's own (`./gradlew :app:suggestRuntimeModules`): java.net.http fetches the sites' icons.
            modules("java.instrument", "java.net.http", "jdk.unsupported")
            // The disk image is for macOS: the only platform the JCEF bundle is built for.
            targetFormats(TargetFormat.Dmg)
            packageName = "KaSH"
            packageVersion = project.version.toString()
            description = "Isolated, persistent Chromium environments, one per tab"
            vendor = "Kodein Koders"
            jvmArgs += jcefJvmArgs

            macOS {
                bundleID = "app.kash"
                dockName = "KaSH"
                iconFile.set(layout.buildDirectory.file("icons/KaSH.icns"))
            }
        }
    }
}

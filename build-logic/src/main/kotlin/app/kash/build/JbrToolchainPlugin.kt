package app.kash.build

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.jvm.toolchain.JavaToolchainDownload
import org.gradle.jvm.toolchain.JavaToolchainRequest
import org.gradle.jvm.toolchain.JavaToolchainResolver
import org.gradle.jvm.toolchain.JavaToolchainResolverRegistry
import org.gradle.jvm.toolchain.JvmToolchainManagement
import org.gradle.platform.Architecture
import org.gradle.platform.OperatingSystem
import java.net.URI
import java.util.Optional
import javax.inject.Inject

/**
 * Serves the JetBrains Runtime **without JCEF** (`jbrsdk`, not `jbrsdk_jcef`) to the toolchains that ask for the JetBrains vendor.
 *
 * KaSH builds its own JCEF (the `jcef` submodule), whose jar the JBR's own `jcef` module would replace, so the JBR it runs on must not have one.
 * Foojay, which Gradle asks for the other toolchains, only has the `jbrsdk_jcef` builds of the JetBrains Runtime.
 *
 * It has to be applied **before** Foojay's resolver in `settings.gradle.kts`: the resolvers are asked in the order they are registered.
 * Gradle asks a resolver only when no JDK it already knows satisfies the request, and a `jbrsdk_jcef` already in `~/.gradle/jdks` does:
 * `buildJcef` fails on one (the message names what to delete), after which this resolver is the one that provides the JDK.
 */
class JbrToolchainPlugin @Inject constructor(private val registry: JavaToolchainResolverRegistry) : Plugin<Settings> {
    override fun apply(settings: Settings) {
        settings.plugins.apply("jvm-toolchain-management")
        registry.register(JbrToolchainResolver::class.java)
        val management = settings.toolchainManagement.extensions.getByType(JvmToolchainManagement::class.java)
        management.javaRepositories.repository("jbr") {
            resolverClass.set(JbrToolchainResolver::class.java)
        }
    }
}

abstract class JbrToolchainResolver : JavaToolchainResolver {
    override fun resolve(request: JavaToolchainRequest): Optional<JavaToolchainDownload> {
        val specification = request.javaToolchainSpec
        val build = JBR_BUILDS[specification.languageVersion.get().asInt()] ?: return Optional.empty()
        if (!specification.vendor.get().matches("JetBrains")) return Optional.empty()
        val system = when (request.buildPlatform.operatingSystem) {
            OperatingSystem.MAC_OS -> "osx"
            OperatingSystem.LINUX -> "linux"
            OperatingSystem.WINDOWS -> "windows"
            else -> return Optional.empty()
        }
        val architecture = when (request.buildPlatform.architecture) {
            Architecture.AARCH64 -> "aarch64"
            Architecture.X86_64 -> "x64"
            else -> return Optional.empty()
        }
        return Optional.of(JavaToolchainDownload.fromUri(URI("$BASE_URL/jbrsdk-${build.version}-$system-$architecture-b${build.build}.tar.gz")))
    }

    private class Build(val version: String, val build: String)

    private companion object {
        const val BASE_URL = "https://cache-redirector.jetbrains.com/intellij-jbr"

        /**
         * The JBR that everybody builds and runs KaSH with, by Java version: the release `jbr-release-<version>b<build>` of
         * https://github.com/JetBrains/JetBrainsRuntime/releases. Bump it here, for the whole team.
         */
        val JBR_BUILDS = mapOf(
            25 to Build(version = "25.0.5.1", build = "635.102"),
        )
    }
}

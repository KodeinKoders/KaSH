plugins {
    `kotlin-dsl`
}

// No `jvmToolchain` here, unlike the other modules: this build provides the toolchain resolver, so it is compiled before any toolchain can be
// resolved, and asking for one would need the very plugin that is being compiled.

gradlePlugin {
    plugins {
        register("jbrToolchain") {
            id = "app.kash.jbr-toolchain"
            implementationClass = "app.kash.build.JbrToolchainPlugin"
        }
    }
}

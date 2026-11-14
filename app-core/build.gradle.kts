plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    jvmToolchain(findProperty("jvm.toolchain")!!.toString().toInt())
    explicitApi()
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test.junit)

    testRuntimeOnly(libs.slf4j.simple)
}

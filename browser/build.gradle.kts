plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(findProperty("jvm.toolchain")!!.toString().toInt())
    explicitApi()
}

dependencies {
    // JCEF stays behind this module's API: no `org.cef` type leaves it.
    implementation(projects.jcefNative)

    // StateFlow is part of BrowserTab's API.
    api(libs.kotlinx.coroutines)

    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test.junit)
}

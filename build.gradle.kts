plugins {
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
}

allprojects {
    group = "app.kash"
    // macOS does not accept a bundle version whose first number is 0.
    version = "1.0.0"
}

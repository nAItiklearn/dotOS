plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dotos.feature.wallpaper"
    buildFeatures { compose = true }
}

dependencies {
    api(project(":core:designsystem"))
    api(project(":core:data"))
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

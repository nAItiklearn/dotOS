plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.dotos.core.launcher"
}

dependencies {
    api(project(":core:data"))
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

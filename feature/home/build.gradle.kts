plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dotos.feature.home"
    buildFeatures { compose = true }
}

dependencies {
    api(project(":core:designsystem"))
    api(project(":core:data"))
    api(project(":core:launcher"))
    api(project(":feature:icons"))
    api(project(":feature:wallpaper"))
    api(project(":feature:widgets"))
    api(project(":feature:drawer"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

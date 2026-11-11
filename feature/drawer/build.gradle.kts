plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dotos.feature.drawer"
    buildFeatures { compose = true }
}

dependencies {
    api(project(":core:designsystem"))
    api(project(":core:launcher"))
    api(project(":feature:icons"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
}

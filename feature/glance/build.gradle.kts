plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dotos.feature.glance"
    buildFeatures { compose = true }
}

dependencies {
    // Resources only: the clock widget references @font/doto and @font/space_mono from the design system.
    implementation(project(":core:designsystem"))
    implementation(libs.glance.appwidget)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}

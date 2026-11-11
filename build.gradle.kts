import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// Shared Android/Kotlin configuration for every module, so each module file stays tiny.
subprojects {
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<LibraryExtension> {
            compileSdk = 36
            defaultConfig {
                minSdk = 29
                consumerProguardFiles("consumer-rules.pro")
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            testOptions.unitTests.all { it.useJUnitPlatform() }
            lint {
                abortOnError = true
                warningsAsErrors = false
                checkDependencies = false
                disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
            }
        }
    }
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<ApplicationExtension> {
            compileSdk = 36
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            testOptions.unitTests.all { it.useJUnitPlatform() }
        }
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.android") {
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                optIn.addAll(
                    "androidx.compose.foundation.ExperimentalFoundationApi",
                    "androidx.compose.foundation.layout.ExperimentalLayoutApi",
                    "androidx.compose.material3.ExperimentalMaterial3Api",
                    "androidx.compose.ui.ExperimentalComposeUiApi",
                    "androidx.compose.animation.ExperimentalAnimationApi",
                    "androidx.compose.ui.text.ExperimentalTextApi",
                    "kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "kotlinx.coroutines.FlowPreview",
                )
            }
        }
    }
}

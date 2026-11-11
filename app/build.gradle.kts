plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val releaseStoreFile = System.getenv("DOTOS_KEYSTORE")
val releaseStorePassword = System.getenv("DOTOS_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("DOTOS_KEY_ALIAS")
val releaseKeyPassword = System.getenv("DOTOS_KEY_PASSWORD")
val hasReleaseKey = !releaseStoreFile.isNullOrBlank() && file(releaseStoreFile!!).exists()

android {
    namespace = "com.dotos.launcher"

    defaultConfig {
        applicationId = "com.dotos.launcher"
        minSdk = 29
        targetSdk = 36
        // Release workflow passes -PversionCode / -PversionName from the git tag.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.1.0"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = if (releaseStoreFile!!.endsWith(".p12")) "pkcs12" else "jks"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a release key (local builds) fall back to the debug key so the APK still installs.
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "/META-INF/*.version")
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "QueryAllPackagesPermission")
    }

    dependenciesInfo {
        // F-Droid: no Google-encrypted dependency metadata blob.
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:launcher"))
    implementation(project(":feature:home"))
    implementation(project(":feature:drawer"))
    implementation(project(":feature:widgets"))
    implementation(project(":feature:wallpaper"))
    implementation(project(":feature:icons"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:glance"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.profileinstaller)
    debugImplementation(libs.compose.ui.tooling)
}

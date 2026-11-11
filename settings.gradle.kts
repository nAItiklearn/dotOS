pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DotOS"

include(":app")
include(":core:designsystem")
include(":core:data")
include(":core:launcher")
include(":feature:home")
include(":feature:drawer")
include(":feature:widgets")
include(":feature:wallpaper")
include(":feature:icons")
include(":feature:settings")
include(":feature:glance")

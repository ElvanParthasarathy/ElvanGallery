pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Elvan Navil UI, published from Udukkai: kmp> gradlew :elvanNavil:publishToMavenLocal
        mavenLocal()
    }
}

rootProject.name = "ElvanGallery"
include(":app")

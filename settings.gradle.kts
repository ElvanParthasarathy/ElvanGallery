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

// During development, use the standalone design-system source directly when both
// repositories are siblings. Other environments keep using the Maven coordinate.
val elvanNavilUi = file("../ElvanNavilUI")
if (elvanNavilUi.isDirectory) {
    includeBuild(elvanNavilUi) {
        dependencySubstitution {
            substitute(module("com.elvan:elvanNavil")).using(project(":elvanNavil"))
        }
    }
}

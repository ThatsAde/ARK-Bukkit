pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()

        // Paper
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()

        // Paper
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "ARK-Bukkit"

include(
    "core-domain",
    "core-api",
    "core-platform",
    "core-paper-api",
    "module-economy"
)
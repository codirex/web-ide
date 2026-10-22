pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Termux terminal libraries are published on JitPack only.
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroupByRegex("com\\.termux.*")
                includeGroupByRegex("com\\.github\\..*")
            }
        }
    }
}

rootProject.name = "WebIDE"

include(":app")
include(":core")
include(":runtime")
include(":terminal")
include(":editor")
include(":lsp")
include(":workspace")
include(":git")
include(":templates")
include(":preview")

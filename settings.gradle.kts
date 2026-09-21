// Dynamically resolve Android SDK path from system environment without hardcoding usernames or OneDrive paths
val localProps = file("local.properties")
if (!localProps.exists() || !localProps.readText().contains("sdk.dir")) {
    val envSdk = System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: System.getenv("LOCALAPPDATA")?.let { "$it/Android/Sdk" }
        ?: (System.getProperty("user.home") + "/AppData/Local/Android/Sdk")
    val sdkDir = file(envSdk)
    if (sdkDir.exists()) {
        localProps.writeText("sdk.dir=${sdkDir.absolutePath.replace("\\", "/")}\n")
    }
}

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
    }
}

rootProject.name = "JarvisAssistant"
include(":app")

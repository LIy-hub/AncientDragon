pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        val blendLibVersion = providers.gradleProperty("blendlib_version").get()
        ivy {
            name = "blendLibGitHubReleases"
            url = uri("https://github.com/LIy-hub/BlendLib-Public/releases/download/v$blendLibVersion")
            patternLayout {
                artifact("[artifact]-[revision].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("com.liy.blendlib", "blendlib-fabric")
            }
        }
        maven("https://maven.fabricmc.net/")
        mavenCentral()
    }
}

rootProject.name = "ancient-dragon"

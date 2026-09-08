pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        val blendLibReleaseTag = providers.gradleProperty("blendlib_release_tag").get()
        ivy {
            name = "blendLibGitHubReleases"
            url = uri("https://github.com/LIy-hub/BlendLib-Public/releases/download/$blendLibReleaseTag")
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

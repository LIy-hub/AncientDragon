pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        val blendLibRepository = providers.gradleProperty("blendlib_local_maven_repo")
            .orElse("D:/BlendLib/build/local-maven")
            .get()
        maven { url = uri(blendLibRepository) }
        maven("https://maven.fabricmc.net/")
        mavenCentral()
    }
}

rootProject.name = "ancient-dragon"

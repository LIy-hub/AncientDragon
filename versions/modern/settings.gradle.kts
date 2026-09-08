pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { content { includeGroupByRegex("net\\.fabricmc(\\..*)?") } }
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("net.fabricmc.fabric-loom") version "1.15.5"
        id("net.fabricmc.fabric-loom-remap") version "1.15.5"
    }
}
rootProject.name = "ancient-dragon-modern"

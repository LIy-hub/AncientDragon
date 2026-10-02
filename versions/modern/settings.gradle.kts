pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { content { includeGroupByRegex("net\\.fabricmc(\\..*)?") } }
        gradlePluginPortal()
        mavenCentral()
    }
    val loomVersion = if (providers.gradleProperty("minecraft_version").orNull == "26.3") "1.17.21" else "1.15.5"
    plugins {
        id("net.fabricmc.fabric-loom") version loomVersion
        id("net.fabricmc.fabric-loom-remap") version loomVersion
    }
}
rootProject.name = "ancient-dragon-modern"

// 26.3 consumes the public library source, with no dependency on an unpublished Release asset.
if (providers.gradleProperty("minecraft_version").orNull == "26.3") {
    val blendLibSource = file(providers.gradleProperty("blendlib_source").orElse("../../../BlendLib-Public").get())
    check(blendLibSource.resolve("versions/modern/settings.gradle.kts").isFile) {
        "Check out LIy-hub/BlendLib-Public at the revision in blendlib-source.properties beside AncientDragon, or set -Pblendlib_source=/path/to/BlendLib-Public"
    }
    val expected = java.util.Properties().apply { file("blendlib-source.properties").inputStream().use { load(it) } }.getProperty("revision")
    val actual = providers.exec { commandLine("git", "-C", blendLibSource.absolutePath, "rev-parse", "HEAD") }.standardOutput.asText.get().trim()
    check(actual == expected) { "BlendLib source must be at $expected, found $actual" }
    includeBuild(blendLibSource.resolve("versions/modern")) {
        dependencySubstitution {
            substitute(module("com.liy.blendlib:blendlib-fabric")).using(project(":"))
        }
    }
}

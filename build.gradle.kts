import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import groovy.json.JsonSlurper
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("net.fabricmc.fabric-loom") version "1.15.5"
}

group = providers.gradleProperty("maven_group").get()
version = providers.gradleProperty("mod_version").get()

base {
    archivesName.set(providers.gradleProperty("archives_base_name").get())
}

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

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        create("ancient_dragon") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }

    runs {
        named("client") {
            setConfigName("Ancient Dragon Client")
            runDir("run/client")
            programArgs("--username", "DragonTester")
        }
        named("server") {
            setConfigName("Ancient Dragon Server")
            runDir("run/server")
        }
    }
}

val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val loaderVersion = providers.gradleProperty("loader_version").get()
val fabricVersion = providers.gradleProperty("fabric_version").get()
val blendLibVersion = providers.gradleProperty("blendlib_version").get()
val blendLibSha256 = providers.gradleProperty("blendlib_sha256").get().lowercase()

val blendLibDistribution = configurations.create("blendLibDistribution") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val verifyBlendLibDistribution = tasks.register("verifyBlendLibDistribution") {
    group = "verification"
    description = "Verifies the public, complete BlendLib Beta.2 runtime used for compilation and launch."
    inputs.files(blendLibDistribution)
    inputs.property("blendlib_sha256", blendLibSha256)
    doLast {
        val distribution = blendLibDistribution.singleFile
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(distribution.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(digest == blendLibSha256) {
            "BlendLib public release checksum mismatch: expected $blendLibSha256, got $digest"
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")
    implementation("com.liy.blendlib:blendlib-fabric:$blendLibVersion")
    add(blendLibDistribution.name, "com.liy.blendlib:blendlib-fabric:$blendLibVersion")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(verifyBlendLibDistribution)
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("-Xlint:all")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
    from("LICENSE") {
        into("META-INF")
        rename { "LICENSE-ancient-dragon.txt" }
    }
    from("ASSET_LICENSE.md") {
        into("META-INF")
    }
    from("THIRD_PARTY_NOTICES.md") {
        into("META-INF")
    }
}

val dragonDescriptor = layout.projectDirectory.file(
    "src/main/resources/assets/ancient_dragon/blend_models/entity/ancient_dragon.json",
)
val dragonModel = layout.projectDirectory.file(
    "src/main/resources/assets/ancient_dragon/models3d/entity/ancient_dragon.glb",
)
val dragonCollisionRig = layout.projectDirectory.file(
    "src/main/resources/assets/ancient_dragon/collision/ancient_dragon_collision_rig.json",
)
val requiredDragonTextures = listOf("body", "eyes", "teeth", "tongue").map { material ->
    layout.projectDirectory.file(
        "src/main/resources/assets/ancient_dragon/textures/blendlib/entity_ancient_dragon__${material}.png",
    )
}

val verifyDragonAssets = tasks.register("verifyDragonAssets") {
    group = "verification"
    description = "Verifies that the strict BlendLib dragon bundle is present before compilation is accepted."
    inputs.file(dragonDescriptor)
    inputs.file(dragonModel)
    inputs.file(dragonCollisionRig)
    requiredDragonTextures.forEach(inputs::file)

    doLast {
        val requiredFiles = listOf(dragonDescriptor, dragonModel, dragonCollisionRig) + requiredDragonTextures
        val missing = requiredFiles.map { it.asFile }.filterNot { it.isFile && it.length() > 0L }
        check(missing.isEmpty()) {
            "Ancient Dragon resource bundle is incomplete: ${missing.joinToString()}"
        }

        val descriptorText = dragonDescriptor.asFile.readText(StandardCharsets.UTF_8)
        check("\"units_per_block\": 0.0625" in descriptorText) {
            "Dragon descriptor must freeze the intended 16 blocks-per-model-unit scale"
        }
        check("ancient_dragon:combat_idle" in descriptorText
                && "ancient_dragon:fly_cruise" in descriptorText
                && "ancient_dragon:bite_heavy" in descriptorText
                && "ancient_dragon:death_landmark" in descriptorText) {
            "Dragon descriptor is missing a vertical-slice semantic animation key"
        }

        @Suppress("UNCHECKED_CAST")
        val collisionRoot = JsonSlurper().parse(dragonCollisionRig.asFile) as Map<String, Any?>
        check((collisionRoot["format_version"] as? Number)?.toInt() == 2) {
            "Dragon collision rig has an unsupported format version"
        }
        check((collisionRoot["units_to_blocks"] as? Number)?.toDouble() == 16.0) {
            "Dragon collision rig must match the renderer's 16 blocks-per-model-unit scale"
        }
        val collisionParts = collisionRoot["part_order"] as? List<*>
            ?: error("Dragon collision rig is missing part_order")
        check(collisionParts.size == 18 && collisionParts.firstOrNull() == "head"
                && collisionParts.lastOrNull() == "tail_tip") {
            "Dragon collision rig must contain the frozen 18-part bone proxy order"
        }
        val collisionBones = collisionRoot["bone_order"] as? List<*>
            ?: error("Dragon collision rig is missing bone_order")
        val collisionParents = collisionRoot["bone_parents"] as? List<*>
            ?: error("Dragon collision rig is missing bone_parents")
        val collisionPartBones = collisionRoot["part_bones"] as? List<*>
            ?: error("Dragon collision rig is missing part_bones")
        val collisionPartOffsets = collisionRoot["part_offsets"] as? List<*>
            ?: error("Dragon collision rig is missing part_offsets")
        check(collisionBones.size >= 40 && collisionParents.size == collisionBones.size
                && collisionPartBones.size == 18 && collisionPartOffsets.size == 18) {
            "Dragon collision rig v2 must contain parent-first local-TRS skeleton metadata"
        }
        val collisionClips = collisionRoot["clips"] as? Map<*, *>
            ?: error("Dragon collision rig is missing clips")
        val requiredCollisionClips = setOf(
            "DRG_Dormant_Hold",
            "DRG_Awaken_Intro",
            "DRG_Takeoff",
            "DRG_Fly_Cruise_Loop",
            "DRG_Fly_Descend",
            "DRG_Land",
            "DRG_Combat_Idle",
            "DRG_Combat_Stand",
            "DRG_Ground_Stalk",
            "DRG_Bite_Heavy",
            "DRG_Wing_Slam",
            "DRG_Roar_Storm",
            "DRG_Solar_Breath",
            "DRG_Hit_React",
            "DRG_Dive_Strike",
            "DRG_Aerial_Tail_Sweep",
            "DRG_Aerial_Storm_Burst",
            "DRG_Aerial_Solar_Breath",
            "DRG_Death_Landmark",
            "DRG_Corpse_Static",
        )
        check(collisionClips.keys.containsAll(requiredCollisionClips)) {
            "Dragon collision rig is missing a server-authoritative encounter clip"
        }
        check(collisionClips.values.all { clipValue ->
            val clip = clipValue as? Map<*, *> ?: return@all false
            val durationTicks = (clip["duration_ticks"] as? Number)?.toInt() ?: return@all false
            val localTrs = clip["local_trs"] as? List<*> ?: return@all false
            localTrs.size == durationTicks + 1
                    && localTrs.all { frame -> (frame as? List<*>)?.size == collisionBones.size * 10 }
        }) {
            "Dragon collision rig v2 clips must contain one complete local-TRS frame per tick"
        }

        val glbBytes = dragonModel.asFile.readBytes()
        val glb = ByteBuffer.wrap(glbBytes).order(ByteOrder.LITTLE_ENDIAN)
        check(glb.remaining() >= 20 && glb.int == 0x46546C67 && glb.int == 2 && glb.int == glbBytes.size) {
            "Dragon model is not a complete GLB 2.0 file"
        }
        var gltfJson: String? = null
        while (glb.remaining() >= 8) {
            val chunkLength = glb.int
            val chunkType = glb.int
            check(chunkLength >= 0 && chunkLength <= glb.remaining()) {
                "Dragon GLB contains an invalid chunk length"
            }
            val chunk = ByteArray(chunkLength)
            glb.get(chunk)
            if (chunkType == 0x4E4F534A) {
                gltfJson = String(chunk, StandardCharsets.UTF_8)
                    .trimEnd('\u0000', ' ', '\t', '\r', '\n')
            }
        }
        check(gltfJson != null) { "Dragon GLB is missing its JSON chunk" }

        @Suppress("UNCHECKED_CAST")
        val gltf = JsonSlurper().parseText(gltfJson) as Map<String, Any?>
        val accessors = gltf["accessors"] as? List<*>
            ?: error("Dragon GLB is missing accessors")
        val animations = gltf["animations"] as? List<*>
            ?: error("Dragon GLB is missing animations")
        val clipsByName = animations.associateBy { animationValue ->
            val animation = animationValue as? Map<*, *>
                ?: error("Dragon GLB contains an invalid animation")
            animation["name"] as? String
                ?: error("Dragon GLB animation is missing its name")
        }
        val requiredArticulatedRotationChannels = mapOf(
            "DRG_Combat_Idle" to 20,
            "DRG_Bite_Heavy" to 18,
            "DRG_Fly_Cruise_Loop" to 25,
            "DRG_Fly_Loop" to 25,
            "DRG_Death_Landmark" to 35,
        )
        for ((clipName, minimumChannels) in requiredArticulatedRotationChannels) {
            val animation = clipsByName[clipName] as? Map<*, *>
                ?: error("Dragon GLB is missing required clip $clipName")
            val samplers = animation["samplers"] as? List<*>
                ?: error("Dragon GLB clip $clipName is missing samplers")
            val channels = animation["channels"] as? List<*>
                ?: error("Dragon GLB clip $clipName is missing channels")
            val articulatedRotationChannels = channels.count { channelValue ->
                val channel = channelValue as? Map<*, *> ?: return@count false
                val target = channel["target"] as? Map<*, *> ?: return@count false
                if (target["path"] != "rotation") {
                    return@count false
                }
                val samplerIndex = (channel["sampler"] as? Number)?.toInt() ?: return@count false
                val sampler = samplers.getOrNull(samplerIndex) as? Map<*, *> ?: return@count false
                val outputAccessorIndex = (sampler["output"] as? Number)?.toInt() ?: return@count false
                val accessor = accessors.getOrNull(outputAccessorIndex) as? Map<*, *> ?: return@count false
                ((accessor["count"] as? Number)?.toInt() ?: 0) > 2
            }
            check(articulatedRotationChannels >= minimumChannels) {
                "Dragon clip $clipName lost articulated bone motion: " +
                    "$articulatedRotationChannels dynamic rotation channels, expected at least $minimumChannels"
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyDragonAssets)
}

val prepareServerRun = tasks.register("prepareServerRun") {
    group = "fabric"
    description = "Creates only the isolated Ancient Dragon development server safety files."
    val eulaFile = layout.projectDirectory.file("run/server/eula.txt")
    val propertiesFile = layout.projectDirectory.file("run/server/server.properties")
    outputs.files(eulaFile, propertiesFile)

    doLast {
        val eula = eulaFile.asFile
        eula.parentFile.mkdirs()
        if (!eula.exists()) {
            eula.writeText("eula=true\n", StandardCharsets.UTF_8)
        }

        val properties = propertiesFile.asFile
        if (!properties.exists()) {
            properties.writeText(
                "server-ip=127.0.0.1\n" +
                    "server-port=25579\n" +
                    "online-mode=false\n" +
                    "motd=Ancient Dragon isolated development server\n",
                StandardCharsets.UTF_8,
            )
        }
    }
}

tasks.named("runServer") {
    dependsOn(prepareServerRun)
}

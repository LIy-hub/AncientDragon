import java.security.MessageDigest
import java.util.zip.ZipFile
import groovy.json.JsonSlurper

plugins {
    java
    id("net.fabricmc.fabric-loom") apply false
    id("net.fabricmc.fabric-loom-remap") apply false
}

val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val targets = mapOf(
    "1.21.9" to Pair("0.134.1+1.21.9", "565b2d9f10595789cc1f61897375f29ba7c6a312b0a96efe36ee2691f709ee7e"),
    "1.21.10" to Pair("0.138.4+1.21.10", "101aacd800d0a37121f435482da84c7b9e01a8072a0a5a0a0a559de648c3d5ef"),
    "1.21.11" to Pair("0.141.6+1.21.11", "9e4dafa29bb5f6cee2ce60ba45aa8c150b4ba8a36d70b3577320f46a07de49cb"),
    "26.1" to Pair("0.145.1+26.1", "bbd5df63029d521bdb58a8e93ee238521c63c7c518154e5fcd727d9b4f8f45ad"),
    "26.1.1" to Pair("0.145.4+26.1.1", "5f11b14b2d4b83e90a829338cb3b6b84b303314b7945f006ff75089b831beae8"),
    "26.1.2" to Pair("0.154.2+26.1.2", "01a1cabe230da43222fe30baab23d3f31ed7f09868601c7d81cac9b82f5d83de"),
    "26.3" to Pair("0.161.0+26.3", "source-pinned"),
    "26.2" to Pair("0.153.0+26.2", "e9539358a7b6567e7ce0e7e057e46aefb5ba643e58043c9fd04d11ea2c9aeab0"),
)
val (fabricVersion, blendLibSha256) = targets[minecraftVersion] ?: error("Unsupported Minecraft target: $minecraftVersion")
val javaVersion = if (minecraftVersion.startsWith("1.21.")) 21 else 25
val obfuscated = javaVersion == 21
val needsSpears = minecraftVersion in setOf("1.21.9", "1.21.10")
val blendLibVersion = if (minecraftVersion == "26.3") "1.0.0-beta.3+26.3" else "1.0.0-beta.2+$minecraftVersion"
val repository = rootDir.resolve("../..").canonicalFile
apply(plugin = if (obfuscated) "net.fabricmc.fabric-loom-remap" else "net.fabricmc.fabric-loom")
repositories.withType<org.gradle.api.artifacts.repositories.MavenArtifactRepository>().configureEach {
    if (url.host == "maven.fabricmc.net") content { includeGroupByRegex("net\\.fabricmc(\\..*)?") }
}
repositories {
    exclusiveContent {
        forRepository {
            ivy {
                name = "verifiedBlendLibRelease"
                url = uri("https://github.com/LIy-hub/BlendLib-Public/releases/download/v1.0.0-beta.2")
                patternLayout { artifact("[artifact]-[revision].[ext]") }
                metadataSources { artifact() }
            }
        }
        filter { includeModule("com.liy.blendlib", "blendlib-fabric") }
    }
    mavenCentral()
}
group = "com.liy.ancientdragon"
version = "0.1.0-beta.1+$minecraftVersion"
base.archivesName.set("ancient-dragon")
layout.buildDirectory.set(layout.projectDirectory.dir("build/$minecraftVersion"))
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    withSourcesJar()
}
configure<net.fabricmc.loom.api.LoomGradleExtensionAPI> {
    splitEnvironmentSourceSets()
    mods {
        create("ancient_dragon") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
    runs {
        named("client") { runDir("run/$minecraftVersion/client") }
        named("server") { runDir("run/$minecraftVersion/server") }
    }
}
val blendLibDistribution = configurations.create("blendLibDistribution") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
dependencies {
    add("minecraft", "com.mojang:minecraft:$minecraftVersion")
    if (obfuscated) add("mappings", project.extensions.getByType<net.fabricmc.loom.api.LoomGradleExtensionAPI>().officialMojangMappings())
    val implementationName = if (obfuscated) "modImplementation" else "implementation"
    add(implementationName, "net.fabricmc:fabric-loader:${if (minecraftVersion == "26.3") "0.19.5" else providers.gradleProperty("loader_version").get()}")
    add(implementationName, "net.fabricmc.fabric-api:fabric-api:$fabricVersion")
    add(implementationName, "com.liy.blendlib:blendlib-fabric:$blendLibVersion")
    add(blendLibDistribution.name, "com.liy.blendlib:blendlib-fabric:$blendLibVersion")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
val verifyBlendLibDistribution = tasks.register("verifyBlendLibDistribution") {
    inputs.files(blendLibDistribution)
    inputs.property("sha256", blendLibSha256)
    doLast {
        val distribution = blendLibDistribution.singleFile
        val digest = MessageDigest.getInstance("SHA-256").digest(distribution.readBytes())
            .joinToString("") { "%02x".format(it) }
        if (minecraftVersion == "26.3") {
            // This target is built from the exact public source revision pinned in settings.
            ZipFile(distribution).use { jar ->
                val metadata = JsonSlurper().parse(jar.getInputStream(jar.getEntry("fabric.mod.json"))) as Map<*, *>
                check(metadata["id"] == "blendlib" && metadata["version"] == blendLibVersion)
                check((metadata["depends"] as Map<*, *>)["minecraft"] == minecraftVersion)
            }
            logger.lifecycle("SOURCE_BUILT_BLENDLIB_SHA256=$digest")
        } else {
            check(digest == blendLibSha256) { "BlendLib checksum mismatch: $digest" }
        }
    }
}
fun portJava(text: String, name: String): String {
    var result = text
    if (minecraftVersion in setOf("26.1", "26.1.1")) {
        result = result.replace("ServerEntityEvents.ALLOW_LOAD.register(SacredMountainEnvironmentService::allowEntityLoad);", """
            ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
                if (shouldSuppressHostile(entity instanceof Enemy, SacredMountainRegion.contains(level, entity.blockPosition()))) {
                    entity.discard();
                }
            });
        """.trimIndent())
    }
    if (obfuscated) {
        result = result.replace("PayloadTypeRegistry.clientboundPlay()", "PayloadTypeRegistry.playS2C()")
            .replace("ServerTickEvents.END_LEVEL_TICK", "ServerTickEvents.END_WORLD_TICK")
            .replace("api.creativetab.v1.FabricCreativeModeTab", "api.itemgroup.v1.FabricItemGroup")
            .replace("FabricCreativeModeTab.", "FabricItemGroup.")
            .replace("ContainerInput", "ClickType")
            .replace("GuiGraphicsExtractor", "GuiGraphics")
            .replace("extractRenderState(", "render(")
            .replace("extractErrorIcon(", "renderErrorIcon(")
            .replace("extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float deltaTicks)", "renderBg(GuiGraphics graphics, float deltaTicks, int mouseX, int mouseY)")
            .replace("super.extractBackground(graphics, mouseX, mouseY, deltaTicks)", "super.renderBg(graphics, deltaTicks, mouseX, mouseY)")
            .replace("graphics.fakeItem(", "graphics.renderFakeItem(")
            .replace("graphics.entity(", "graphics.submitEntityRenderState(")
            .replace("net.minecraft.world.level.saveddata.WeatherData", "net.minecraft.world.level.storage.ServerLevelData")
            .replace("WeatherData weather = level.getWeatherData()", "ServerLevelData weather = (ServerLevelData) level.getLevelData()")
            .replace("new ServerBossEvent(\n            UUID.randomUUID(),", "new ServerBossEvent(")
            .replace("interact(Player player, InteractionHand hand, Vec3 location)", "interactAt(Player player, Vec3 location, InteractionHand hand)")
            .replace("ChunkPos.containing(", "new ChunkPos(")
            .replace("ChunkPos.pack(", "ChunkPos.asLong(")
            .replace("ChunkPos::x", "chunkPosition -> chunkPosition.x")
            .replace("ChunkPos::z", "chunkPosition -> chunkPosition.z")
            .replace("(level, chunk, newlyGenerated) -> schedule(level, chunk)", "(level, chunk) -> schedule(level, chunk)")
            .replace("ServerEntityEvents.ALLOW_LOAD.register(SacredMountainEnvironmentService::allowEntityLoad);", """
                ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
                    if (shouldSuppressHostile(entity instanceof Enemy, SacredMountainRegion.contains(level, entity.blockPosition()))) {
                        entity.discard();
                    }
                });
            """.trimIndent())
        for (match in Regex("\\bChunkPos\\s+(\\w+)").findAll(text)) {
            val variable = match.groupValues[1]
            result = result.replace("$variable.x()", "$variable.x")
                .replace("$variable.z()", "$variable.z")
                .replace("$variable.pack()", "$variable.toLong()")
        }
        if (text.contains("new SavedDataType<>")) {
            result = result.replace(Regex("(new SavedDataType<>\\(\\s*)Identifier.fromNamespaceAndPath\\(\"ancient_dragon\", \"([^\"]+)\"\\)"), "$1\"$2\"")
        }
        if (result.contains("player.sendSystemMessage(")) {
            result = result.replace("player.sendSystemMessage(", "sendPlayerMessage(player, ")
            result = result.substringBeforeLast("}") + """
                private static void sendPlayerMessage(net.minecraft.world.entity.player.Player player, net.minecraft.network.chat.Component message) {
                    player.displayClientMessage(message, false);
                }
                private static void sendPlayerMessage(net.minecraft.world.entity.player.Player player, net.minecraft.network.chat.Component message, boolean overlay) {
                    player.displayClientMessage(message, overlay);
                }
            }
            """.trimIndent()
        }
        if (name == "CameraMixin.java") {
            result = result.replace("method = \"update\"", "method = \"setup\"")
                .replace("DeltaTracker deltaTracker, CallbackInfo callback", "CallbackInfo callback")
        }
        if (name == "SunheartAltarMenuLogicTest.java") {
            result = result.replace(Regex("private static void bindComponents\\([\\s\\S]*?(?=    @ParameterizedTest)"), """
                private static void bindComponents(Item item, int maxStackSize, int maxDamage) {
                    // These versions bind vanilla item components during Bootstrap already.
                    assertEquals(maxStackSize, item.getDefaultInstance().getMaxStackSize());
                    assertEquals(maxDamage, item.getDefaultInstance().getMaxDamage());
                }

            """.trimIndent() + "\n\n")
        }
        if (name == "SacredMountainAuthoringDataTest.java") {
            result = result.replace("first.getFirst().x()", "first.getFirst().x")
                .replace("first.getFirst().z()", "first.getFirst().z")
        }
        if (minecraftVersion != "1.21.11") {
            result = result.replace(Regex("\\bIdentifier\\b"), "ResourceLocation")
                .replace(".identifier()", ".location()")
                .replace("net.minecraft.advancements.criterion.", "net.minecraft.advancements.critereon.")
                .replace("import net.minecraft.server.permissions.Permissions;", "")
                .replace("source.permissions().hasPermission(Permissions.COMMANDS_ADMIN)", "source.hasPermission(2)")
            if (minecraftVersion == "1.21.9") {
                result = result.replace("InsideBlockEffectApplier effects,\n            boolean intersects", "InsideBlockEffectApplier effects")
            }
        }
    }
    if (needsSpears) {
        if (name == "SunheartAltarScreen.java") {
            result = result.replace("armorStandPreview.leftHandItemStack = ItemStack.EMPTY;", "")
                .replace("armorStandPreview.leftHandItemStack = stack.copy();", "")
                .replace("armorStandPreview.leftHandItemState", "armorStandPreview.leftHandItem")
        }
        if (name == "AncientDragonItems.java") {
            result = result.replace("new Item(dragonboneSpearProperties(properties))", "new com.liy.ancientdragon.compat.LegacySpearItem(dragonboneSpearProperties(properties).attributes(com.liy.ancientdragon.compat.LegacySpearMaterials.withReach(dragonboneSpearAttributes())))")
                .replace(".spear(DRAGONBONE_NETHERITE, 1.15F, 1.2F, 0.4F, 2.5F, 9.0F, 5.5F, 5.1F, 8.75F, 4.6F)", ".durability(DRAGONBONE_NETHERITE.durability()).repairable(DRAGONBONE_NETHERITE.repairItems()).enchantable(DRAGONBONE_NETHERITE.enchantmentValue())")
                .replace("CREATIVE_TAB_ITEMS.forEach(output::accept)", "{ CREATIVE_TAB_ITEMS.forEach(output::accept); com.liy.ancientdragon.compat.LegacySpearMaterials.addToTab(output); }")
                .replace("public static void initialize() {", "public static void initialize() {\n        com.liy.ancientdragon.compat.LegacySpearMaterials.initialize();")
        }
        if (name == "SunheartAltarRite.java") result = result.replace("minecraft(\"netherite_spear\")", "ancientDragon(\"netherite_spear\")")
        if (name == "SunheartAltarRiteTest.java") result = result.replace("\"minecraft:netherite_spear\"", "\"ancient_dragon:netherite_spear\"")
    }
    if (minecraftVersion in setOf("26.2", "26.3")) {
        result = result.replace("net.minecraft.advancements.criterion.ContextAwarePredicate", "net.minecraft.advancements.predicates.ContextAwarePredicate")
            .replace("net.minecraft.advancements.criterion.EntityPredicate", "net.minecraft.advancements.predicates.entity.EntityPredicate")
            .replace("net.minecraft.advancements.criterion.SimpleCriterionTrigger", "net.minecraft.advancements.triggers.SimpleCriterionTrigger")
            .replace("EntityType.LIGHTNING_BOLT", "net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT")
            .replace("EntityType.ARMOR_STAND", "net.minecraft.world.entity.EntityTypes.ARMOR_STAND")
            .replace("Blocks.GRAY_CONCRETE", "Blocks.CONCRETE.gray()")
            .replace("Blocks.LIGHT_GRAY_CONCRETE", "Blocks.CONCRETE.lightGray()")
            .replace("Blocks.CYAN_CONCRETE", "Blocks.CONCRETE.cyan()")
            .replace("Blocks.RED_CONCRETE", "Blocks.CONCRETE.red()")
            .replace("Blocks.YELLOW_CONCRETE", "Blocks.CONCRETE.yellow()")
            .replace("feet.getBottomCenter()", "Vec3.atBottomCenterOf(feet)")
    }
    if (minecraftVersion == "26.3") {
        result = result
            .replace("import net.minecraft.world.item.AxeItem;", "")
            .replace("import net.minecraft.world.item.HoeItem;", "")
            .replace("import net.minecraft.world.item.ShovelItem;", "")
            .replace("new AxeItem(DRAGONBONE_NETHERITE, 10.0F, -3.0F, properties)", "new Item(properties.axe(DRAGONBONE_NETHERITE, 10.0F, -3.0F))")
            .replace("new ShovelItem(DRAGONBONE_NETHERITE, 6.5F, -3.0F, properties)", "new Item(properties.shovel(DRAGONBONE_NETHERITE, 6.5F, -3.0F))")
            .replace("new HoeItem(DRAGONBONE_NETHERITE, 1.0F, 0.0F, properties)", "new Item(properties.hoe(DRAGONBONE_NETHERITE, 1.0F, 0.0F))")
            .replace("player.drop(stack, false)", "player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY)")
            .replace("player.drop(book, false)", "player.drop(book, false, net.minecraft.util.Prediction.SERVER_ONLY)")
            .replace("player.drop(heart, false)", "player.drop(heart, false, net.minecraft.util.Prediction.SERVER_ONLY)")
            .replace("player.hurtMarked = true", "player.syncVelocity = true")
            .replace(".startsForStructure(chunk, candidate -> candidate == structure)", ".startsForStructure(chunk.x(), chunk.z(), candidate -> candidate == structure)")
            .replace(".startsForStructure(loadedChunk, candidate -> candidate == structure)", ".startsForStructure(loadedChunk.x(), loadedChunk.z(), candidate -> candidate == structure)")
            .replace("import net.minecraft.advancements.predicates.ContextAwarePredicate;", "import net.minecraft.core.Holder;\nimport net.minecraft.world.level.storage.loot.predicates.LootItemCondition;")
            .replace("Optional<ContextAwarePredicate>", "Optional<Holder<LootItemCondition>>")
            .replace("EntityPredicate.ADVANCEMENT_CODEC", "LootItemCondition.CODEC")
            .replace("import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;", "")
            .replace("StructurePlacementType<SacredMountainPlacement>", "com.mojang.serialization.MapCodec<SacredMountainPlacement>")
            .replace("() -> SacredMountainPlacement.CODEC", "SacredMountainPlacement.CODEC")
            .replace("import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;", "import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;")
            .replace("extends StructurePlacement {", "extends AbstractSpreadingStructurePlacement {")
            .replace("public StructurePlacementType<?> type()", "public MapCodec<SacredMountainPlacement> codec()")
            .replace("int quartY = QuartPos.fromBlock(seaLevel);", "int quartY = QuartPos.fromBlock(seaLevel);\n        var biomeResolver = biomeSource.createUncachedResolver(randomState);")
            .replace("biomeSource.getNoiseBiome(", "biomeResolver.getNoiseBiome(")
            .replace(",\n                    randomState.sampler()", "")
            .replace(",\n                        randomState.sampler()", "")
        if (name in setOf("EternalSoulFireBlock.java", "AncientCityGatewayBlock.java")) {
            result = result.replace(Regex("    @Override\\s+public MapCodec<[^>]+> codec\\(\\) \\{[^}]*}\\s*"), "")
                .replace(Regex("    public static final MapCodec<AncientCityGatewayBlock> CODEC = [^;]+;\\n"), "")
                .replace("import com.mojang.serialization.MapCodec;", "")
        }
    }
    return result
}
val preparePortSources = tasks.register("preparePortSources") {
    inputs.dir(repository.resolve("src"))
    inputs.files(fileTree("overrides"))
    inputs.property("minecraftVersion", minecraftVersion)
    val output = layout.buildDirectory.dir("generated/sources")
    outputs.dir(output)
    doLast {
        val outputDir = output.get().asFile
        check(outputDir.canonicalFile.toPath().startsWith(layout.buildDirectory.get().asFile.canonicalFile.toPath()))
        delete(outputDir)
        for (sourceSet in listOf("main", "client", "test")) {
            val input = repository.resolve("src/$sourceSet/java")
            input.walkTopDown().filter { it.isFile && it.extension == "java" }.forEach { original ->
                val target = outputDir.resolve("$sourceSet/${original.relativeTo(input)}")
                target.parentFile.mkdirs()
                target.writeText(portJava(original.readText().replace("\r\n", "\n"), original.name))
            }
            for (overrideGroup in listOfNotNull("common", if (obfuscated) "obfuscated" else "unobfuscated", if (needsSpears) "spears" else null, minecraftVersion)) {
                val overrideRoot = file("overrides/$overrideGroup/$sourceSet")
                overrideRoot.walkTopDown().filter { it.isFile && it.extension == "java" }.forEach { original ->
                    val target = outputDir.resolve("$sourceSet/${original.relativeTo(overrideRoot)}")
                    target.parentFile.mkdirs()
                    target.writeText(portJava(original.readText(), original.name))
                }
            }
        }
    }
}
for (sourceSet in listOf("main", "client", "test")) {
    sourceSets[sourceSet].java.setSrcDirs(listOf(layout.buildDirectory.dir("generated/sources/$sourceSet")))
    sourceSets[sourceSet].resources.setSrcDirs(listOf(repository.resolve("src/$sourceSet/resources")))
}
val preparePortResources = tasks.register("preparePortResources") {
    inputs.dir(repository.resolve("src/main/resources"))
    inputs.files(fileTree("resources"))
    inputs.property("minecraftVersion", minecraftVersion)
    val output = layout.buildDirectory.dir("generated/resources")
    outputs.dir(output)
    doLast {
        val outputDir = output.get().asFile
        check(outputDir.canonicalFile.toPath().startsWith(layout.buildDirectory.get().asFile.canonicalFile.toPath()))
        delete(outputDir)
        for (input in listOfNotNull(repository.resolve("src/main/resources"), file("resources/common"), file("resources/${if (obfuscated) "obfuscated" else "unobfuscated"}"), if (needsSpears) file("resources/spears") else null, file("resources/$minecraftVersion"))) {
            input.walkTopDown().filter { it.isFile }.forEach { original ->
                val relative = original.relativeTo(input).invariantSeparatorsPath
                val target = outputDir.resolve(relative)
                target.parentFile.mkdirs()
                if (obfuscated && relative.startsWith("assets/ancient_dragon/equipment/") && original.extension == "json") {
                    @Suppress("UNCHECKED_CAST")
                    val json = JsonSlurper().parse(original) as MutableMap<String, Any?>
                    @Suppress("UNCHECKED_CAST")
                    val layers = json["layers"] as? MutableMap<String, Any?>
                    // Older vanilla uses the humanoid layer for these armor renderers.
                    layers?.remove("humanoid_baby")
                    target.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)) + "\n")
                } else if (needsSpears && relative == "fabric.mod.json") {
                    @Suppress("UNCHECKED_CAST")
                    val json = JsonSlurper().parse(original) as MutableMap<String, Any?>
                    @Suppress("UNCHECKED_CAST")
                    val mixins = json["mixins"] as MutableList<Any?>
                    mixins.add("ancient_dragon.legacy.mixins.json")
                    target.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)) + "\n")
                } else if (needsSpears && relative in setOf("assets/ancient_dragon/lang/en_us.json", "assets/ancient_dragon/lang/zh_cn.json")) {
                    @Suppress("UNCHECKED_CAST")
                    val json = JsonSlurper().parse(original) as MutableMap<String, Any?>
                    val chinese = relative.endsWith("zh_cn.json")
                    json["item.ancient_dragon.diamond_spear"] = if (chinese) "钻石长矛" else "Diamond Spear"
                    json["item.ancient_dragon.netherite_spear"] = if (chinese) "下界合金长矛" else "Netherite Spear"
                    target.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)) + "\n")
                } else {
                    original.copyTo(target, overwrite = true)
                }
            }
        }
    }
}
sourceSets["main"].resources.setSrcDirs(listOf(layout.buildDirectory.dir("generated/resources")))
tasks.test { useJUnitPlatform(); workingDir = repository }
tasks.withType<JavaCompile>().configureEach {
    dependsOn(preparePortSources, verifyBlendLibDistribution)
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}
tasks.withType<ProcessResources>().configureEach {
    dependsOn(preparePortResources)
    inputs.property("portVersion", project.version)
    inputs.property("minecraftVersion", minecraftVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
        filter { line -> line.replace("\"26.1.2\"", "\"$minecraftVersion\"")
            .replace("\"java\": \">=25\"", "\"java\": \">=$javaVersion\"")
            .let { if (minecraftVersion == "26.3") it.replace(">=0.19.3", ">=0.19.5").replace("\"fabric-api\": \"*\"", "\"fabric-api\": \">=$fabricVersion\"").replace(">=1.0.0-beta.2 <1.1.0", ">=1.0.0-beta.3 <1.1.0") else it } }
    }
    filesMatching("ancient_dragon.mixins.json") {
        filter { line -> line.replace("JAVA_25", "JAVA_$javaVersion") }
    }
}
tasks.withType<Jar>().configureEach {
    from(repository.resolve("LICENSE")) { into("META-INF"); rename { "LICENSE-ancient-dragon.txt" } }
    from(repository.resolve("ASSET_LICENSE.md")) { into("META-INF") }
    from(repository.resolve("THIRD_PARTY_NOTICES.md")) { into("META-INF") }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest.attributes("AncientDragon-Minecraft-Target" to minecraftVersion, "Implementation-Version" to project.version)
}
tasks.named("sourcesJar") { dependsOn(preparePortSources, preparePortResources) }
val runtimeTask = tasks.named<org.gradle.api.tasks.bundling.AbstractArchiveTask>(if (obfuscated) "remapJar" else "jar")
val verifyRuntimeJar = tasks.register("verifyRuntimeJar") {
    dependsOn(runtimeTask, verifyBlendLibDistribution)
    inputs.file(runtimeTask.flatMap { it.archiveFile })
    doLast {
        ZipFile(runtimeTask.get().archiveFile.get().asFile).use { jar ->
            val metadata = JsonSlurper().parse(jar.getInputStream(jar.getEntry("fabric.mod.json"))) as Map<*, *>
            check(metadata["id"] == "ancient_dragon" && metadata["version"] == project.version.toString())
            val depends = metadata["depends"] as Map<*, *>
            check(depends["minecraft"] == minecraftVersion && depends["java"] == ">=$javaVersion")
            check(depends["blendlib"] == if (minecraftVersion == "26.3") ">=1.0.0-beta.3 <1.1.0" else ">=1.0.0-beta.2 <1.1.0")
            val entries = jar.entries().asSequence().map { it.name }.toSet()
            val sourceClasses = repository.resolve("src/main/java").walkTopDown().filter { it.isFile && it.extension == "java" }.toList() +
                repository.resolve("src/client/java").walkTopDown().filter { it.isFile && it.extension == "java" }.toList()
            for (source in sourceClasses) {
                val sourceRoot = repository.resolve(if (source.toPath().startsWith(repository.resolve("src/client").toPath())) "src/client/java" else "src/main/java")
                val className = source.relativeTo(sourceRoot).invariantSeparatorsPath.removeSuffix(".java") + ".class"
                check(className in entries) { "Missing gameplay class $className" }
            }
            for (path in listOf("assets/ancient_dragon/models3d/entity/ancient_dragon.glb", "assets/ancient_dragon/collision/ancient_dragon_collision_rig.json", "assets/ancient_dragon/blend_models/entity/ancient_dragon.json", "META-INF/ASSET_LICENSE.md", "META-INF/THIRD_PARTY_NOTICES.md")) {
                check(path in entries) { "Missing runtime asset $path" }
            }
            check(entries.none { it.endsWith(".log") || it.endsWith(".blend") || it.startsWith("run/") || it.startsWith("art/") })
        }
    }
}
tasks.named("build") { dependsOn(verifyRuntimeJar) }

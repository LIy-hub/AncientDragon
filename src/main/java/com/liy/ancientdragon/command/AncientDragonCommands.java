package com.liy.ancientdragon.command;

import com.liy.ancientdragon.animation.AncientDragonAnimations;
import com.liy.ancientdragon.animation.pose.DragonPoseState.Maneuver;
import com.liy.ancientdragon.boss.AncientDragonEncounterRules.Attack;
import com.liy.ancientdragon.boss.AncientDragonEncounterRules.Phase;
import com.liy.ancientdragon.chronicle.DragonChronicleService;
import com.liy.ancientdragon.entity.AncientDragonEntities;
import com.liy.ancientdragon.entity.AncientDragonEntity;
import com.liy.ancientdragon.entity.AncientDragonPartKind;
import com.liy.ancientdragon.worldgen.SacredMountainBuildService;
import com.liy.ancientdragon.worldgen.AncientDragonWorldgen;
import com.liy.ancientdragon.worldgen.SacredMountainPlacement;
import com.liy.ancientdragon.worldgen.SacredMountainPlacementService;
import com.liy.ancientdragon.worldgen.SacredMountainNaturalRepairService;
import com.liy.ancientdragon.worldgen.SacredMountainShape;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import java.util.Optional;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Player-owned chronicle recovery plus administrator-only controls for the playable visual slice. */
public final class AncientDragonCommands {
    private static final double TARGET_RADIUS_SQUARED = 1_024.0D * 1_024.0D;
    private static final double DUPLICATE_SPAWN_RADIUS = 192.0D;

    private AncientDragonCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, commandBuildContext, selection) ->
                register(dispatcher));
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ancientdragon")
                .then(Commands.literal("chronicle").executes(context -> chronicle(context.getSource())))
                .then(Commands.literal("spawn")
                        .requires(AncientDragonCommands::isAdministrator)
                        .executes(context -> spawn(context.getSource())))
                .then(Commands.literal("awaken")
                        .requires(AncientDragonCommands::isAdministrator)
                        .executes(context -> awaken(context.getSource())))
                .then(Commands.literal("animation").requires(AncientDragonCommands::isAdministrator)
                        .then(Commands.literal("idle").executes(context -> persistent(
                                context.getSource(), AncientDragonAnimations.COMBAT_IDLE, "combat_idle")))
                        .then(Commands.literal("cruise").executes(context -> persistent(
                                context.getSource(), AncientDragonAnimations.FLY_CRUISE, "fly_cruise")))
                        .then(Commands.literal("bite").executes(context -> transientAnimation(
                                context.getSource(), AncientDragonAnimations.BITE_HEAVY, "bite_heavy")))
                        .then(Commands.literal("death").executes(context -> persistent(
                                context.getSource(), AncientDragonAnimations.DEATH_LANDMARK, "death_landmark"))))
                .then(Commands.literal("maneuver").requires(AncientDragonCommands::isAdministrator)
                        .then(Commands.literal("roll_left").executes(context -> maneuver(
                                context.getSource(), Maneuver.ROLL_LEFT)))
                        .then(Commands.literal("roll_right").executes(context -> maneuver(
                                context.getSource(), Maneuver.ROLL_RIGHT)))
                        .then(Commands.literal("loop").executes(context -> maneuver(
                                context.getSource(), Maneuver.LOOP)))
                        .then(Commands.literal("evade_left").executes(context -> maneuver(
                                context.getSource(), Maneuver.EVADE_LEFT)))
                        .then(Commands.literal("evade_right").executes(context -> maneuver(
                                context.getSource(), Maneuver.EVADE_RIGHT)))
                        .then(Commands.literal("pull_up").executes(context -> maneuver(
                                context.getSource(), Maneuver.PULL_UP)))
                        .then(Commands.literal("cancel").executes(context -> cancelManeuver(context.getSource()))))
                .then(Commands.literal("attention").requires(AncientDragonCommands::isAdministrator)
                        .then(Commands.literal("nearest").executes(context -> attention(
                                context.getSource(), "nearest")))
                        .then(Commands.literal("scan").executes(context -> attention(
                                context.getSource(), "scan")))
                        .then(Commands.literal("clear").executes(context -> attention(
                                context.getSource(), "clear"))))
                .then(Commands.literal("combat").requires(AncientDragonCommands::isAdministrator)
                        .then(Commands.literal("status").executes(context -> combatStatus(context.getSource())))
                        .then(Commands.literal("start").executes(context -> combatStart(context.getSource())))
                        .then(Commands.literal("phase")
                                .then(Commands.literal("mountain").executes(context -> combatPhase(
                                        context.getSource(), Phase.MOUNTAIN)))
                                .then(Commands.literal("storm").executes(context -> combatPhase(
                                        context.getSource(), Phase.STORM)))
                                .then(Commands.literal("solar").executes(context -> combatPhase(
                                        context.getSource(), Phase.SOLAR))))
                        .then(Commands.literal("attack")
                                .then(Commands.literal("dive").executes(context -> combatAttack(
                                        context.getSource(), Attack.DIVE_STRIKE)))
                                .then(Commands.literal("tail").executes(context -> combatAttack(
                                        context.getSource(), Attack.TAIL_SWEEP)))
                                .then(Commands.literal("storm").executes(context -> combatAttack(
                                        context.getSource(), Attack.STORM_BURST)))
                                .then(Commands.literal("solar").executes(context -> combatAttack(
                                        context.getSource(), Attack.SOLAR_BREATH)))
                                .then(Commands.literal("bite").executes(context -> combatAttack(
                                        context.getSource(), Attack.BITE_HEAVY)))
                                .then(Commands.literal("wing").executes(context -> combatAttack(
                                        context.getSource(), Attack.WING_SLAM))))
                        .then(Commands.literal("stability")
                                .then(Commands.literal("get").executes(context -> combatStabilityGet(
                                        context.getSource())))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 310))
                                                .executes(context -> combatStabilitySet(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "value")))))
                                .then(Commands.literal("break").executes(context -> combatStabilitySet(
                                        context.getSource(), 0))))
                        .then(Commands.literal("participants").executes(context -> combatParticipants(
                                context.getSource())))
                        .then(Commands.literal("hitboxes")
                                .then(Commands.literal("on").executes(context -> combatToggle(
                                        context.getSource(), "hitboxes", true)))
                                .then(Commands.literal("off").executes(context -> combatToggle(
                                        context.getSource(), "hitboxes", false))))
                        .then(Commands.literal("route")
                                .then(Commands.literal("on").executes(context -> combatToggle(
                                        context.getSource(), "route", true)))
                                .then(Commands.literal("off").executes(context -> combatToggle(
                                        context.getSource(), "route", false))))
                        .then(Commands.literal("director")
                                .then(Commands.literal("on").executes(context -> combatToggle(
                                        context.getSource(), "director", true)))
                                .then(Commands.literal("off").executes(context -> combatToggle(
                                        context.getSource(), "director", false))))
                        .then(Commands.literal("stop")
                                .then(Commands.literal("confirm").executes(context -> combatStop(
                                        context.getSource())))))
                .then(Commands.literal("mountain").requires(AncientDragonCommands::isAdministrator)
                        .then(Commands.literal("preview")
                                .executes(context -> startMountain(
                                        context.getSource(), SacredMountainShape.DEFAULT_SEED, true))
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(context -> startMountain(
                                                context.getSource(), LongArgumentType.getLong(context, "seed"), true)))
                                .then(Commands.literal("clear")
                                        .executes(context -> mountainAction(
                                                context.getSource(),
                                                SacredMountainBuildService.clearPreview(
                                                        context.getSource().getLevel())))))
                        .then(Commands.literal("build")
                                .executes(context -> startMountain(
                                        context.getSource(), SacredMountainShape.DEFAULT_SEED, false))
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(context -> startMountain(
                                                context.getSource(), LongArgumentType.getLong(context, "seed"), false))))
                        .then(Commands.literal("status").executes(context -> mountainStatus(context.getSource())))
                        .then(Commands.literal("pause").executes(context -> mountainAction(
                                context.getSource(),
                                SacredMountainBuildService.pause(context.getSource().getLevel()))))
                        .then(Commands.literal("resume").executes(context -> mountainAction(
                                context.getSource(),
                                SacredMountainBuildService.resume(context.getSource().getLevel()))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("build_id", StringArgumentType.word())
                                        .then(Commands.literal("confirm")
                                                .executes(context -> mountainAction(
                                                        context.getSource(),
                                                        SacredMountainBuildService.reset(
                                                                context.getSource().getLevel(),
                                                                StringArgumentType.getString(
                                                                        context, "build_id")))))))
                        .then(Commands.literal("finalize").executes(context -> mountainAction(
                                context.getSource(),
                                SacredMountainBuildService.finalizeDraft(context.getSource().getLevel())))))
                .then(Commands.literal("structure").requires(AncientDragonCommands::isAdministrator)
                        .then(Commands.literal("locate")
                                .executes(context -> locateNaturalStructure(context.getSource())))
                        .then(Commands.literal("preflight")
                                .executes(context -> startStructurePreflight(context.getSource(), 0))
                                .then(Commands.argument("quarter_turns", IntegerArgumentType.integer(0, 3))
                                        .executes(context -> startStructurePreflight(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "quarter_turns")))))
                        .then(Commands.literal("status")
                                .executes(context -> structureStatus(context.getSource())))
                        .then(Commands.literal("repair-natural")
                                .executes(context -> structureAction(
                                        context.getSource(),
                                        SacredMountainNaturalRepairService.start(
                                                context.getSource().getLevel())))
                                .then(Commands.literal("status")
                                        .executes(context -> naturalRepairStatus(context.getSource()))))
                        .then(Commands.literal("place")
                                .then(Commands.argument("placement_id", StringArgumentType.word())
                                        .then(Commands.literal("confirm")
                                                .executes(context -> structureAction(
                                                        context.getSource(),
                                                        SacredMountainPlacementService.confirmPlacement(
                                                                context.getSource().getLevel(),
                                                                StringArgumentType.getString(
                                                                        context, "placement_id")))))))
                        .then(Commands.literal("pause").executes(context -> structureAction(
                                context.getSource(),
                                SacredMountainPlacementService.pause(context.getSource().getLevel()))))
                        .then(Commands.literal("resume").executes(context -> structureAction(
                                context.getSource(),
                                SacredMountainPlacementService.resume(context.getSource().getLevel()))))
                        .then(Commands.literal("discard")
                                .then(Commands.argument("placement_id", StringArgumentType.word())
                                        .then(Commands.literal("confirm")
                                                .executes(context -> structureAction(
                                                        context.getSource(),
                                                        SacredMountainPlacementService.discard(
                                                                context.getSource().getLevel(),
                                                                StringArgumentType.getString(
                                                                        context, "placement_id"))))))))
                .then(Commands.literal("clear").requires(AncientDragonCommands::isAdministrator)
                        .executes(context -> clear(context.getSource())))
                .then(Commands.literal("status").requires(AncientDragonCommands::isAdministrator)
                        .executes(context -> status(context.getSource()))));
    }

    private static boolean isAdministrator(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
    }

    private static int chronicle(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.ancient_dragon.chronicle.player_only"));
            return 0;
        }
        int reissued = DragonChronicleService.reissueUnlocked(player);
        if (reissued == 0) {
            source.sendFailure(Component.translatable("command.ancient_dragon.chronicle.none"));
            return 0;
        }
        source.sendSuccess(
                () -> Component.translatable("command.ancient_dragon.chronicle.reissued", reissued), false);
        return reissued;
    }

    private static int startStructurePreflight(CommandSourceStack source, int quarterTurns) {
        SacredMountainPlacementService.ActionResult result = SacredMountainPlacementService.startPreflight(
                source.getLevel(), BlockPos.containing(source.getPosition()), quarterTurns);
        return structureAction(source, result);
    }

    private static int locateNaturalStructure(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var holder = registry.get(AncientDragonWorldgen.SACRED_MOUNTAIN_KEY).orElse(null);
        if (holder == null) {
            source.sendFailure(Component.literal("Natural Sacred Mountain is not registered in this world"));
            return 0;
        }
        BlockPos origin = BlockPos.containing(source.getPosition());
        var generatorState = level.getChunkSource().getGeneratorState();
        var placements = generatorState.getPlacementsForStructure(holder);
        if (placements.stream().anyMatch(SacredMountainPlacement.class::isInstance)) {
            var selected = SacredMountainPlacement.findSelectedChunk(
                    generatorState.getLevelSeed(),
                    level.getChunkSource().getGenerator().getBiomeSource(),
                    generatorState.randomState(),
                    level.getSeaLevel(),
                    holder.value().biomes()::contains);
            if (selected.isEmpty()) {
                source.sendFailure(Component.literal("No deep-ocean Sacred Mountain candidate exists in this world"));
                return 0;
            }
            ChunkPos position = selected.orElseThrow();
            return reportNaturalStructure(source, origin,
                    new BlockPos(position.getMinBlockX(), level.getSeaLevel(), position.getMinBlockZ()),
                    "open deep-ocean candidate; 256-block land clearance");
        }
        var located = level.getChunkSource().getGenerator().findNearestMapStructure(
                level, HolderSet.direct(holder), origin, 1_024, false);
        if (located == null) {
            source.sendFailure(Component.literal("No natural Sacred Mountain candidate exists in this world"
                    + " (active placements=" + placements.size() + ")"));
            return 0;
        }
        return reportNaturalStructure(source, origin, located.getFirst(), "generated placement");
    }

    private static int reportNaturalStructure(
            CommandSourceStack source, BlockPos origin, BlockPos position, String kind) {
        int horizontalDistance = (int) Math.round(Math.sqrt(
                origin.distSqr(new BlockPos(position.getX(), origin.getY(), position.getZ()))));
        source.sendSuccess(() -> Component.literal("Natural Sacred Mountain: "
                + position.getX() + " " + position.getY() + " " + position.getZ()
                + " (" + horizontalDistance + " blocks away; " + kind + ")"), false);
        return 1;
    }

    private static int structureStatus(CommandSourceStack source) {
        String status = SacredMountainPlacementService.status(source.getLevel());
        source.sendSuccess(() -> Component.literal(status), false);
        return 1;
    }

    private static int naturalRepairStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                SacredMountainNaturalRepairService.status(source.getLevel())), false);
        return 1;
    }

    private static int structureAction(
            CommandSourceStack source, SacredMountainPlacementService.ActionResult result) {
        if (!result.succeeded()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static int startMountain(CommandSourceStack source, long seed, boolean preview) {
        BlockPos position = BlockPos.containing(source.getPosition());
        SacredMountainBuildService.StartResult result = preview
                ? SacredMountainBuildService.startPreview(source.getLevel(), position, seed)
                : SacredMountainBuildService.startBuild(source.getLevel(), position, seed);
        if (!result.started()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static int mountainStatus(CommandSourceStack source) {
        String status = SacredMountainBuildService.status(source.getLevel());
        source.sendSuccess(() -> Component.literal(status), false);
        return 1;
    }

    private static int mountainAction(
            CommandSourceStack source, SacredMountainBuildService.ActionResult result) {
        if (!result.succeeded()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static int spawn(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition().add(0.0D, 4.0D, 0.0D);
        AABB duplicateCheck = AABB.ofSize(
                position,
                DUPLICATE_SPAWN_RADIUS * 2.0D,
                DUPLICATE_SPAWN_RADIUS * 2.0D,
                DUPLICATE_SPAWN_RADIUS * 2.0D);
        Optional<AncientDragonEntity> existing = level.getEntitiesOfClass(
                        AncientDragonEntity.class, duplicateCheck, Entity::isAlive)
                .stream()
                .min((left, right) -> Double.compare(
                        left.distanceToSqr(position), right.distanceToSqr(position)));
        if (existing.isPresent()) {
            AncientDragonEntity dragon = existing.get();
            source.sendFailure(Component.literal("Ancient Dragon " + dragon.getUUID()
                    + " already exists within " + (int) DUPLICATE_SPAWN_RADIUS
                    + " blocks at " + formatPosition(dragon.position())));
            return 0;
        }

        AncientDragonEntity dragon = new AncientDragonEntity(AncientDragonEntities.ANCIENT_DRAGON, level);
        dragon.setPos(position.x(), position.y(), position.z());
        dragon.setYRot(source.getRotation().y);
        dragon.setNoGravity(true);
        dragon.setDeltaMovement(Vec3.ZERO);
        dragon.setEncounterOrigin(position);
        dragon.setEncounterRotation(0);
        if (!level.addFreshEntity(dragon)) {
            source.sendFailure(Component.literal("Ancient Dragon entity was rejected by the level"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Spawned Ancient Dragon " + dragon.getUUID()
                + " dormant at " + formatPosition(position)), true);
        return 1;
    }

    private static int awaken(CommandSourceStack source) {
        Optional<AncientDragonEntity> dragon = nearest(source);
        if (dragon.isEmpty()) {
            return noDragon(source);
        }
        if (!dragon.get().awaken()) {
            source.sendFailure(Component.literal("Nearest Ancient Dragon is no longer dormant"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("The sacred mountain has been disturbed"), true);
        return 1;
    }

    private static int combatStatus(CommandSourceStack source) {
        Optional<AncientDragonEntity> dragon = nearest(source);
        if (dragon.isEmpty()) {
            return noDragon(source);
        }
        source.sendSuccess(() -> Component.literal(dragon.get().combatDebugStatus()), false);
        return 1;
    }

    private static int combatStart(CommandSourceStack source) {
        Optional<AncientDragonEntity> dragon = nearest(source);
        if (dragon.isEmpty()) {
            return noDragon(source);
        }
        if (!dragon.get().debugAwaken()) {
            source.sendFailure(Component.literal("Combat debug start requires a dormant Ancient Dragon"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Ancient Dragon debug encounter started; formal rewards disabled"), true);
        return 1;
    }

    private static int combatPhase(CommandSourceStack source, Phase phase) {
        Optional<AncientDragonEntity> dragon = nearest(source);
        if (dragon.isEmpty()) {
            return noDragon(source);
        }
        if (!dragon.get().debugForcePhase(phase)) {
            source.sendFailure(Component.literal("Phase override requires an active debug encounter"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Ancient Dragon debug phase: " + phase.serializedName()), true);
        return 1;
    }

    private static int combatAttack(CommandSourceStack source, Attack attack) {
        Optional<AncientDragonEntity> dragon = nearest(source);
        if (dragon.isEmpty()) {
            return noDragon(source);
        }
        if (!dragon.get().debugForceAttack(attack)) {
            source.sendFailure(Component.literal(
                    "Attack override requires a matching air/ground debug state and an active target"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Ancient Dragon debug attack: " + attack.name().toLowerCase(java.util.Locale.ROOT)), true);
        return 1;
    }

    private static int combatStabilityGet(CommandSourceStack source) {
        Optional<AncientDragonEntity> dragon = nearest(source);
        if (dragon.isEmpty()) {
            return noDragon(source);
        }
        source.sendSuccess(() -> Component.literal(String.format(
                java.util.Locale.ROOT,
                "Ancient Dragon stability %.1f/%.1f",
                dragon.get().stabilityCurrent(),
                dragon.get().stabilityMaximum())), false);
        return 1;
    }

    private static int combatStabilitySet(CommandSourceStack source, int value) {
        Optional<AncientDragonEntity> dragon = nearest(source);
        if (dragon.isEmpty()) {
            return noDragon(source);
        }
        if (!dragon.get().debugSetStability(value)) {
            source.sendFailure(Component.literal("Stability value exceeds the current scaled maximum"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Ancient Dragon stability set to " + value), true);
        return 1;
    }

    private static int combatParticipants(CommandSourceStack source) {
        Optional<AncientDragonEntity> dragon = nearest(source);
        if (dragon.isEmpty()) {
            return noDragon(source);
        }
        source.sendSuccess(() -> Component.literal(dragon.get().participantDebugStatus()), false);
        return 1;
    }

    private static int combatToggle(CommandSourceStack source, String kind, boolean enabled) {
        Optional<AncientDragonEntity> dragon = nearest(source);
        if (dragon.isEmpty()) {
            return noDragon(source);
        }
        switch (kind) {
            case "hitboxes" -> dragon.get().debugHitboxes(enabled);
            case "route" -> dragon.get().debugRoute(enabled);
            case "director" -> dragon.get().debugDirector(enabled);
            default -> throw new IllegalArgumentException("Unknown combat debug toggle " + kind);
        }
        source.sendSuccess(() -> Component.literal("Ancient Dragon " + kind + "=" + enabled), false);
        return 1;
    }

    private static int combatStop(CommandSourceStack source) {
        Optional<AncientDragonEntity> dragon = nearest(source);
        if (dragon.isEmpty()) {
            return noDragon(source);
        }
        if (!dragon.get().debugStopEncounter()) {
            source.sendFailure(Component.literal("Only a live debug encounter can be stopped automatically"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Ancient Dragon debug encounter reset to dormant"), true);
        return 1;
    }

    private static int persistent(
            CommandSourceStack source,
            com.liy.blendlib.api.BlendAnimationKey animation,
            String displayName) {
        Optional<AncientDragonEntity> dragon = nearest(source);
        if (dragon.isEmpty()) {
            return noDragon(source);
        }
        dragon.get().setPersistentAnimation(animation);
        source.sendSuccess(() -> Component.literal("Ancient Dragon persistent animation: " + displayName), true);
        return 1;
    }

    private static int transientAnimation(
            CommandSourceStack source,
            com.liy.blendlib.api.BlendAnimationKey animation,
            String displayName) {
        Optional<AncientDragonEntity> dragon = nearest(source);
        if (dragon.isEmpty()) {
            return noDragon(source);
        }
        dragon.get().triggerAnimation(animation);
        source.sendSuccess(() -> Component.literal("Ancient Dragon triggered animation: " + displayName), true);
        return 1;
    }

    private static int maneuver(CommandSourceStack source, Maneuver maneuver) {
        Optional<AncientDragonEntity> dragon = nearest(source);
        if (dragon.isEmpty()) {
            return noDragon(source);
        }
        if (!dragon.get().requestManeuver(maneuver)) {
            source.sendFailure(Component.literal(
                    "Maneuver rejected: requires an eligible airborne state, cooldown and sufficient clearance"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Ancient Dragon maneuver: " + maneuver.name().toLowerCase()), true);
        return 1;
    }

    private static int cancelManeuver(CommandSourceStack source) {
        Optional<AncientDragonEntity> dragon = nearest(source);
        if (dragon.isEmpty()) {
            return noDragon(source);
        }
        dragon.get().cancelManeuver();
        source.sendSuccess(() -> Component.literal("Ancient Dragon maneuver cancelled"), true);
        return 1;
    }

    private static int attention(CommandSourceStack source, String mode) {
        Optional<AncientDragonEntity> dragon = nearest(source);
        if (dragon.isEmpty()) {
            return noDragon(source);
        }
        switch (mode) {
            case "nearest" -> dragon.get().attentionNearest();
            case "scan" -> dragon.get().attentionScan();
            case "clear" -> dragon.get().attentionClear();
            default -> throw new IllegalArgumentException("Unknown attention mode " + mode);
        }
        source.sendSuccess(() -> Component.literal("Ancient Dragon attention: " + mode), true);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        int removed = 0;
        for (Entity entity : source.getLevel().getAllEntities()) {
            if (entity instanceof AncientDragonEntity) {
                entity.discard();
                removed++;
            }
        }
        int count = removed;
        source.sendSuccess(() -> Component.literal("Removed " + count + " Ancient Dragon host(s)"), true);
        return removed;
    }

    private static int status(CommandSourceStack source) {
        long count = 0L;
        for (Entity entity : source.getLevel().getAllEntities()) {
            if (entity instanceof AncientDragonEntity) {
                count++;
            }
        }
        Optional<AncientDragonEntity> nearest = nearest(source);
        if (nearest.isEmpty()) {
            long loadedCount = count;
            source.sendSuccess(() -> Component.literal("Loaded Ancient Dragon hosts: " + loadedCount), false);
            return (int) Math.min(count, Integer.MAX_VALUE);
        }
        AncientDragonEntity dragon = nearest.get();
        String attack = dragon.currentAttack().map(Attack::name).orElse("none");
        String target = dragon.currentTargetId().map(java.util.UUID::toString).orElse("none");
        String lastPart = dragon.lastHitPart().map(AncientDragonPartKind::serializedName).orElse("none");
        var pose = dragon.dragonPoseState();
        long loadedCount = count;
        source.sendSuccess(() -> Component.literal(String.format(
                java.util.Locale.ROOT,
                "Ancient Dragon hosts=%d state=%s state_ticks=%d phase=%s health=%.1f/%.1f participants=%d attack=%s target=%s last_part=%s route_target=%s patrol_index=%d disengaged_ticks=%d maneuver=%s maneuver_cooldown=%d attention=%s attention_target=%d attention_yaw=%.1f attention_pitch=%.1f",
                loadedCount,
                dragon.bossState().serializedName(),
                dragon.stateTicks(),
                dragon.encounterPhase().serializedName(),
                dragon.getHealth(),
                dragon.getMaxHealth(),
                dragon.scaledParticipants(),
                attack,
                target,
                lastPart,
                dragon.flightTargetName(),
                dragon.patrolWaypointIndex(),
                dragon.disengagedTicks(),
                pose.maneuver().name().toLowerCase(),
                dragon.maneuverCooldownTicks(),
                pose.attentionMode().name().toLowerCase(),
                pose.attentionTargetEntityId(),
                Math.toDegrees(pose.attentionYawRadians()),
                Math.toDegrees(pose.attentionPitchRadians()))), false);
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    private static Optional<AncientDragonEntity> nearest(CommandSourceStack source) {
        Vec3 origin = source.getPosition();
        AncientDragonEntity nearest = null;
        double nearestDistance = TARGET_RADIUS_SQUARED;
        for (Entity entity : source.getLevel().getAllEntities()) {
            if (!(entity instanceof AncientDragonEntity dragon)) {
                continue;
            }
            double distance = dragon.position().distanceToSqr(origin);
            if (distance <= nearestDistance) {
                nearest = dragon;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static int noDragon(CommandSourceStack source) {
        source.sendFailure(Component.literal("No loaded Ancient Dragon within 1024 blocks"));
        return 0;
    }

    private static String formatPosition(Vec3 position) {
        return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", position.x(), position.y(), position.z());
    }
}

package com.liy.ancientdragon.entity;

import com.liy.ancientdragon.animation.AncientDragonAnimations;
import com.liy.ancientdragon.advancement.AncientDragonCriteria;
import com.liy.ancientdragon.atmosphere.DragonAtmosphereCue;
import com.liy.ancientdragon.atmosphere.DragonAtmosphereDispatcher;
import com.liy.ancientdragon.animation.pose.DragonPoseState;
import com.liy.ancientdragon.animation.pose.DragonPoseDynamics;
import com.liy.ancientdragon.animation.pose.DragonStagedPose;
import com.liy.ancientdragon.animation.pose.DragonPoseState.AttentionMode;
import com.liy.ancientdragon.animation.pose.DragonPoseState.Curve;
import com.liy.ancientdragon.animation.pose.DragonPoseState.Maneuver;
import com.liy.ancientdragon.animation.pose.DragonQuaternion;
import com.liy.ancientdragon.boss.AncientDragonBossState;
import com.liy.ancientdragon.boss.AncientDragonArenaVolume;
import com.liy.ancientdragon.boss.AncientDragonAttackDirector;
import com.liy.ancientdragon.boss.AncientDragonCorpseDissipation;
import com.liy.ancientdragon.boss.AncientDragonCorpseExperience;
import com.liy.ancientdragon.boss.AncientDragonCorpseFallPhysics;
import com.liy.ancientdragon.boss.AncientDragonCorpseLoot;
import com.liy.ancientdragon.boss.AncientDragonCorpseRewardService;
import com.liy.ancientdragon.boss.AncientDragonEncounterRules;
import com.liy.ancientdragon.boss.AncientDragonEncounterData;
import com.liy.ancientdragon.boss.AncientDragonEncounterData.Stage;
import com.liy.ancientdragon.boss.AncientDragonEncounterRules.Attack;
import com.liy.ancientdragon.boss.AncientDragonEncounterRules.AttackDomain;
import com.liy.ancientdragon.boss.AncientDragonEncounterRules.Phase;
import com.liy.ancientdragon.boss.AncientDragonParticipantLedger;
import com.liy.ancientdragon.boss.AncientDragonStability;
import com.liy.ancientdragon.chronicle.DragonChronicleEvent;
import com.liy.ancientdragon.chronicle.DragonChronicleService;
import com.liy.ancientdragon.entity.DragonFlightController.FlightEnvelope;
import com.liy.ancientdragon.entity.DragonFlightController.FlightIntent;
import com.liy.ancientdragon.item.AncientDragonItems;
import com.liy.blendlib.api.BlendAnimationKey;
import com.liy.blendlib.fabric.common.animation.BlendAnimations;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative first playable encounter slice for the Ancient Dragon.
 *
 * <p>Animation is presentation only. Targeting, movement, timings, damage, participant scaling,
 * and attack geometry are all decided on the server.</p>
 */
public final class AncientDragonEntity extends Mob {
    public static final int AWAKEN_DURATION_TICKS = 181;
    public static final int TAKEOFF_DURATION_TICKS = 81;
    static final int DESCEND_ANIMATION_TICKS = 61;
    static final int LAND_DURATION_TICKS = 81;
    private static final int DEATH_DURATION_TICKS = 241;
    private static final int DEATH_RETURN_MAX_TICKS = 160;
    /** Five and a half seconds of visible purple flight path, re-emitted at historical points. */
    private static final int FLIGHT_TRAIL_DURATION_TICKS = 110;
    private static final int FLIGHT_TRAIL_SAMPLE_INTERVAL_TICKS = 4;
    private static final int STORM_AMBIENT_LIGHTNING_MIN_INTERVAL_TICKS = 72;
    private static final int STORM_AMBIENT_LIGHTNING_INTERVAL_VARIANCE_TICKS = 49;
    private static final double STORM_AMBIENT_LIGHTNING_MIN_RADIUS_BLOCKS = 42.0D;
    private static final double STORM_AMBIENT_LIGHTNING_MAX_RADIUS_BLOCKS = 156.0D;
    private static final double STORM_AMBIENT_LIGHTNING_PLAYER_CLEARANCE_BLOCKS = 20.0D;
    private static final int CORPSE_PARTICIPANT_PROTECTION_TICKS = 12_000;
    private static final double CORPSE_GROUND_EPSILON = 0.0001D;
    private static final double DISTURB_RADIUS_SQUARED = AncientDragonEncounterRules.AWAKEN_RADIUS_BLOCKS
            * AncientDragonEncounterRules.AWAKEN_RADIUS_BLOCKS;
    private static final double OBSERVER_ATTENTION_RANGE = 144.0D;
    private static final int NO_TARGET_ATTACK_DELAY_TICKS = 40;
    private static final int RETURN_TO_ROOST_DELAY_TICKS = AncientDragonEncounterRules.RETURN_TO_ROOST_DELAY_TICKS;
    private static final EntityDataAccessor<Float> DATA_ORIENTATION_X =
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ORIENTATION_Y =
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ORIENTATION_Z =
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ORIENTATION_W =
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TORSO_PITCH =
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TORSO_YAW =
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TORSO_ROLL =
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TAIL_PITCH =
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TAIL_YAW =
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TAIL_ROLL =
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ATTENTION_YAW =
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ATTENTION_PITCH =
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_ATTENTION_MODE =
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_ATTENTION_TARGET =
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_MANEUVER =
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_BLOCKS_PLAYERS =
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final List<EntityDataAccessor<Integer>> DATA_STAGED_NECK = List.of(
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.INT),
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.INT),
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.INT),
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.INT),
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.INT));
    private static final List<EntityDataAccessor<Integer>> DATA_STAGED_TAIL = List.of(
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.INT),
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.INT),
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.INT),
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.INT),
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.INT),
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.INT),
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.INT),
            SynchedEntityData.defineId(AncientDragonEntity.class, EntityDataSerializers.INT));

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            UUID.randomUUID(),
            Component.translatable("entity.ancient_dragon.ancient_dragon"),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS);
    private final ServerBossEvent stabilityEvent = new ServerBossEvent(
            UUID.randomUUID(),
            Component.translatable("bossbar.ancient_dragon.stability"),
            BossEvent.BossBarColor.BLUE,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private final AncientDragonParticipantLedger participantLedger = new AncientDragonParticipantLedger();
    private final AncientDragonAttackDirector attackDirector = new AncientDragonAttackDirector();
    private final AncientDragonStability stability = new AncientDragonStability();
    private final EnumMap<AncientDragonPartKind, AncientDragonPartEntity> collisionParts =
            new EnumMap<>(AncientDragonPartKind.class);
    private final DragonFlightController flightController = new DragonFlightController();
    private final DragonAttentionController attentionController = new DragonAttentionController();
    private final DragonPoseDynamics poseDynamics = new DragonPoseDynamics();
    private final Deque<FlightTrailSample> flightTrail = new ArrayDeque<>();

    private AncientDragonBossState bossState = AncientDragonBossState.DORMANT;
    private Phase phase = Phase.MOUNTAIN;
    private Attack currentAttack;
    private AttackDomain currentAttackDomain = AttackDomain.AIR;
    private Phase pendingPhase;
    private Vec3 attackFocus = Vec3.ZERO;
    private final List<Vec3> stormStrikePositions = new ArrayList<>();
    private int stateTicks;
    private int attackCooldownTicks;
    private int attackSequence;
    private int patrolWaypointIndex;
    private int approachWaypointIndex;
    private int disengagedTicks;
    private int scaledParticipants = 1;
    private int currentActiveParticipants = 1;
    private int airAttacksThisCycle;
    private int groundCombatTicksRemaining;
    private int forcedLandingDelayTicks;
    private int stormAmbientLightningCooldown;
    private boolean forcedLandingPending;
    private boolean initialTakeoff = true;
    private boolean perchPending;
    private int perchApproachStage;
    private Vec3 takeoffTarget = Vec3.ZERO;
    private final Set<UUID> playersHitByCurrentAttack = new HashSet<>();
    private final Set<UUID> victoryEligiblePlayers = new HashSet<>();
    private final Map<UUID, Integer> pendingCorpseExperience = new HashMap<>();
    private final Map<UUID, EnumMap<CorpseHarvestNode, Integer>> pendingCorpseLoot = new HashMap<>();
    private final EnumMap<CorpseHarvestNode, Integer> corpseHarvestProgress =
            new EnumMap<>(CorpseHarvestNode.class);
    private final EnumSet<CorpseHarvestNode> harvestedCorpseNodes =
            EnumSet.noneOf(CorpseHarvestNode.class);
    /** -1 until the full corpse reward allocation completes, then elapsed dissipation ticks. */
    private int corpseDissipationTicks = -1;
    private Map<Attack, Double> lastDirectorScores = Map.of();
    private boolean initialAnimationPublished;
    private boolean encounterOriginSet;
    private Vec3 encounterOrigin = Vec3.ZERO;
    private int encounterQuarterTurns;
    private Vec3 headAttackOrigin = Vec3.ZERO;
    private Vec3 headAttackDirection = new Vec3(0.0D, 0.0D, 1.0D);
    private Vec3 tailAttackOrigin = Vec3.ZERO;
    private final Set<UUID> tailSweepHitPlayers = new HashSet<>();
    private final EnumMap<AncientDragonPartKind, Vec3> previousTailPartCenters =
            new EnumMap<>(AncientDragonPartKind.class);
    private Vec3 solarPeakHold = Vec3.ZERO;
    private Vec3 solarSweepCenter = Vec3.ZERO;
    private boolean solarBreathStarted;
    private int solarApproachTicks;
    private AncientDragonPartKind lastHitPart;
    private AncientDragonPartKind incomingHitPart;
    private DragonAttentionController.AttentionStep attentionStep = new DragonAttentionController.AttentionStep(
            0.0D, 0.0D, AttentionMode.SCAN, -1, Vec3.ZERO, 0.0D);
    private DragonAttentionController.AttentionOverride attentionOverride =
            DragonAttentionController.AttentionOverride.NONE;
    private int recentAttackerEntityId = -1;
    private int recentAttackerTick = Integer.MIN_VALUE;
    private Vec3 recentAttackerLastKnownPosition = Vec3.ZERO;
    private boolean flightControllerInitialized;
    private boolean participantScalingInitialized;
    private AncientDragonArenaVolume arenaVolume;
    private boolean debugEncounter;
    private boolean debugHitboxes;
    private boolean debugRoute;
    private boolean debugDirector;

    public AncientDragonEntity(EntityType<? extends AncientDragonEntity> entityType, Level level) {
        super(entityType, level);
        setNoGravity(true);
        setPersistenceRequired();
        bossEvent.setVisible(false);
        bossEvent.setDarkenScreen(true);
        bossEvent.setCreateWorldFog(false);
        stabilityEvent.setVisible(false);
        stabilityEvent.setDarkenScreen(false);
        stabilityEvent.setCreateWorldFog(false);
    }

    @Override
    protected void registerGoals() {
        // Flight and attacks use the explicit encounter state machine below.
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ORIENTATION_X, 0.0F);
        builder.define(DATA_ORIENTATION_Y, 0.0F);
        builder.define(DATA_ORIENTATION_Z, 0.0F);
        builder.define(DATA_ORIENTATION_W, 1.0F);
        builder.define(DATA_TORSO_PITCH, 0.0F);
        builder.define(DATA_TORSO_YAW, 0.0F);
        builder.define(DATA_TORSO_ROLL, 0.0F);
        builder.define(DATA_TAIL_PITCH, 0.0F);
        builder.define(DATA_TAIL_YAW, 0.0F);
        builder.define(DATA_TAIL_ROLL, 0.0F);
        builder.define(DATA_ATTENTION_YAW, 0.0F);
        builder.define(DATA_ATTENTION_PITCH, 0.0F);
        builder.define(DATA_ATTENTION_MODE, (byte) AttentionMode.SCAN.ordinal());
        builder.define(DATA_ATTENTION_TARGET, -1);
        builder.define(DATA_MANEUVER, (byte) Maneuver.NONE.ordinal());
        builder.define(DATA_BLOCKS_PLAYERS, true);
        for (EntityDataAccessor<Integer> accessor : DATA_STAGED_NECK) {
            builder.define(accessor, 0);
        }
        for (EntityDataAccessor<Integer> accessor : DATA_STAGED_TAIL) {
            builder.define(accessor, 0);
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return;
        }
        setNoGravity(true);
        // The return route deliberately uses the open volume around the roost and cliff. The
        // parent is only a small control root; letting vanilla resolve it against the final
        // cliff lip can leave the whole animated dragon permanently short of its next waypoint.
        // Combat hit proxies remain server-authoritative. Only the scripted return handoff
        // bypasses root collision resolution; patrol and combat movement keep normal physics.
        noPhysics = DragonTransitPolicy.bypassesTerrainCollision(bossState);
        resetFallDistance();

        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean blocksPlayers = DragonTransitPolicy.blocksPlayers(bossState);
        if (entityData.get(DATA_BLOCKS_PLAYERS) != blocksPlayers) {
            entityData.set(DATA_BLOCKS_PLAYERS, blocksPlayers);
        }
        if (!encounterOriginSet) {
            setEncounterOrigin(position());
        }
        if (!initialAnimationPublished) {
            publishPersistentAnimation(animationForState());
        }
        initializeFlightControllerIfNeeded();
        ensureParticipantScaling();
        updateParticipantRuntime(serverLevel);
        updateBossBar();
        if (!isCorpseDissipating()) {
            ensureCollisionParts(serverLevel);
        }
        if (bossState == AncientDragonBossState.CORPSE) {
            deliverPendingCorpseExperience(serverLevel);
            deliverPendingCorpseLoot(serverLevel);
        }

        if (!isAlive()) {
            if (!isCorpseDissipating()) {
                updateCollisionParts(serverLevel);
            }
            return;
        }

        stateTicks++;
        updatePhase(serverLevel);
        updateAttention(serverLevel);
        switch (bossState) {
            case DORMANT -> tickDormant(serverLevel);
            case AWAKENING -> tickAwakening(serverLevel);
            case TAKEOFF -> tickTakeoff(serverLevel);
            case DEPARTING -> tickDeparting(serverLevel);
            case CRUISING -> tickCruising(serverLevel);
            case PERCH_APPROACH -> tickPerchApproach(serverLevel);
            case PERCHED -> tickPerched(serverLevel);
            case ATTACKING -> tickAttack(serverLevel);
            case COMBAT_RETURNING -> tickCombatReturning(serverLevel);
            case COMBAT_APPROACHING -> tickCombatApproaching(serverLevel);
            case COMBAT_LANDING -> tickCombatLanding(serverLevel);
            case GROUND_COMBAT -> tickGroundCombat(serverLevel);
            case RETURNING -> tickReturning(serverLevel);
            case APPROACHING -> tickApproaching(serverLevel);
            case LANDING -> tickLanding(serverLevel);
            case DEATH_RETURNING -> tickDeathReturning(serverLevel);
            case DYING -> stopFlight();
            case CORPSE -> flightController.stop();
        }
        tickFlightTrail(serverLevel);
        tickDragonAura(serverLevel);
        tickStormAmbientLightning(serverLevel);
        advanceAndSynchronizePoseState();
        if (!isCorpseDissipating()) {
            updateCollisionParts(serverLevel);
        }
        tickDebugVisuals(serverLevel);
        if (tickCount % 20 == 0) {
            DragonAtmosphereDispatcher.snapshot(this, serverLevel);
        }
    }

    private void tickDormant(ServerLevel serverLevel) {
        stopFlight();
        boolean disturbed = serverLevel.getPlayers(this::canDisturb).stream()
                .anyMatch(player -> distanceToSqr(player) <= DISTURB_RADIUS_SQUARED);
        if (disturbed) {
            awaken(serverLevel);
        }
    }

    private void tickAwakening(ServerLevel serverLevel) {
        stopFlight();
        if (stateTicks == 45) {
            serverLevel.sendParticles(
                    ParticleTypes.CLOUD, getX(), getY() + 1.0D, getZ(), 80, 6.0D, 1.5D, 6.0D, 0.08D);
        }
        if (stateTicks == 122) {
            serverLevel.playSound(
                    null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0F, 0.72F);
        }
        if (stateTicks >= AWAKEN_DURATION_TICKS) {
            beginTakeoff(serverLevel, true);
        }
    }

    private void tickTakeoff(ServerLevel serverLevel) {
        // Keep the compact takeoff pose until the entire dragon is above the frozen mountain.
        // Switching to cruise at the clip's fixed 81-tick boundary used to open the wings near
        // (147,160,-5), where the approved east cliff reaches roughly Y=185..205. The assembly
        // point is the first asset-tested position where the full cruise rig is clear.
        boolean animationComplete = stateTicks > TAKEOFF_DURATION_TICKS;
        Vec3 ascentTarget = takeoffTarget.equals(Vec3.ZERO)
                ? SacredMountainFlightPath.takeoffWorldPosition(encounterOrigin, encounterQuarterTurns)
                : takeoffTarget;
        double ascentSpeed = SacredMountainFlightPath.takeoffMaximumSpeed(animationComplete);
        moveToward(ascentTarget, ascentSpeed, FlightEnvelope.TAKEOFF);
        if (stateTicks % 16 == 0) {
            serverLevel.playSound(
                    null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 4.0F, 0.8F);
            serverLevel.sendParticles(
                    ParticleTypes.CLOUD, getX(), getY(), getZ(), 28, 5.0D, 1.0D, 5.0D, 0.12D);
        }
        if (animationComplete && position().distanceToSqr(ascentTarget)
                <= SacredMountainFlightPath.ARRIVAL_RADIUS * SacredMountainFlightPath.ARRIVAL_RADIUS) {
            beginDeparture();
        }
    }

    private void tickDeparting(ServerLevel serverLevel) {
        SacredMountainFlightPath.Waypoint assembly = SacredMountainFlightPath.waypoint(
                SacredMountainFlightPath.ASSEMBLY_INDEX, encounterQuarterTurns);
        Vec3 destination = encounterOrigin.add(assembly.offset());
        moveToward(destination, assembly.maximumSpeed(), FlightEnvelope.TAKEOFF);
        if (stateTicks % 20 == 0) {
            serverLevel.sendParticles(
                    ParticleTypes.CLOUD, getX(), getY(), getZ(), 18, 4.0D, 0.8D, 4.0D, 0.08D);
        }
        if (position().distanceToSqr(destination)
                <= SacredMountainFlightPath.ARRIVAL_RADIUS * SacredMountainFlightPath.ARRIVAL_RADIUS) {
            patrolWaypointIndex = SacredMountainFlightPath.nextIndex(SacredMountainFlightPath.ASSEMBLY_INDEX);
            if (perchPending) {
                beginPerchApproach();
            } else if (participantLedger.emptyTicks() >= RETURN_TO_ROOST_DELAY_TICKS) {
                beginReturning();
            } else {
                beginCruising();
            }
        }
    }

    private void tickCruising(ServerLevel serverLevel) {
        List<ServerPlayer> participants = activeParticipants(serverLevel);
        Optional<ServerPlayer> target = selectTarget(participants);
        // The patrol route belongs to the authored mountain, not to the target's per-tick position.
        // Players influence attention and attacks without dragging the route away from the peaks.
        flySacredMountainPatrol();

        if (attackCooldownTicks > 0) {
            attackCooldownTicks--;
        }
        if (target.isEmpty()) {
            attackCooldownTicks = Math.max(attackCooldownTicks, NO_TARGET_ATTACK_DELAY_TICKS);
            if (participantLedger.emptyTicks() >= RETURN_TO_ROOST_DELAY_TICKS) {
                beginReturning();
            }
            return;
        }
        if (forcedLandingPending) {
            if (forcedLandingDelayTicks > 0) {
                forcedLandingDelayTicks--;
            } else {
                beginCombatReturning();
            }
            return;
        }
        if (flightController.maneuver() != Maneuver.NONE) {
            return;
        }
        if (attackCooldownTicks <= 0) {
            beginAttack(target.get(), AttackDomain.AIR, participants);
        }
    }

    private void tickPerchApproach(ServerLevel serverLevel) {
        SacredMountainFlightPath.Perch perch = SacredMountainFlightPath.perchFor(phase, encounterQuarterTurns);
        Vec3 destination = encounterOrigin.add(
                perchApproachStage == 0 ? perch.skyApproachOffset() : perch.rootOffset());
        moveToward(destination, perchApproachStage == 0 ? 0.46D : 0.22D,
                perchApproachStage == 0 ? FlightEnvelope.CRUISE : FlightEnvelope.ATTACK);
        double arrival = perchApproachStage == 0 ? 12.0D : 4.0D;
        if (position().distanceToSqr(destination) > arrival * arrival) {
            return;
        }
        if (perchApproachStage == 0) {
            perchApproachStage = 1;
            stateTicks = 0;
            triggerAnimation(AncientDragonAnimations.FLY_DESCEND);
        } else {
            beginPerched();
        }
    }

    private void tickPerched(ServerLevel serverLevel) {
        stopFlight();
        if (participantLedger.emptyTicks() >= RETURN_TO_ROOST_DELAY_TICKS) {
            beginCombatReturning();
            return;
        }
        if (forcedLandingPending) {
            if (forcedLandingDelayTicks > 0) {
                forcedLandingDelayTicks--;
            } else {
                beginCombatReturning();
            }
            return;
        }
        if (stateTicks >= 81) {
            beginTakeoff(serverLevel, false);
        }
    }

    private void tickAttack(ServerLevel serverLevel) {
        List<ServerPlayer> participants = activeParticipants(serverLevel);
        Optional<ServerPlayer> target = selectTarget(participants);
        if (currentAttackDomain == AttackDomain.GROUND && groundCombatTicksRemaining > 0) {
            groundCombatTicksRemaining--;
        }
        if (currentAttack == null) {
            if (currentAttackDomain == AttackDomain.GROUND) {
                beginGroundCombat();
            } else {
                beginCruising();
            }
            return;
        }

        switch (currentAttack) {
            case DIVE_STRIKE -> tickDiveStrike(serverLevel, target);
            case TAIL_SWEEP -> tickTailSweep(serverLevel, target);
            case STORM_BURST -> tickStormBurst(serverLevel, participants, target);
            case SOLAR_BREATH -> tickSolarBreath(serverLevel, participants, target);
            case LIGHTNING_CHAIN -> tickLightningChain(serverLevel, target);
            case WIND_BLADE -> tickWindBlade(serverLevel, target);
            case STORM_CAGE -> tickStormCage(serverLevel, target);
            case BITE_HEAVY -> tickBiteHeavy(serverLevel);
            case WING_SLAM -> tickWingSlam(serverLevel);
            case RIFT_CLAW -> tickRiftClaw(serverLevel, target);
            case GROUND_STORM_BURST -> tickGroundStormBurst(serverLevel, participants);
            case GROUND_SOLAR_BREATH -> tickGroundSolarBreath(serverLevel, participants);
        }

        if (stateTicks >= currentAttack.durationTicks()) {
            AttackDomain completedDomain = currentAttackDomain;
            currentAttack = null;
            playersHitByCurrentAttack.clear();
            if (completedDomain == AttackDomain.GROUND) {
                if (groundCombatTicksRemaining <= 0) {
                    perchPending = true;
                    beginTakeoff(serverLevel, false);
                } else {
                    beginGroundCombat();
                }
            } else {
                airAttacksThisCycle++;
                if (forcedLandingPending) {
                    forcedLandingDelayTicks = 33;
                    triggerAnimation(AncientDragonAnimations.HIT_REACT);
                    beginCruisingWithoutCooldown();
                } else if (airAttacksThisCycle >= AncientDragonEncounterRules.AIR_ATTACKS_PER_CYCLE) {
                    beginCombatReturning();
                } else {
                    beginCruising();
                }
            }
        }
    }

    private void tickCombatReturning(ServerLevel serverLevel) {
        Vec3 destination = SacredMountainFlightPath.worldPosition(
                encounterOrigin, SacredMountainFlightPath.ASSEMBLY_INDEX, encounterQuarterTurns);
        moveToward(destination, 0.64D, FlightEnvelope.CRUISE);
        if (position().distanceToSqr(destination)
                <= SacredMountainFlightPath.ARRIVAL_RADIUS * SacredMountainFlightPath.ARRIVAL_RADIUS) {
            beginCombatApproach();
        }
    }

    private void tickCombatApproaching(ServerLevel serverLevel) {
        SacredMountainFlightPath.ApproachWaypoint waypoint =
                SacredMountainFlightPath.combatApproachWaypoint(approachWaypointIndex, encounterQuarterTurns);
        Vec3 destination = SacredMountainFlightPath.combatApproachWorldPosition(
                encounterOrigin, approachWaypointIndex, encounterQuarterTurns);
        moveToward(destination, waypoint.maximumSpeed(), FlightEnvelope.CRUISE);
        double arrivalRadius = approachWaypointIndex == SacredMountainFlightPath.combatApproachSize() - 1
                ? 4.0D
                : SacredMountainFlightPath.APPROACH_ARRIVAL_RADIUS;
        if (position().distanceToSqr(destination) > arrivalRadius * arrivalRadius) {
            return;
        }
        int next = SacredMountainFlightPath.nextCombatApproachIndex(approachWaypointIndex);
        if (next >= SacredMountainFlightPath.combatApproachSize()) {
            beginCombatLanding();
        } else {
            approachWaypointIndex = next;
        }
    }

    private void tickCombatLanding(ServerLevel serverLevel) {
        Vec3 platform = SacredMountainFlightPath.combatPlatformWorldPosition(
                encounterOrigin, encounterQuarterTurns);
        moveToward(platform, 0.36D, FlightEnvelope.ATTACK);
        if (stateTicks % 12 == 0) {
            serverLevel.sendParticles(
                    ParticleTypes.CLOUD, getX(), getY(), getZ(), 22, 4.5D, 0.7D, 4.5D, 0.06D);
        }
        if (stateTicks >= LAND_DURATION_TICKS) {
            finishCombatLanding();
        }
    }

    private void tickGroundCombat(ServerLevel serverLevel) {
        stopFlight();
        if (participantLedger.emptyTicks() >= RETURN_TO_ROOST_DELAY_TICKS) {
            perchPending = false;
            beginTakeoff(serverLevel, false);
            return;
        }
        if (groundCombatTicksRemaining > 0) {
            groundCombatTicksRemaining--;
        }
        if (groundCombatTicksRemaining <= 0) {
            perchPending = true;
            beginTakeoff(serverLevel, false);
            return;
        }
        if (attackCooldownTicks > 0) {
            attackCooldownTicks--;
            return;
        }
        List<ServerPlayer> participants = activeParticipants(serverLevel);
        selectTarget(participants).ifPresent(target -> beginAttack(target, AttackDomain.GROUND, participants));
    }

    private void tickDeathReturning(ServerLevel serverLevel) {
        Vec3 deathApproach = SacredMountainFlightPath.deathApproachWorldPosition(
                encounterOrigin, encounterQuarterTurns);
        moveToward(deathApproach, 3.5D, FlightEnvelope.DIVE);
        if (position().distanceToSqr(deathApproach) <= 8.0D * 8.0D
                || stateTicks >= DEATH_RETURN_MAX_TICKS) {
            Vec3 platform = SacredMountainFlightPath.combatPlatformWorldPosition(
                    encounterOrigin, encounterQuarterTurns);
            setPos(platform.x, platform.y, platform.z);
            alignRoostFacing();
            beginDeathLandmark(serverLevel.damageSources().generic());
        }
    }

    private void tickReturning(ServerLevel serverLevel) {
        if (!activeParticipants(serverLevel).isEmpty()) {
            beginDeparture();
            return;
        }
        Vec3 destination = SacredMountainFlightPath.worldPosition(
                encounterOrigin, SacredMountainFlightPath.ASSEMBLY_INDEX, encounterQuarterTurns);
        moveToward(destination, 0.64D, FlightEnvelope.CRUISE);
        if (position().distanceToSqr(destination)
                <= SacredMountainFlightPath.ARRIVAL_RADIUS * SacredMountainFlightPath.ARRIVAL_RADIUS) {
            beginApproach();
        }
    }

    private void tickApproaching(ServerLevel serverLevel) {
        if (!activeParticipants(serverLevel).isEmpty()) {
            beginDeparture();
            return;
        }
        SacredMountainFlightPath.ApproachWaypoint waypoint =
                SacredMountainFlightPath.approachWaypoint(approachWaypointIndex, encounterQuarterTurns);
        Vec3 destination = encounterOrigin.add(waypoint.offset());
        moveToward(destination, waypoint.maximumSpeed(), FlightEnvelope.CRUISE);
        double arrivalRadius = approachWaypointIndex == SacredMountainFlightPath.approachSize() - 1
                ? 4.0D
                : SacredMountainFlightPath.APPROACH_ARRIVAL_RADIUS;
        if (position().distanceToSqr(destination) > arrivalRadius * arrivalRadius) {
            return;
        }
        int next = SacredMountainFlightPath.nextApproachIndex(approachWaypointIndex);
        if (next >= SacredMountainFlightPath.approachSize()) {
            beginLanding();
        } else {
            approachWaypointIndex = next;
        }
    }

    private void tickLanding(ServerLevel serverLevel) {
        moveToward(encounterOrigin, 0.28D, FlightEnvelope.ATTACK);
        if (stateTicks % 12 == 0) {
            serverLevel.sendParticles(
                    ParticleTypes.CLOUD, getX(), getY(), getZ(), 22, 4.5D, 0.7D, 4.5D, 0.06D);
        }
        if (stateTicks >= LAND_DURATION_TICKS) {
            finishLanding();
        }
    }

    private void tickDiveStrike(ServerLevel serverLevel, Optional<ServerPlayer> target) {
        if (stateTicks <= 34 && target.isPresent()) {
            ServerPlayer player = target.get();
            attackFocus = AncientDragonAttackGeometry.leadTarget(
                    player.position(), player.getDeltaMovement(), 8, 12.0D);
        }
        Vec3 targetPosition = attackFocus;
        if (stateTicks <= 68) {
            moveToward(targetPosition.add(0.0D, 1.5D, 0.0D), 0.78D, FlightEnvelope.DIVE);
        } else {
            if (stateTicks == 69) {
                requestManeuver(Maneuver.PULL_UP);
            }
            moveToward(targetPosition.add(0.0D, 20.0D, 0.0D), 0.58D, FlightEnvelope.DIVE);
        }
        if (stateTicks >= 48 && stateTicks <= 64) {
            updateCollisionParts(serverLevel);
            if (stateTicks == 58) {
                serverLevel.sendParticles(
                        ParticleTypes.EXPLOSION,
                        headAttackOrigin.x,
                        headAttackOrigin.y,
                        headAttackOrigin.z,
                        18,
                        4.0D,
                        2.0D,
                        4.0D,
                        0.05D);
                DragonAtmosphereDispatcher.cue(this, serverLevel, DragonAtmosphereCue.DIVE_IMPACT, headAttackOrigin);
            }
            damagePlayersTouchingParts(
                    serverLevel,
                    Set.of(AncientDragonPartKind.HEAD, AncientDragonPartKind.CHEST),
                    Attack.DIVE_STRIKE.baseDamage(),
                    1.35D,
                    0.35D);
        }
    }

    private void tickTailSweep(ServerLevel serverLevel, Optional<ServerPlayer> target) {
        Vec3 focus = target.map(Entity::position).orElse(encounterOrigin);
        moveToward(focus.add(0.0D, 7.0D, 0.0D), 0.24D, FlightEnvelope.ATTACK);
        updateCollisionParts(serverLevel);
        tickTailSweepContacts(serverLevel);
        if (AncientDragonAttackGeometry.tailContactActive(stateTicks) && stateTicks % 4 == 0) {
            serverLevel.sendParticles(
                    ParticleTypes.CLOUD,
                    tailAttackOrigin.x,
                    tailAttackOrigin.y,
                    tailAttackOrigin.z,
                    12,
                    1.2D,
                    0.8D,
                    1.2D,
                    0.08D);
        }
    }

    private void tickStormBurst(
            ServerLevel serverLevel, List<ServerPlayer> participants, Optional<ServerPlayer> primaryTarget) {
        Vec3 focus = primaryTarget.map(Entity::position).orElse(encounterOrigin);
        moveTowardFacing(
                focus.add(0.0D, 18.0D, 0.0D), 0.3D, focus, 8.0F, FlightEnvelope.ATTACK);

        List<ServerPlayer> pressureOrder = participants.stream().sorted(targetComparator()).toList();
        int pressureTargets = Math.min(
                AncientDragonEncounterRules.simultaneousPressureTargets(scaledParticipants), participants.size());
        if (AncientDragonAttackGeometry.stormTargetTracksPlayer(stateTicks)) {
            stormStrikePositions.clear();
            for (int index = 0; index < pressureTargets; index++) {
                ServerPlayer player = pressureOrder.get(index);
                stormStrikePositions.add(player.position());
            }
        }
        if (stateTicks >= AncientDragonAttackGeometry.STORM_TELEGRAPH_START_TICK
                && stateTicks < AncientDragonAttackGeometry.STORM_STRIKE_TICK
                && stateTicks % 2 == 0) {
            double progress = AncientDragonAttackGeometry.stormTelegraphProgress(stateTicks);
            for (Vec3 strikePosition : stormStrikePositions) {
                spawnStormTelegraph(serverLevel, strikePosition, progress);
            }
        }
        if (stateTicks == AncientDragonAttackGeometry.STORM_STRIKE_TICK) {
            updateCollisionParts(serverLevel);
            serverLevel.playSound(
                    null,
                    headAttackOrigin.x,
                    headAttackOrigin.y,
                    headAttackOrigin.z,
                    SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.HOSTILE,
                    5.0F,
                    0.82F);
            DragonAtmosphereDispatcher.cue(this, serverLevel, DragonAtmosphereCue.STORM_STRIKE, headAttackOrigin);
            Set<UUID> damagedPlayers = new HashSet<>();
            for (Vec3 strikePosition : stormStrikePositions) {
                spawnBeamParticles(
                        serverLevel, headAttackOrigin, strikePosition.add(0.0D, 1.0D, 0.0D), 14, true);
                electricStrike(
                        serverLevel,
                        strikePosition,
                        Attack.STORM_BURST,
                        AncientDragonAttackGeometry.STORM_STRIKE_RADIUS_BLOCKS,
                        damagedPlayers);
            }
        }
    }

    private void tickSolarBreath(
            ServerLevel serverLevel, List<ServerPlayer> participants, Optional<ServerPlayer> primaryTarget) {
        if (!solarBreathStarted) {
            solarApproachTicks++;
            primaryTarget.ifPresent(player -> solarSweepCenter = player.position());
            attackFocus = solarSweepCenter;
            moveTowardFacing(solarPeakHold, 0.72D, solarSweepCenter, 12.0F, FlightEnvelope.ATTACK);
            boolean arrived = position().distanceToSqr(solarPeakHold) <= 12.0D * 12.0D;
            if (arrived || solarApproachTicks >= 600) {
                solarBreathStarted = true;
                stateTicks = 0;
                triggerAnimation(AncientDragonAnimations.AERIAL_SOLAR_BREATH);
            } else {
                stateTicks = 0;
            }
            return;
        }

        Vec3 focus = AncientDragonAttackGeometry.solarSweepTarget(solarPeakHold, solarSweepCenter, stateTicks);
        attackFocus = focus;
        moveTowardFacing(position(), 0.0D, focus, 18.0F, FlightEnvelope.ATTACK);
        updateCollisionParts(serverLevel);
        Vec3 beamStart = headAttackOrigin;
        Vec3 aimedDirection = focus.subtract(beamStart);
        Vec3 beamDirection = aimedDirection.lengthSqr() > 0.0001D
                ? aimedDirection.normalize()
                : headAttackDirection;
        double beamLength = AncientDragonAttackGeometry.solarBeamLength(beamStart, focus);
        Vec3 beamEnd = clipBeamToTerrain(serverLevel, beamStart, beamStart.add(beamDirection.scale(beamLength)));

        if (stateTicks < AncientDragonAttackGeometry.SOLAR_PREVIEW_START_TICK && stateTicks % 2 == 0) {
            spawnSolarChargeParticles(serverLevel, beamStart, stateTicks);
        }
        if (stateTicks >= AncientDragonAttackGeometry.SOLAR_PREVIEW_START_TICK
                && stateTicks < AncientDragonAttackGeometry.SOLAR_ACTIVE_START_TICK
                && stateTicks % 2 == 0) {
            spawnBeamParticles(serverLevel, beamStart, beamEnd, 12, false);
        }
        if (stateTicks == AncientDragonAttackGeometry.SOLAR_ACTIVE_START_TICK) {
            DragonAtmosphereDispatcher.cue(this, serverLevel, DragonAtmosphereCue.SOLAR_IGNITION, beamStart);
        }
        if (stateTicks >= AncientDragonAttackGeometry.SOLAR_ACTIVE_START_TICK
                && stateTicks <= AncientDragonAttackGeometry.SOLAR_ACTIVE_END_TICK
                && stateTicks % 2 == 0) {
            spawnBeamParticles(serverLevel, beamStart, beamEnd, 28, true);
        }
        if (stateTicks >= AncientDragonAttackGeometry.SOLAR_ACTIVE_START_TICK
                && stateTicks <= AncientDragonAttackGeometry.SOLAR_ACTIVE_END_TICK
                && stateTicks % 10 == 4) {
            for (ServerPlayer player : participants) {
                double radius = AncientDragonAttackGeometry.SOLAR_BEAM_RADIUS_BLOCKS;
                if (distanceSquaredToSegment(
                                player.position().add(0.0D, 1.0D, 0.0D), beamStart, beamEnd)
                        <= radius * radius) {
                    hurtPlayer(serverLevel, player, Attack.SOLAR_BREATH.baseDamage());
                }
            }
        }
    }

    private void tickLightningChain(ServerLevel serverLevel, Optional<ServerPlayer> target) {
        if (AncientDragonAttackGeometry.lightningChainTracksTarget(stateTicks) && target.isPresent()) {
            ServerPlayer player = target.get();
            attackFocus = AncientDragonAttackGeometry.leadTarget(
                    player.position(), player.getDeltaMovement(), 6, 8.0D);
        }
        moveTowardFacing(
                attackFocus.add(0.0D, 20.0D, 0.0D),
                0.32D,
                attackFocus,
                10.0F,
                FlightEnvelope.ATTACK);
        if (AncientDragonAttackGeometry.lightningChainTelegraphActive(stateTicks)
                && stateTicks % 2 == 0) {
            spawnStormTelegraph(
                    serverLevel,
                    attackFocus,
                    AncientDragonAttackGeometry.lightningChainTelegraphProgress(stateTicks));
        }
        if (AncientDragonAttackGeometry.lightningChainStrikes(stateTicks)) {
            serverLevel.playSound(
                    null,
                    attackFocus.x,
                    attackFocus.y,
                    attackFocus.z,
                    SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.HOSTILE,
                    4.5F,
                    0.92F);
            electricStrike(
                    serverLevel,
                    attackFocus,
                    Attack.LIGHTNING_CHAIN,
                    AncientDragonAttackGeometry.LIGHTNING_CHAIN_RADIUS_BLOCKS,
                    new HashSet<>());
        }
    }

    private void tickWindBlade(ServerLevel serverLevel, Optional<ServerPlayer> target) {
        if (stateTicks <= 28 && target.isPresent()) {
            ServerPlayer player = target.get();
            attackFocus = AncientDragonAttackGeometry.leadTarget(
                    player.position(), player.getDeltaMovement(), 10, 14.0D);
        }
        moveTowardFacing(position(), 0.0D, attackFocus, 16.0F, FlightEnvelope.ATTACK);
        Vec3 direction = attackFocus.subtract(headAttackOrigin);
        if (direction.horizontalDistanceSqr() <= 0.0001D) {
            direction = flightController.orientation().forward();
        }
        List<Vec3> blades = AncientDragonAttackGeometry.spreadDirections(
                direction, AncientDragonAttackGeometry.WIND_BLADE_SPREAD_RADIANS);
        if (stateTicks >= AncientDragonAttackGeometry.WIND_BLADE_TELEGRAPH_START_TICK
                && stateTicks < AncientDragonAttackGeometry.WIND_BLADE_FIRE_TICKS[0]
                && stateTicks % 2 == 0) {
            for (Vec3 blade : blades) {
                spawnWindBladeLine(serverLevel, headAttackOrigin, blade, 64.0D, false);
            }
        }
        if (AncientDragonAttackGeometry.windBladeFires(stateTicks)) {
            serverLevel.playSound(
                    null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP,
                    SoundSource.HOSTILE, 4.0F, 0.78F + (stateTicks * 0.002F));
            for (Vec3 blade : blades) {
                fireWindBlade(serverLevel, headAttackOrigin, blade);
            }
        }
    }

    private void tickStormCage(ServerLevel serverLevel, Optional<ServerPlayer> target) {
        if (stateTicks <= AncientDragonAttackGeometry.STORM_CAGE_TARGET_LOCK_TICK && target.isPresent()) {
            ServerPlayer player = target.get();
            attackFocus = AncientDragonAttackGeometry.leadTarget(
                    player.position(), player.getDeltaMovement(), 5, 7.0D);
            stormStrikePositions.clear();
            stormStrikePositions.addAll(AncientDragonAttackGeometry.ringPositions(
                    attackFocus,
                    AncientDragonAttackGeometry.STORM_CAGE_RING_RADIUS_BLOCKS,
                    AncientDragonAttackGeometry.STORM_CAGE_PILLARS));
        }
        moveTowardFacing(
                attackFocus.add(0.0D, 24.0D, 0.0D),
                0.28D,
                attackFocus,
                10.0F,
                FlightEnvelope.ATTACK);
        if (stateTicks >= 20
                && stateTicks < AncientDragonAttackGeometry.STORM_CAGE_RING_STRIKE_TICK
                && stateTicks % 2 == 0) {
            spawnParticleRing(
                    serverLevel,
                    ParticleTypes.ELECTRIC_SPARK,
                    attackFocus.add(0.0D, 0.2D, 0.0D),
                    AncientDragonAttackGeometry.STORM_CAGE_RING_RADIUS_BLOCKS,
                    40);
            for (Vec3 pillar : stormStrikePositions) {
                spawnParticleRing(
                        serverLevel,
                        ParticleTypes.END_ROD,
                        pillar.add(0.0D, 0.25D, 0.0D),
                        AncientDragonAttackGeometry.STORM_CAGE_PILLAR_RADIUS_BLOCKS,
                        10);
            }
        }
        if (stateTicks == AncientDragonAttackGeometry.STORM_CAGE_RING_STRIKE_TICK) {
            serverLevel.playSound(
                    null, attackFocus.x, attackFocus.y, attackFocus.z,
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 5.5F, 0.7F);
            Set<UUID> damaged = new HashSet<>();
            for (Vec3 pillar : stormStrikePositions) {
                electricStrike(
                        serverLevel,
                        pillar,
                        Attack.STORM_CAGE,
                        AncientDragonAttackGeometry.STORM_CAGE_PILLAR_RADIUS_BLOCKS,
                        damaged);
            }
        }
        if (stateTicks == AncientDragonAttackGeometry.STORM_CAGE_CENTER_STRIKE_TICK) {
            electricStrike(
                    serverLevel,
                    attackFocus,
                    Attack.STORM_CAGE,
                    AncientDragonAttackGeometry.STORM_CAGE_CENTER_RADIUS_BLOCKS,
                    new HashSet<>());
        }
    }

    private void tickBiteHeavy(ServerLevel serverLevel) {
        stopFlight();
        updateCollisionParts(serverLevel);
        if (stateTicks >= 20 && stateTicks <= 34) {
            damagePlayersTouchingParts(
                    serverLevel,
                    Set.of(AncientDragonPartKind.HEAD, AncientDragonPartKind.NECK_FRONT),
                    Attack.BITE_HEAVY.baseDamage(),
                    1.0D,
                    0.3D);
            damagePlayersInBiteArc(serverLevel);
        }
    }

    private void tickWingSlam(ServerLevel serverLevel) {
        stopFlight();
        updateCollisionParts(serverLevel);
        if (stateTicks == 48) {
            DragonAtmosphereDispatcher.cue(this, serverLevel, DragonAtmosphereCue.WING_IMPACT, position());
            damagePlayersInWingShockwave(serverLevel);
        }
        if (stateTicks >= 34 && stateTicks <= 60) {
            damagePlayersTouchingParts(
                    serverLevel,
                    Set.of(
                            AncientDragonPartKind.PRIMARY_WING_L_INNER,
                            AncientDragonPartKind.PRIMARY_WING_L_MID,
                            AncientDragonPartKind.PRIMARY_WING_L_TIP,
                            AncientDragonPartKind.PRIMARY_WING_R_INNER,
                            AncientDragonPartKind.PRIMARY_WING_R_MID,
                            AncientDragonPartKind.PRIMARY_WING_R_TIP),
                    Attack.WING_SLAM.baseDamage(),
                    1.9D,
                    0.65D);
        }
    }

    private void tickRiftClaw(ServerLevel serverLevel, Optional<ServerPlayer> target) {
        stopFlight();
        if (stateTicks <= AncientDragonAttackGeometry.RIFT_CLAW_TARGET_LOCK_TICK && target.isPresent()) {
            ServerPlayer player = target.get();
            attackFocus = AncientDragonAttackGeometry.leadTarget(
                    player.position(), player.getDeltaMovement(), 7, 10.0D);
        }
        Vec3 origin = position().add(0.0D, 0.2D, 0.0D);
        Vec3 direction = attackFocus.subtract(origin);
        if (direction.horizontalDistanceSqr() <= 0.0001D) {
            direction = DragonQuaternion.fromMinecraftYaw(getYRot()).forward();
        }
        List<Vec3> rifts = AncientDragonAttackGeometry.spreadDirections(
                direction, AncientDragonAttackGeometry.RIFT_CLAW_SPREAD_RADIANS);
        if (stateTicks >= 20
                && stateTicks < AncientDragonAttackGeometry.RIFT_CLAW_IMPACT_TICK
                && stateTicks % 2 == 0) {
            double progress = (stateTicks - 20) / (double) (AncientDragonAttackGeometry.RIFT_CLAW_IMPACT_TICK - 20);
            for (Vec3 rift : rifts) {
                spawnRiftLine(
                        serverLevel,
                        origin,
                        rift,
                        AncientDragonAttackGeometry.RIFT_CLAW_LENGTH_BLOCKS * progress,
                        false);
            }
        }
        if (stateTicks == AncientDragonAttackGeometry.RIFT_CLAW_IMPACT_TICK) {
            serverLevel.playSound(
                    null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE,
                    SoundSource.HOSTILE, 4.0F, 0.68F);
            for (Vec3 rift : rifts) {
                strikeRift(serverLevel, origin, rift);
            }
        }
    }

    private void tickGroundStormBurst(ServerLevel serverLevel, List<ServerPlayer> participants) {
        stopFlight();
        List<ServerPlayer> pressureOrder = participants.stream().sorted(targetComparator()).toList();
        int pressureTargets = Math.min(
                AncientDragonEncounterRules.simultaneousPressureTargets(currentActiveParticipants),
                pressureOrder.size());
        if (AncientDragonAttackGeometry.stormTargetTracksPlayer(stateTicks)) {
            stormStrikePositions.clear();
            for (int index = 0; index < pressureTargets; index++) {
                stormStrikePositions.add(pressureOrder.get(index).position());
            }
        }
        if (stateTicks >= AncientDragonAttackGeometry.STORM_TELEGRAPH_START_TICK
                && stateTicks < AncientDragonAttackGeometry.STORM_STRIKE_TICK
                && stateTicks % 2 == 0) {
            double progress = AncientDragonAttackGeometry.stormTelegraphProgress(stateTicks);
            for (Vec3 strike : stormStrikePositions) {
                spawnStormTelegraph(serverLevel, strike, progress);
            }
        }
        if (stateTicks == AncientDragonAttackGeometry.STORM_STRIKE_TICK) {
            serverLevel.playSound(
                    null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.HOSTILE, 5.0F, 0.76F);
            DragonAtmosphereDispatcher.cue(this, serverLevel, DragonAtmosphereCue.STORM_STRIKE, position());
            Set<UUID> damaged = new HashSet<>();
            for (Vec3 strike : stormStrikePositions) {
                electricStrike(
                        serverLevel,
                        strike,
                        Attack.GROUND_STORM_BURST,
                        AncientDragonAttackGeometry.STORM_STRIKE_RADIUS_BLOCKS,
                        damaged);
            }
        }
    }

    private void tickGroundSolarBreath(ServerLevel serverLevel, List<ServerPlayer> participants) {
        stopFlight();
        updateCollisionParts(serverLevel);
        Vec3 beamStart = headAttackOrigin;
        Vec3 aimed = attackFocus.add(0.0D, 1.0D, 0.0D).subtract(beamStart);
        Vec3 direction = aimed.lengthSqr() > 0.0001D ? aimed.normalize() : headAttackDirection;
        Vec3 beamEnd = clipBeamToTerrain(serverLevel, beamStart, beamStart.add(direction.scale(96.0D)));
        if (stateTicks >= 28 && stateTicks < 48 && stateTicks % 2 == 0) {
            spawnBeamParticles(serverLevel, beamStart, beamEnd, 12, false);
        }
        if (stateTicks == 48) {
            DragonAtmosphereDispatcher.cue(this, serverLevel, DragonAtmosphereCue.SOLAR_IGNITION, beamStart);
        }
        if (stateTicks >= 48 && stateTicks <= 88 && stateTicks % 2 == 0) {
            spawnBeamParticles(serverLevel, beamStart, beamEnd, 24, true);
        }
        if (stateTicks >= 48 && stateTicks <= 88 && stateTicks % 12 == 0) {
            for (ServerPlayer player : participants) {
                if (playersHitByCurrentAttack.contains(player.getUUID())) {
                    continue;
                }
                if (distanceSquaredToSegment(
                                player.getBoundingBox().getCenter(), beamStart, beamEnd)
                        <= AncientDragonAttackGeometry.SOLAR_BEAM_RADIUS_BLOCKS
                                * AncientDragonAttackGeometry.SOLAR_BEAM_RADIUS_BLOCKS) {
                    if (hurtPlayer(serverLevel, player, Attack.GROUND_SOLAR_BREATH.baseDamage())) {
                        playersHitByCurrentAttack.add(player.getUUID());
                    }
                }
            }
        }
    }

    private void damagePlayersTouchingParts(
            ServerLevel serverLevel,
            Set<AncientDragonPartKind> damagingParts,
            float damage,
            double horizontalKnockback,
            double verticalKnockback) {
        for (ServerPlayer player : activeParticipants(serverLevel)) {
            if (playersHitByCurrentAttack.contains(player.getUUID())) {
                continue;
            }
            boolean intersects = false;
            for (AncientDragonPartKind kind : damagingParts) {
                AncientDragonPartEntity part = collisionParts.get(kind);
                if (part != null
                        && !part.isRemoved()
                        && part.getBoundingBox()
                                .inflate(AncientDragonAttackGeometry.MELEE_CONTACT_MARGIN_BLOCKS)
                                .intersects(player.getBoundingBox())
                        && attackPathClear(
                                serverLevel,
                                part.getBoundingBox().getCenter(),
                                player.getBoundingBox().getCenter())) {
                    intersects = true;
                    break;
                }
            }
            if (!intersects) {
                continue;
            }
            if (!hurtPlayer(serverLevel, player, damage)) {
                continue;
            }
            playersHitByCurrentAttack.add(player.getUUID());
            Vec3 horizontal = player.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
            if (horizontal.lengthSqr() > 0.0001D) {
                horizontal = horizontal.normalize();
                player.push(
                        horizontal.x * horizontalKnockback,
                        verticalKnockback,
                        horizontal.z * horizontalKnockback);
            }
        }
    }

    private void damagePlayersInBiteArc(ServerLevel serverLevel) {
        Vec3 start = position().add(0.0D, 2.5D, 0.0D);
        Vec3 end = headAttackOrigin.add(
                headAttackDirection.scale(AncientDragonAttackGeometry.BITE_FORWARD_REACH_BLOCKS));
        double radiusSquared = AncientDragonAttackGeometry.BITE_CONTACT_RADIUS_BLOCKS
                * AncientDragonAttackGeometry.BITE_CONTACT_RADIUS_BLOCKS;
        for (ServerPlayer player : activeParticipants(serverLevel)) {
            if (playersHitByCurrentAttack.contains(player.getUUID())
                    || distanceSquaredToSegment(player.getBoundingBox().getCenter(), start, end) > radiusSquared
                    || !attackPathClear(serverLevel, headAttackOrigin, player.getBoundingBox().getCenter())
                    || !hurtPlayer(serverLevel, player, Attack.BITE_HEAVY.baseDamage())) {
                continue;
            }
            playersHitByCurrentAttack.add(player.getUUID());
            Vec3 launch = player.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
            if (launch.lengthSqr() > 0.0001D) {
                launch = launch.normalize();
                player.push(launch.x, 0.3D, launch.z);
            }
        }
    }

    private void damagePlayersInWingShockwave(ServerLevel serverLevel) {
        Vec3 origin = position();
        for (ServerPlayer player : activeParticipants(serverLevel)) {
            Vec3 target = player.getBoundingBox().getCenter();
            if (playersHitByCurrentAttack.contains(player.getUUID())
                    || !AncientDragonAttackGeometry.groundShockwaveContact(
                            origin,
                            target,
                            AncientDragonAttackGeometry.WING_SLAM_SHOCKWAVE_RADIUS_BLOCKS,
                            AncientDragonAttackGeometry.WING_SLAM_VERTICAL_REACH_BLOCKS)
                    || !attackPathClear(serverLevel, origin.add(0.0D, 1.0D, 0.0D), target)
                    || !hurtPlayer(serverLevel, player, Attack.WING_SLAM.baseDamage())) {
                continue;
            }
            playersHitByCurrentAttack.add(player.getUUID());
            Vec3 launch = player.position().subtract(origin).multiply(1.0D, 0.0D, 1.0D);
            if (launch.lengthSqr() > 0.0001D) {
                launch = launch.normalize();
                player.push(launch.x * 1.9D, 0.65D, launch.z * 1.9D);
            }
        }
    }

    private boolean attackPathClear(ServerLevel serverLevel, Vec3 start, Vec3 end) {
        HitResult hit = serverLevel.clip(
                new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(start) + 0.25D >= end.distanceToSqr(start);
    }

    public boolean awaken() {
        if (!(level() instanceof ServerLevel serverLevel) || bossState != AncientDragonBossState.DORMANT) {
            return false;
        }
        awaken(serverLevel);
        return true;
    }

    private void awaken(ServerLevel serverLevel) {
        disengagedTicks = 0;
        List<ServerPlayer> participants = activeParticipants(serverLevel);
        participantLedger.tick(participants.stream().map(ServerPlayer::getUUID).toList(), tickCount);
        scaledParticipants = participantLedger.maxParticipantsSeen();
        currentActiveParticipants = participants.size();
        applyParticipantScaling(scaledParticipants, true);
        stability.resetForAirCycle(scaledParticipants);
        initialTakeoff = true;
        airAttacksThisCycle = 0;
        bossEvent.setVisible(true);
        bossState = AncientDragonBossState.AWAKENING;
        if (!debugEncounter) {
            AncientDragonEncounterData.get(serverLevel).updateStage(getUUID(), Stage.ACTIVE, List.of());
            for (ServerPlayer participant : participants) {
                DragonChronicleService.grant(participant, DragonChronicleEvent.DRAGON_AWAKENED);
            }
        }
        stateTicks = 0;
        publishPersistentAnimation(AncientDragonAnimations.AWAKEN_INTRO);
        serverLevel.playSound(
                null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4.0F, 0.62F);
        DragonAtmosphereDispatcher.snapshot(this, serverLevel);
        DragonAtmosphereDispatcher.cue(this, serverLevel, DragonAtmosphereCue.AWAKEN, position());
    }

    private void beginTakeoff(ServerLevel serverLevel, boolean initial) {
        AncientDragonBossState previousState = bossState;
        bossState = AncientDragonBossState.TAKEOFF;
        stateTicks = 0;
        initialTakeoff = initial;
        airAttacksThisCycle = 0;
        forcedLandingPending = false;
        forcedLandingDelayTicks = 0;
        if (previousState != AncientDragonBossState.PERCHED) {
            stability.resetForAirCycle(scaledParticipants);
        }
        takeoffTarget = initial || position().distanceToSqr(encounterOrigin) <= 32.0D * 32.0D
                ? SacredMountainFlightPath.takeoffWorldPosition(encounterOrigin, encounterQuarterTurns)
                : position().add(0.0D, 80.0D, 0.0D);
        patrolWaypointIndex = SacredMountainFlightPath.ASSEMBLY_INDEX;
        publishPersistentAnimation(AncientDragonAnimations.TAKEOFF);
        serverLevel.sendParticles(
                ParticleTypes.CLOUD, getX(), getY(), getZ(), 120, 7.0D, 2.0D, 7.0D, 0.15D);
        DragonAtmosphereDispatcher.snapshot(this, serverLevel);
        DragonAtmosphereDispatcher.cue(this, serverLevel, DragonAtmosphereCue.TAKEOFF, position());
    }

    private void beginDeparture() {
        bossState = AncientDragonBossState.DEPARTING;
        stateTicks = 0;
        disengagedTicks = 0;
        patrolWaypointIndex = SacredMountainFlightPath.ASSEMBLY_INDEX;
        publishPersistentAnimation(AncientDragonAnimations.FLY_CRUISE);
    }

    private void beginCruising() {
        bossState = AncientDragonBossState.CRUISING;
        stateTicks = 0;
        disengagedTicks = 0;
        initialTakeoff = false;
        attackCooldownTicks = AncientDragonEncounterRules.attackCooldownTicks(currentActiveParticipants);
        publishPersistentAnimation(AncientDragonAnimations.FLY_CRUISE);
        applyPendingPhase();
    }

    private void beginCruisingWithoutCooldown() {
        bossState = AncientDragonBossState.CRUISING;
        stateTicks = 0;
        attackCooldownTicks = 0;
        publishPersistentAnimation(AncientDragonAnimations.FLY_CRUISE);
    }

    private void beginPerchApproach() {
        bossState = AncientDragonBossState.PERCH_APPROACH;
        stateTicks = 0;
        perchApproachStage = 0;
        publishPersistentAnimation(AncientDragonAnimations.FLY_CRUISE);
    }

    private void beginPerched() {
        SacredMountainFlightPath.Perch perch = SacredMountainFlightPath.perchFor(phase, encounterQuarterTurns);
        Vec3 root = encounterOrigin.add(perch.rootOffset());
        setPos(root.x, root.y, root.z);
        setYRot(perch.yaw());
        setXRot(0.0F);
        setYBodyRot(perch.yaw());
        setYHeadRot(perch.yaw());
        setDeltaMovement(Vec3.ZERO);
        flightController.reset(DragonQuaternion.fromMinecraftYaw(perch.yaw()), Vec3.ZERO);
        bossState = AncientDragonBossState.PERCHED;
        stateTicks = 0;
        perchPending = false;
        publishPersistentAnimation(AncientDragonAnimations.COMBAT_STAND);
        if (phase == Phase.STORM) {
            triggerAnimation(AncientDragonAnimations.ROAR_STORM);
        } else if (phase == Phase.SOLAR) {
            triggerAnimation(AncientDragonAnimations.SOLAR_BREATH);
        }
    }

    private void beginAttack(ServerPlayer target, AttackDomain domain, List<ServerPlayer> participants) {
        disengagedTicks = 0;
        AncientDragonAttackDirector.Decision decision = attackDirector.choose(
                attackDirectorContext(domain, participants));
        lastDirectorScores = decision.scores();
        attackSequence++;
        startAttack(decision.attack(), target, domain);
    }

    private void startAttack(Attack attack, ServerPlayer target, AttackDomain domain) {
        currentAttack = attack;
        currentAttackDomain = domain;
        attackFocus = target.position();
        stormStrikePositions.clear();
        playersHitByCurrentAttack.clear();
        tailSweepHitPlayers.clear();
        snapshotTailPartCenters();
        solarBreathStarted = false;
        solarApproachTicks = 0;
        solarSweepCenter = target.position();
        solarPeakHold = SacredMountainFlightPath.nearestPeakHoldWorldPosition(
                encounterOrigin, position(), encounterQuarterTurns);
        bossState = AncientDragonBossState.ATTACKING;
        stateTicks = 0;
        if (domain == AttackDomain.GROUND) {
            faceGroundAttackTarget(target.position());
        }
        if (currentAttack != Attack.SOLAR_BREATH) {
            triggerAnimation(animationForAttack(currentAttack));
        }
    }

    private void faceGroundAttackTarget(Vec3 target) {
        Vec3 horizontal = target.subtract(position()).multiply(1.0D, 0.0D, 1.0D);
        if (horizontal.lengthSqr() <= 0.0001D) {
            return;
        }
        DragonQuaternion facing = DragonQuaternion.lookRotation(
                horizontal.normalize(), new Vec3(0.0D, 1.0D, 0.0D));
        float yaw = facing.minecraftYawDegrees();
        setYRot(yaw);
        setXRot(0.0F);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        flightController.reset(facing, Vec3.ZERO);
    }

    private AncientDragonAttackDirector.Context attackDirectorContext(
            AttackDomain domain, List<ServerPlayer> participants) {
        int close = 0;
        int exposed = 0;
        int clustered = 0;
        int rear = 0;
        double distanceTotal = 0.0D;
        Vec3 forward = DragonQuaternion.fromMinecraftYaw(getYRot()).forward();
        for (ServerPlayer player : participants) {
            double distance = distanceTo(player);
            distanceTotal += distance;
            if (distance <= 42.0D) {
                close++;
            }
            if (hasLineOfSight(player)) {
                exposed++;
            }
            Vec3 direction = player.position().subtract(position());
            if (direction.lengthSqr() > 0.0001D && direction.normalize().dot(forward) < -0.25D) {
                rear++;
            }
            for (ServerPlayer other : participants) {
                if (other != player && other.position().distanceToSqr(player.position()) <= 24.0D * 24.0D) {
                    clustered++;
                    break;
                }
            }
        }
        double meanDistance = participants.isEmpty() ? 0.0D : distanceTotal / participants.size();
        return new AncientDragonAttackDirector.Context(
                phase, domain, clustered, close, rear, exposed, meanDistance);
    }

    private void beginCombatReturning() {
        currentAttack = null;
        flightController.cancelManeuver();
        forcedLandingPending = false;
        forcedLandingDelayTicks = 0;
        bossState = AncientDragonBossState.COMBAT_RETURNING;
        stateTicks = 0;
        publishPersistentAnimation(AncientDragonAnimations.FLY_CRUISE);
    }

    private void beginCombatApproach() {
        approachWaypointIndex = 0;
        bossState = AncientDragonBossState.COMBAT_APPROACHING;
        stateTicks = 0;
        publishPersistentAnimation(AncientDragonAnimations.FLY_CRUISE);
        triggerAnimation(AncientDragonAnimations.FLY_DESCEND);
    }

    private void beginCombatLanding() {
        flightController.cancelManeuver();
        flightController.reset(flightController.orientation(), Vec3.ZERO);
        setDeltaMovement(Vec3.ZERO);
        bossState = AncientDragonBossState.COMBAT_LANDING;
        stateTicks = 0;
        publishPersistentAnimation(AncientDragonAnimations.COMBAT_IDLE);
        triggerAnimation(AncientDragonAnimations.LAND);
    }

    private void finishCombatLanding() {
        Vec3 platform = SacredMountainFlightPath.combatPlatformWorldPosition(
                encounterOrigin, encounterQuarterTurns);
        setPos(platform.x, platform.y, platform.z);
        noPhysics = false;
        alignRoostFacing();
        groundCombatTicksRemaining = phase.groundWindowTicks();
        beginGroundCombat();
    }

    private void beginGroundCombat() {
        bossState = AncientDragonBossState.GROUND_COMBAT;
        stateTicks = 0;
        currentAttack = null;
        currentAttackDomain = AttackDomain.GROUND;
        attackCooldownTicks = Math.max(
                28, AncientDragonEncounterRules.attackCooldownTicks(currentActiveParticipants) / 2);
        publishPersistentAnimation(AncientDragonAnimations.COMBAT_IDLE);
        applyPendingPhase();
    }

    private void beginReturning() {
        currentAttack = null;
        disengagedTicks = 0;
        flightController.cancelManeuver();
        bossState = AncientDragonBossState.RETURNING;
        stateTicks = 0;
        publishPersistentAnimation(AncientDragonAnimations.FLY_CRUISE);
    }

    private void beginApproach() {
        approachWaypointIndex = 0;
        bossState = AncientDragonBossState.APPROACHING;
        stateTicks = 0;
        publishPersistentAnimation(AncientDragonAnimations.FLY_CRUISE);
        triggerAnimation(AncientDragonAnimations.FLY_DESCEND);
    }

    private void beginLanding() {
        flightController.cancelManeuver();
        bossState = AncientDragonBossState.LANDING;
        stateTicks = 0;
        publishPersistentAnimation(AncientDragonAnimations.COMBAT_IDLE);
        triggerAnimation(AncientDragonAnimations.LAND);
    }

    private void finishLanding() {
        setPos(encounterOrigin.x, encounterOrigin.y, encounterOrigin.z);
        noPhysics = false;
        alignRoostFacing();
        currentAttack = null;
        attackFocus = Vec3.ZERO;
        stormStrikePositions.clear();
        participantLedger.clearForDormancy();
        recentAttackerEntityId = -1;
        recentAttackerTick = Integer.MIN_VALUE;
        recentAttackerLastKnownPosition = Vec3.ZERO;
        phase = Phase.MOUNTAIN;
        pendingPhase = null;
        scaledParticipants = 1;
        currentActiveParticipants = 0;
        applyParticipantScaling(1, true);
        attackCooldownTicks = 0;
        attackSequence = 0;
        patrolWaypointIndex = SacredMountainFlightPath.ASSEMBLY_INDEX;
        approachWaypointIndex = 0;
        disengagedTicks = 0;
        airAttacksThisCycle = 0;
        groundCombatTicksRemaining = 0;
        forcedLandingPending = false;
        forcedLandingDelayTicks = 0;
        perchPending = false;
        initialTakeoff = true;
        bossState = AncientDragonBossState.DORMANT;
        stateTicks = 0;
        attentionController.reset();
        attentionStep = new DragonAttentionController.AttentionStep(
                0.0D, 0.0D, AttentionMode.SCAN, -1, encounterOrigin, 0.0D);
        bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
        bossEvent.setVisible(false);
        stabilityEvent.setVisible(false);
        if (!debugEncounter && level() instanceof ServerLevel serverLevel) {
            AncientDragonEncounterData.get(serverLevel).updateStage(getUUID(), Stage.DORMANT, List.of());
        }
        poseDynamics.reset(DragonStagedPose.matching(authoritativePoseState()));
        synchronizePoseState();
        if (level() instanceof ServerLevel serverLevel) {
            DragonAtmosphereDispatcher.snapshot(this, serverLevel);
        }
        publishPersistentAnimation(AncientDragonAnimations.DORMANT_HOLD);
    }

    private void updatePhase(ServerLevel serverLevel) {
        if (bossState == AncientDragonBossState.DORMANT
                || bossState == AncientDragonBossState.AWAKENING
                || bossState == AncientDragonBossState.TAKEOFF
                || bossState == AncientDragonBossState.DEPARTING
                || bossState == AncientDragonBossState.RETURNING
                || bossState == AncientDragonBossState.APPROACHING
                || bossState == AncientDragonBossState.LANDING
                || bossState == AncientDragonBossState.DEATH_RETURNING
                || bossState == AncientDragonBossState.DYING
                || bossState == AncientDragonBossState.CORPSE) {
            return;
        }
        Phase newPhase = AncientDragonEncounterRules.phaseFor(getHealth(), getMaxHealth());
        if (newPhase == phase) {
            return;
        }
        pendingPhase = newPhase;
        if (bossState == AncientDragonBossState.CRUISING
                || bossState == AncientDragonBossState.GROUND_COMBAT
                || bossState == AncientDragonBossState.PERCHED) {
            applyPendingPhase();
        }
    }

    private void applyPendingPhase() {
        if (pendingPhase == null || pendingPhase == phase) {
            pendingPhase = null;
            return;
        }
        phase = pendingPhase;
        pendingPhase = null;
        attackCooldownTicks = Math.max(attackCooldownTicks, 60);
        if (phase == Phase.STORM) {
            bossEvent.setColor(BossEvent.BossBarColor.BLUE);
            triggerAnimation(AncientDragonAnimations.ROAR_STORM);
        } else if (phase == Phase.SOLAR) {
            bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
            triggerAnimation(bossState == AncientDragonBossState.GROUND_COMBAT
                    ? AncientDragonAnimations.SOLAR_BREATH
                    : AncientDragonAnimations.AERIAL_SOLAR_BREATH);
        }
        if (level() instanceof ServerLevel serverLevel) {
            DragonAtmosphereDispatcher.snapshot(this, serverLevel);
            if (phase == Phase.STORM) {
                DragonAtmosphereDispatcher.cue(this, serverLevel, DragonAtmosphereCue.PHASE_STORM, position());
            } else if (phase == Phase.SOLAR) {
                DragonAtmosphereDispatcher.cue(this, serverLevel, DragonAtmosphereCue.PHASE_SOLAR, position());
            }
        }
    }

    private void applyParticipantScaling(int participants, boolean restoreToFullHealth) {
        float oldMaximum = Math.max(getMaxHealth(), 1.0F);
        float oldFraction = restoreToFullHealth ? 1.0F : Math.clamp(getHealth() / oldMaximum, 0.0F, 1.0F);
        AttributeInstance maximumHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maximumHealth == null) {
            throw new IllegalStateException("Ancient Dragon is missing its maximum-health attribute");
        }
        maximumHealth.setBaseValue(AncientDragonEncounterRules.maxHealth(participants));
        setHealth(getMaxHealth() * oldFraction);
        participantScalingInitialized = true;
    }

    /**
     * Brings dragons saved under an earlier balance version onto the current health curve while
     * preserving their current health percentage. New entities already have this base value, so
     * the check is a one-time no-op for the normal path.
     */
    private void ensureParticipantScaling() {
        if (participantScalingInitialized) {
            return;
        }
        applyParticipantScaling(scaledParticipants, false);
    }

    private void updateParticipantRuntime(ServerLevel serverLevel) {
        if (bossState == AncientDragonBossState.DORMANT
                || bossState == AncientDragonBossState.DYING
                || bossState == AncientDragonBossState.CORPSE) {
            currentActiveParticipants = 0;
            return;
        }
        List<ServerPlayer> active = activeParticipants(serverLevel);
        currentActiveParticipants = active.size();
        participantLedger.tick(active.stream().map(ServerPlayer::getUUID).toList(), tickCount);
        disengagedTicks = participantLedger.emptyTicks();
        int lockedScaling = participantLedger.maxParticipantsSeen();
        if (lockedScaling > scaledParticipants) {
            scaledParticipants = lockedScaling;
            applyParticipantScaling(scaledParticipants, false);
            stability.rescaleMaximum(scaledParticipants);
        }
    }

    private List<ServerPlayer> activeParticipants(ServerLevel serverLevel) {
        if (arenaVolume == null) {
            arenaVolume = AncientDragonArenaVolume.resolve(serverLevel, getUUID(), encounterOrigin);
        }
        List<ServerPlayer> participants = new ArrayList<>(serverLevel.getPlayers(player ->
                canDisturb(player) && arenaVolume.contains(player.position())));
        participants.sort(Comparator.comparing(ServerPlayer::getUUID));
        return participants;
    }

    private boolean canDisturb(ServerPlayer player) {
        return AncientDragonPerceptionRules.canParticipate(player.isAlive(), player.isSpectator());
    }

    private Optional<ServerPlayer> selectTarget(List<ServerPlayer> participants) {
        Map<UUID, ServerPlayer> byId = new java.util.LinkedHashMap<>();
        for (ServerPlayer participant : participants) {
            byId.put(participant.getUUID(), participant);
        }
        return participantLedger.selectTarget(byId.keySet(), tickCount).map(byId::get);
    }

    private Comparator<ServerPlayer> targetComparator() {
        return Comparator
                .<ServerPlayer>comparingDouble(player -> participantLedger.recentThreat(player.getUUID(), tickCount))
                .reversed()
                .thenComparing(Comparator
                        .<ServerPlayer>comparingDouble(player -> participantLedger.lifetimeDamage(player.getUUID()))
                        .reversed())
                .thenComparing(ServerPlayer::getUUID);
    }

    private void flySacredMountainPatrol() {
        SacredMountainFlightPath.Waypoint waypoint = SacredMountainFlightPath.waypoint(
                patrolWaypointIndex, encounterQuarterTurns);
        Vec3 destination = encounterOrigin.add(waypoint.offset());
        moveToward(destination, waypoint.maximumSpeed(), FlightEnvelope.CRUISE);
        if (position().distanceToSqr(destination)
                <= SacredMountainFlightPath.ARRIVAL_RADIUS * SacredMountainFlightPath.ARRIVAL_RADIUS) {
            patrolWaypointIndex = SacredMountainFlightPath.nextIndex(patrolWaypointIndex);
        }
    }

    private void moveToward(Vec3 destination, double speed, FlightEnvelope envelope) {
        destination = constrainFlightDestination(destination);
        FlightIntent intent = FlightIntent.route(
                destination, speed, attentionStep.bodyAssistYawRadians(), envelope);
        applyFlightStep(flightController.tick(position(), intent));
    }

    private void moveTowardFacing(
            Vec3 destination,
            double speed,
            Vec3 facingTarget,
            float maximumFacingBiasDegrees,
            FlightEnvelope envelope) {
        destination = constrainFlightDestination(destination);
        FlightIntent intent = FlightIntent.facing(
                destination,
                speed,
                facingTarget,
                Math.toRadians(maximumFacingBiasDegrees),
                envelope);
        applyFlightStep(flightController.tick(position(), intent));
    }

    private void applyFlightStep(DragonFlightController.FlightStep step) {
        Vec3 movement = constrainFlightMovement(step.movement());
        move(MoverType.SELF, movement);
        setDeltaMovement(movement);
        applyVanillaRotationCompatibility(step.orientation());
    }

    private Vec3 constrainFlightDestination(Vec3 destination) {
        return isAirborneBossState()
                ? SacredMountainFlightPath.clampToFlightBounds(
                        destination, encounterOrigin, encounterQuarterTurns)
                : destination;
    }

    private Vec3 constrainFlightMovement(Vec3 movement) {
        if (!isAirborneBossState()
                || !SacredMountainFlightPath.isInsideFlightBounds(
                        position(), encounterOrigin, encounterQuarterTurns)) {
            // Old saves can resume outside the new box and fly back toward a clamped destination
            // without a visible one-tick teleport.
            return movement;
        }
        Vec3 bounded = SacredMountainFlightPath.clampToFlightBounds(
                position().add(movement), encounterOrigin, encounterQuarterTurns);
        return bounded.subtract(position());
    }

    private void initializeFlightControllerIfNeeded() {
        if (flightControllerInitialized) {
            return;
        }
        flightController.reset(DragonQuaternion.fromMinecraftYaw(getYRot()), getDeltaMovement());
        flightControllerInitialized = true;
        poseDynamics.reset(DragonStagedPose.matching(authoritativePoseState()));
        synchronizePoseState();
    }

    private void updateAttention(ServerLevel serverLevel) {
        List<DragonAttentionController.Candidate> candidates = new ArrayList<>();
        List<ServerPlayer> participants = activeParticipants(serverLevel);
        Optional<ServerPlayer> threat = selectTarget(participants);
        if (bossState == AncientDragonBossState.ATTACKING) {
            int attackTargetId = threat.map(Entity::getId).orElse(-1);
            candidates.add(new DragonAttentionController.Candidate(
                    attackTargetId, attackFocus, AttentionMode.ATTACK_FOCUS, 1000.0D, true));
        }
        if (tickCount - recentAttackerTick <= 100) {
            Entity recentAttacker = serverLevel.getEntity(recentAttackerEntityId);
            if (recentAttacker != null && recentAttacker.isAlive()) {
                boolean visible = hasLineOfSight(recentAttacker);
                if (visible) {
                    recentAttackerLastKnownPosition = recentAttacker.getEyePosition();
                }
                candidates.add(new DragonAttentionController.Candidate(
                        recentAttacker.getId(),
                        recentAttackerLastKnownPosition,
                        AttentionMode.RECENT_ATTACKER,
                        700.0D,
                        visible || tickCount == recentAttackerTick));
            }
        }
        threat.ifPresent(player -> candidates.add(new DragonAttentionController.Candidate(
                player.getId(),
                player.getEyePosition(),
                AttentionMode.THREAT,
                400.0D + Math.min(
                        80.0D, participantLedger.recentThreat(player.getUUID(), tickCount) * 0.05D),
                AncientDragonPerceptionRules.sensesThreat(true, hasLineOfSight(player)))));
        serverLevel.getPlayers(player -> player.isAlive()
                        && !player.isSpectator()
                        && player.distanceToSqr(this) <= OBSERVER_ATTENTION_RANGE * OBSERVER_ATTENTION_RANGE)
                .stream()
                .sorted(Comparator.<ServerPlayer>comparingDouble(this::distanceToSqr)
                        .thenComparing(ServerPlayer::getUUID))
                .forEach(player -> candidates.add(new DragonAttentionController.Candidate(
                        player.getId(),
                        player.getEyePosition(),
                        AttentionMode.OBSERVER,
                        200.0D + (OBSERVER_ATTENTION_RANGE
                                - Math.min(OBSERVER_ATTENTION_RANGE, distanceTo(player))) * 0.1D,
                        hasLineOfSight(player))));

        attentionStep = attentionController.tick(
                tickCount,
                getUUID().getLeastSignificantBits(),
                position().add(0.0D, 4.0D, 0.0D),
                flightController.orientation(),
                candidates,
                attentionOverride);
        if (flightController.maneuver().suppressesAttentionBodyAssist()) {
            double progress = flightController.maneuverTick()
                    / (double) Math.max(1, flightController.maneuver().durationTicks());
            double recoveryWeight = Math.max(0.0D, 1.0D - progress);
            attentionStep = new DragonAttentionController.AttentionStep(
                    Math.clamp(attentionStep.yawRadians(), Math.toRadians(-60.0D), Math.toRadians(60.0D))
                            * recoveryWeight,
                    Math.clamp(attentionStep.pitchRadians(), Math.toRadians(-35.0D), Math.toRadians(35.0D))
                            * recoveryWeight,
                    attentionStep.mode(),
                    attentionStep.targetEntityId(),
                    attentionStep.lookPosition(),
                    0.0D);
        }
    }

    private DragonPoseState authoritativePoseState() {
        boolean airborne = isAirborneBossState();
        DragonQuaternion orientation = airborne
                ? flightController.orientation()
                : DragonQuaternion.fromMinecraftYaw(getYRot());
        Curve torso = airborne ? flightController.torsoCurve() : Curve.ZERO;
        Curve tail = airborne ? flightController.tailCurve() : Curve.ZERO;
        boolean tracksTarget = AncientDragonPerceptionRules.tracksTargetPose(bossState);
        double attentionYaw = tracksTarget ? attentionStep.yawRadians() : 0.0D;
        double attentionPitch = tracksTarget ? attentionStep.pitchRadians() : 0.0D;
        Maneuver maneuver = airborne ? flightController.maneuver() : Maneuver.NONE;
        return new DragonPoseState(
                orientation,
                torso,
                tail,
                attentionYaw,
                attentionPitch,
                attentionStep.mode(),
                attentionStep.targetEntityId(),
                maneuver);
    }

    private boolean isAirborneBossState() {
        return bossState == AncientDragonBossState.TAKEOFF
                || bossState == AncientDragonBossState.DEPARTING
                || bossState == AncientDragonBossState.CRUISING
                || bossState == AncientDragonBossState.PERCH_APPROACH
                || bossState == AncientDragonBossState.ATTACKING
                && currentAttackDomain == AttackDomain.AIR
                || bossState == AncientDragonBossState.COMBAT_RETURNING
                || bossState == AncientDragonBossState.COMBAT_APPROACHING
                || bossState == AncientDragonBossState.COMBAT_LANDING
                || bossState == AncientDragonBossState.DEATH_RETURNING
                || bossState == AncientDragonBossState.RETURNING
                || bossState == AncientDragonBossState.APPROACHING
                || bossState == AncientDragonBossState.LANDING;
    }


    private void advanceAndSynchronizePoseState() {
        DragonPoseState pose = authoritativePoseState();
        poseDynamics.advance(pose);
        synchronizePoseState(pose, poseDynamics.snapshot());
    }

    private void synchronizePoseState() {
        synchronizePoseState(authoritativePoseState(), poseDynamics.snapshot());
    }

    private void synchronizePoseState(DragonPoseState pose, DragonStagedPose staged) {
        entityData.set(DATA_ORIENTATION_X, (float) pose.orientation().x());
        entityData.set(DATA_ORIENTATION_Y, (float) pose.orientation().y());
        entityData.set(DATA_ORIENTATION_Z, (float) pose.orientation().z());
        entityData.set(DATA_ORIENTATION_W, (float) pose.orientation().w());
        entityData.set(DATA_TORSO_PITCH, (float) pose.torso().pitchRadians());
        entityData.set(DATA_TORSO_YAW, (float) pose.torso().yawRadians());
        entityData.set(DATA_TORSO_ROLL, (float) pose.torso().rollRadians());
        entityData.set(DATA_TAIL_PITCH, (float) pose.tail().pitchRadians());
        entityData.set(DATA_TAIL_YAW, (float) pose.tail().yawRadians());
        entityData.set(DATA_TAIL_ROLL, (float) pose.tail().rollRadians());
        entityData.set(DATA_ATTENTION_YAW, (float) pose.attentionYawRadians());
        entityData.set(DATA_ATTENTION_PITCH, (float) pose.attentionPitchRadians());
        entityData.set(DATA_ATTENTION_MODE, (byte) pose.attentionMode().ordinal());
        entityData.set(DATA_ATTENTION_TARGET, pose.attentionTargetEntityId());
        entityData.set(DATA_MANEUVER, (byte) pose.maneuver().ordinal());
        for (int index = 0; index < DATA_STAGED_NECK.size(); index++) {
            entityData.set(DATA_STAGED_NECK.get(index), staged.packedNeck(index));
        }
        for (int index = 0; index < DATA_STAGED_TAIL.size(); index++) {
            entityData.set(DATA_STAGED_TAIL.get(index), staged.packedTail(index));
        }
    }

    private void applyVanillaRotationCompatibility(DragonQuaternion orientation) {
        setYRot(orientation.minecraftYawDegrees());
        setXRot(orientation.minecraftPitchDegrees());
        setYBodyRot(getYRot());
        setYHeadRot(getYRot());
    }

    private void alignRoostFacing() {
        float yaw = SacredMountainFlightPath.roostYaw(encounterQuarterTurns);
        setYRot(yaw);
        setXRot(0.0F);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        setDeltaMovement(Vec3.ZERO);
        flightController.reset(DragonQuaternion.fromMinecraftYaw(yaw), Vec3.ZERO);
    }

    public boolean requestManeuver(Maneuver maneuver) {
        if (!(level() instanceof ServerLevel serverLevel)
                || maneuver == null
                || maneuver == Maneuver.NONE
                || !maneuverAllowedByState()
                || !hasManeuverClearance(serverLevel, maneuver)) {
            return false;
        }
        if (maneuver.isLateral() && flightController.speed() < DragonFlightController.MINIMUM_ROLL_SPEED) {
            return false;
        }
        return flightController.startManeuver(maneuver);
    }

    public void cancelManeuver() {
        flightController.cancelManeuver();
        synchronizePoseState();
    }

    private boolean maneuverAllowedByState() {
        if (bossState == AncientDragonBossState.CRUISING) {
            return true;
        }
        return bossState == AncientDragonBossState.ATTACKING
                && currentAttack == Attack.DIVE_STRIKE
                && stateTicks >= 68;
    }

    private boolean hasManeuverClearance(ServerLevel serverLevel, Maneuver maneuver) {
        if (maneuver == Maneuver.ROLL_LEFT || maneuver == Maneuver.ROLL_RIGHT) {
            return serverLevel.noCollision(this, getBoundingBox().inflate(12.0D).expandTowards(0.0D, -40.0D, 0.0D));
        }
        if (maneuver == Maneuver.EVADE_LEFT || maneuver == Maneuver.EVADE_RIGHT) {
            return serverLevel.noCollision(this, getBoundingBox().inflate(8.0D).expandTowards(0.0D, -20.0D, 0.0D));
        }
        if (maneuver == Maneuver.PULL_UP) {
            return serverLevel.noCollision(this, getBoundingBox().inflate(12.0D).expandTowards(0.0D, 40.0D, 0.0D));
        }
        if (maneuver != Maneuver.LOOP
                || !serverLevel.noCollision(
                        this, getBoundingBox().inflate(16.0D).expandTowards(0.0D, 80.0D, 0.0D))) {
            return false;
        }
        Vec3 forward = flightController.orientation().forward();
        Vec3 up = flightController.orientation().up();
        for (int sample = 1; sample <= 12; sample++) {
            double angle = sample * (Math.PI * 2.0D / 12.0D);
            Vec3 offset = forward.scale(Math.sin(angle) * 18.0D)
                    .add(up.scale((1.0D - Math.cos(angle)) * 18.0D));
            if (!serverLevel.noCollision(this, getBoundingBox().move(offset).inflate(4.0D))) {
                return false;
            }
        }
        return true;
    }

    public void setAttentionOverride(DragonAttentionController.AttentionOverride override) {
        attentionOverride = override == null ? DragonAttentionController.AttentionOverride.NONE : override;
        if (attentionOverride == DragonAttentionController.AttentionOverride.CLEAR) {
            attentionController.reset();
        }
    }

    private void ensureCollisionParts(ServerLevel serverLevel) {
        for (AncientDragonPartKind kind : AncientDragonPartKind.values()) {
            AncientDragonPartEntity existing = collisionParts.get(kind);
            if (existing != null && !existing.isRemoved()) {
                continue;
            }
            AncientDragonPartEntity part =
                    new AncientDragonPartEntity(AncientDragonEntities.ANCIENT_DRAGON_PART, serverLevel);
            part.attach(this, kind);
            part.setPos(position());
            if (!serverLevel.addFreshEntity(part)) {
                throw new IllegalStateException("Could not spawn Ancient Dragon collision part " + kind.serializedName());
            }
            collisionParts.put(kind, part);
        }
    }

    /**
     * Keeps a short rolling ribbon of Enderman-style portal particles behind the flying dragon.
     * Vanilla portal particles have their own motion/lifetime, so each historical point is
     * re-emitted until it is 110 ticks old; the visible route therefore lasts about 5.5 seconds
     * without adding a persistent world entity or changing gameplay collision.
     */
    private void tickFlightTrail(ServerLevel serverLevel) {
        if (isAirborneBossState() && tickCount % FLIGHT_TRAIL_SAMPLE_INTERVAL_TICKS == 0) {
            flightTrail.addLast(new FlightTrailSample(
                    position().add(0.0D, AncientDragonScale.blocks(4.0D), 0.0D), tickCount));
        }
        while (!flightTrail.isEmpty()
                && tickCount - flightTrail.peekFirst().createdTick() > FLIGHT_TRAIL_DURATION_TICKS) {
            flightTrail.removeFirst();
        }
        if (flightTrail.isEmpty() || tickCount % FLIGHT_TRAIL_SAMPLE_INTERVAL_TICKS != 0) {
            return;
        }
        for (FlightTrailSample sample : flightTrail) {
            int age = tickCount - sample.createdTick();
            // Let the oldest end dissipate first so the tail remains readable instead of becoming a solid wall.
            if (age > FLIGHT_TRAIL_DURATION_TICKS - 24 && tickCount % 8 != 0) {
                continue;
            }
            Vec3 point = sample.position();
            serverLevel.sendParticles(
                    ParticleTypes.PORTAL,
                    point.x,
                    point.y,
                    point.z,
                    1,
                    0.42D,
                    0.72D,
                    0.42D,
                    0.014D);
        }
    }

    /** Emits a restrained phase-coloured body aura plus a rotating enchantment-glyph rune halo. */
    private void tickDragonAura(ServerLevel serverLevel) {
        if (bossState == AncientDragonBossState.DORMANT
                || bossState == AncientDragonBossState.DYING
                || bossState == AncientDragonBossState.CORPSE
                || tickCount % 4 != 0) {
            return;
        }
        Vec3 bodyCenter = position().add(0.0D, AncientDragonScale.blocks(4.2D), 0.0D);
        double horizontalSpread = AncientDragonScale.blocks(5.8D);
        double verticalSpread = AncientDragonScale.blocks(2.5D);
        // Purple portal motes make the aura feel tied to the trail rather than a generic fire effect.
        serverLevel.sendParticles(
                ParticleTypes.PORTAL,
                bodyCenter.x,
                bodyCenter.y,
                bodyCenter.z,
                4,
                horizontalSpread,
                verticalSpread,
                horizontalSpread,
                0.012D);
        // ENCHANT uses the vanilla enchantment glyph texture: it is our visible rune language.
        serverLevel.sendParticles(
                ParticleTypes.ENCHANT,
                bodyCenter.x,
                bodyCenter.y,
                bodyCenter.z,
                4,
                horizontalSpread * 0.82D,
                verticalSpread * 0.78D,
                horizontalSpread * 0.82D,
                0.018D);
        switch (phase) {
            case MOUNTAIN -> serverLevel.sendParticles(
                    ParticleTypes.END_ROD,
                    bodyCenter.x,
                    bodyCenter.y,
                    bodyCenter.z,
                    2,
                    horizontalSpread * 0.62D,
                    verticalSpread * 0.58D,
                    horizontalSpread * 0.62D,
                    0.01D);
            case STORM -> serverLevel.sendParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    bodyCenter.x,
                    bodyCenter.y,
                    bodyCenter.z,
                    4,
                    horizontalSpread * 0.74D,
                    verticalSpread * 0.7D,
                    horizontalSpread * 0.74D,
                    0.03D);
            case SOLAR -> {
                serverLevel.sendParticles(
                        ParticleTypes.FLAME,
                        bodyCenter.x,
                        bodyCenter.y,
                        bodyCenter.z,
                        4,
                        horizontalSpread * 0.7D,
                        verticalSpread * 0.65D,
                        horizontalSpread * 0.7D,
                        0.02D);
                serverLevel.sendParticles(
                        ParticleTypes.END_ROD,
                        bodyCenter.x,
                        bodyCenter.y,
                        bodyCenter.z,
                        2,
                        horizontalSpread * 0.54D,
                        verticalSpread * 0.5D,
                        horizontalSpread * 0.54D,
                        0.012D);
            }
        }
        if (tickCount % 8 == 0) {
            spawnRuneHalo(serverLevel, bodyCenter);
        }
    }

    private void spawnRuneHalo(ServerLevel serverLevel, Vec3 center) {
        double radius = AncientDragonScale.blocks(5.4D);
        double rotation = tickCount * 0.13D;
        for (int sample = 0; sample < 12; sample++) {
            double angle = rotation + (sample * Math.TAU / 12.0D);
            serverLevel.sendParticles(
                    ParticleTypes.ENCHANT,
                    center.x + Math.cos(angle) * radius,
                    center.y + Math.sin(angle * 2.0D) * AncientDragonScale.blocks(0.65D),
                    center.z + Math.sin(angle) * radius,
                    1,
                    0.035D,
                    0.035D,
                    0.035D,
                    0.004D);
        }
    }

    /**
     * Spawns visual-only lightning around an active storm encounter. These bolts are deliberately
     * separate from {@link Attack#STORM_BURST}: they neither damage entities nor ignite blocks,
     * and their landing points stay clear of participants so they cannot be mistaken for a dodge
     * check.
     */
    private void tickStormAmbientLightning(ServerLevel serverLevel) {
        if (phase != Phase.STORM
                || currentActiveParticipants <= 0
                || bossState == AncientDragonBossState.DORMANT
                || bossState == AncientDragonBossState.DYING
                || bossState == AncientDragonBossState.CORPSE
                || bossState == AncientDragonBossState.DEATH_RETURNING) {
            stormAmbientLightningCooldown = 0;
            return;
        }
        if (stormAmbientLightningCooldown > 0) {
            stormAmbientLightningCooldown--;
            return;
        }
        List<ServerPlayer> participants = activeParticipants(serverLevel);
        if (participants.isEmpty()) {
            stormAmbientLightningCooldown = 20;
            return;
        }
        Vec3 strike = findStormAmbientLightningPosition(serverLevel, participants);
        if (strike != null) {
            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel, EntitySpawnReason.EVENT);
            if (lightning != null) {
                lightning.setVisualOnly(true);
                lightning.setPos(strike.x, strike.y, strike.z);
                serverLevel.addFreshEntity(lightning);
            }
        }
        stormAmbientLightningCooldown = STORM_AMBIENT_LIGHTNING_MIN_INTERVAL_TICKS
                + getRandom().nextInt(STORM_AMBIENT_LIGHTNING_INTERVAL_VARIANCE_TICKS);
    }

    private Vec3 findStormAmbientLightningPosition(ServerLevel serverLevel, List<ServerPlayer> participants) {
        if (arenaVolume == null) {
            arenaVolume = AncientDragonArenaVolume.resolve(serverLevel, getUUID(), encounterOrigin);
        }
        double clearanceSquared = STORM_AMBIENT_LIGHTNING_PLAYER_CLEARANCE_BLOCKS
                * STORM_AMBIENT_LIGHTNING_PLAYER_CLEARANCE_BLOCKS;
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = getRandom().nextDouble() * Math.TAU;
            double radius = STORM_AMBIENT_LIGHTNING_MIN_RADIUS_BLOCKS
                    + getRandom().nextDouble()
                            * (STORM_AMBIENT_LIGHTNING_MAX_RADIUS_BLOCKS
                                    - STORM_AMBIENT_LIGHTNING_MIN_RADIUS_BLOCKS);
            int x = (int) Math.floor(encounterOrigin.x + (Math.cos(angle) * radius));
            int z = (int) Math.floor(encounterOrigin.z + (Math.sin(angle) * radius));
            BlockPos surface = new BlockPos(x, serverLevel.getMinY(), z);
            if (!serverLevel.isLoaded(surface)) {
                continue;
            }
            int y = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Vec3 candidate = new Vec3(x + 0.5D, y, z + 0.5D);
            if (!arenaVolume.contains(candidate)) {
                continue;
            }
            boolean tooCloseToParticipant = participants.stream()
                    .anyMatch(player -> player.position().distanceToSqr(candidate) < clearanceSquared);
            if (!tooCloseToParticipant) {
                return candidate;
            }
        }
        return null;
    }

    private void updateCollisionParts(ServerLevel serverLevel) {
        ensureCollisionParts(serverLevel);
        AncientDragonCollisionRig rig = AncientDragonCollisionRig.instance();
        AncientDragonCollisionRig.Frame frame = collisionFrame();
        // Attack contact frames can request a collision refresh before this tick's network publish.
        // Use the controller's current double-precision root/curves so hitboxes and attack rays do
        // not lag one angular step behind the movement that was just applied. Delayed joints remain
        // the same quantized authoritative values consumed by clients.
        DragonPoseState pose = authoritativePoseState();
        AncientDragonCollisionRig.SolvedFrame solved = rig.solve(
                frame, pose, dragonStagedPose(), proceduralAnimationKey());
        for (AncientDragonPartKind kind : AncientDragonPartKind.values()) {
            AncientDragonPartEntity part = collisionParts.get(kind);
            Vec3 worldPosition = position().add(pose.orientation().rotate(solved.position(kind)));
            DragonQuaternion worldRotation = pose.orientation().multiply(solved.rotation(kind));
            part.updatePose(worldPosition, worldRotation);
        }
        Vec3 headBoneOrigin = position().add(
                pose.orientation().rotate(solved.position(AncientDragonPartKind.HEAD)));
        tailAttackOrigin = position().add(
                pose.orientation().rotate(solved.position(AncientDragonPartKind.TAIL_TIP)));
        Vec3 rotatedHeadForward = pose.orientation().rotate(solved.headForward());
        headAttackDirection = rotatedHeadForward.lengthSqr() > 0.0001D
                ? rotatedHeadForward.normalize()
                : pose.orientation().forward();
        headAttackOrigin = headBoneOrigin.add(headAttackDirection.scale(AncientDragonScale.blocks(3.5D)));
    }

    private void tickDebugVisuals(ServerLevel serverLevel) {
        if (debugHitboxes && tickCount % 4 == 0) {
            for (AncientDragonPartEntity part : collisionParts.values()) {
                if (!part.isRemoved()) {
                    Vec3 center = part.getBoundingBox().getCenter();
                    serverLevel.sendParticles(
                            ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z,
                            1, 0.02D, 0.02D, 0.02D, 0.0D);
                }
            }
        }
        if (debugRoute && tickCount % 20 == 0) {
            for (int index = 0; index < SacredMountainFlightPath.size(); index++) {
                Vec3 point = SacredMountainFlightPath.worldPosition(
                        encounterOrigin, index, encounterQuarterTurns);
                serverLevel.sendParticles(
                        ParticleTypes.END_ROD, point.x, point.y, point.z,
                        2, 0.15D, 0.15D, 0.15D, 0.0D);
            }
            for (Phase candidate : Phase.values()) {
                SacredMountainFlightPath.Perch perch = SacredMountainFlightPath.perchFor(
                        candidate, encounterQuarterTurns);
                Vec3 point = encounterOrigin.add(perch.rootOffset());
                serverLevel.sendParticles(
                        ParticleTypes.FLAME, point.x, point.y + 1.0D, point.z,
                        4, 0.25D, 0.25D, 0.25D, 0.0D);
            }
        }
    }

    private AncientDragonCollisionRig.Frame collisionFrame() {
        AncientDragonCollisionRig rig = AncientDragonCollisionRig.instance();
        return switch (bossState) {
            case DORMANT -> rig.sample(AncientDragonCollisionRig.DORMANT, stateTicks, true);
            case AWAKENING -> rig.sample(AncientDragonCollisionRig.AWAKEN, stateTicks, false);
            case TAKEOFF -> rig.sample(AncientDragonCollisionRig.TAKEOFF, stateTicks, false);
            case DEPARTING -> rig.sample(AncientDragonCollisionRig.CRUISE, stateTicks, true);
            case CRUISING -> rig.sample(AncientDragonCollisionRig.CRUISE, stateTicks, true);
            case PERCH_APPROACH -> perchApproachStage == 0
                    ? rig.sample(AncientDragonCollisionRig.CRUISE, stateTicks, true)
                    : rig.sample(AncientDragonCollisionRig.DESCEND, stateTicks, false);
            case PERCHED -> rig.sample(AncientDragonCollisionRig.COMBAT_STAND, stateTicks, true);
            case ATTACKING -> currentAttack == Attack.SOLAR_BREATH && !solarBreathStarted
                    ? rig.sample(AncientDragonCollisionRig.CRUISE, stateTicks, true)
                    : rig.sample(collisionClipForAttack(), stateTicks, false);
            case COMBAT_RETURNING -> rig.sample(AncientDragonCollisionRig.CRUISE, stateTicks, true);
            case COMBAT_APPROACHING -> stateTicks <= DESCEND_ANIMATION_TICKS
                    ? rig.sample(AncientDragonCollisionRig.DESCEND, stateTicks, false)
                    : rig.sample(AncientDragonCollisionRig.CRUISE, stateTicks - DESCEND_ANIMATION_TICKS, true);
            case COMBAT_LANDING -> rig.sample(AncientDragonCollisionRig.LAND, stateTicks, false);
            case GROUND_COMBAT -> rig.sample(AncientDragonCollisionRig.COMBAT_IDLE, stateTicks, true);
            case RETURNING -> rig.sample(AncientDragonCollisionRig.CRUISE, stateTicks, true);
            case APPROACHING -> stateTicks <= DESCEND_ANIMATION_TICKS
                    ? rig.sample(AncientDragonCollisionRig.DESCEND, stateTicks, false)
                    : rig.sample(
                            AncientDragonCollisionRig.CRUISE,
                            stateTicks - DESCEND_ANIMATION_TICKS,
                            true);
            case LANDING -> rig.sample(AncientDragonCollisionRig.LAND, stateTicks, false);
            case DEATH_RETURNING -> rig.sample(AncientDragonCollisionRig.CRUISE, stateTicks, true);
            case DYING -> rig.sample(AncientDragonCollisionRig.DEATH, deathTime, false);
            case CORPSE -> rig.sample(AncientDragonCollisionRig.CORPSE, stateTicks, true);
        };
    }

    private String collisionClipForAttack() {
        if (currentAttack == null) {
            return AncientDragonCollisionRig.CRUISE;
        }
        return switch (currentAttack) {
            case DIVE_STRIKE -> AncientDragonCollisionRig.DIVE;
            case TAIL_SWEEP -> AncientDragonCollisionRig.TAIL_SWEEP;
            case STORM_BURST -> AncientDragonCollisionRig.STORM;
            case SOLAR_BREATH -> AncientDragonCollisionRig.SOLAR;
            case LIGHTNING_CHAIN, STORM_CAGE -> AncientDragonCollisionRig.STORM;
            case WIND_BLADE -> AncientDragonCollisionRig.TAIL_SWEEP;
            case BITE_HEAVY -> AncientDragonCollisionRig.BITE;
            case WING_SLAM -> AncientDragonCollisionRig.WING_SLAM;
            case RIFT_CLAW -> AncientDragonCollisionRig.WING_SLAM;
            case GROUND_STORM_BURST -> AncientDragonCollisionRig.ROAR_STORM;
            case GROUND_SOLAR_BREATH -> AncientDragonCollisionRig.GROUND_SOLAR;
        };
    }

    private String proceduralAnimationKey() {
        if (bossState == AncientDragonBossState.ATTACKING
                && currentAttack == Attack.SOLAR_BREATH
                && !solarBreathStarted) {
            return AncientDragonAnimations.FLY_CRUISE.value();
        }
        if (bossState == AncientDragonBossState.ATTACKING && currentAttack != null) {
            return animationForAttack(currentAttack).value();
        }
        return animationForState().value();
    }

    private void stopFlight() {
        flightController.stop();
        setDeltaMovement(Vec3.ZERO);
        stopInPlace();
    }

    private void snapshotTailPartCenters() {
        previousTailPartCenters.clear();
        for (Map.Entry<AncientDragonPartKind, AncientDragonPartEntity> entry : collisionParts.entrySet()) {
            if (entry.getKey().isTail() && !entry.getValue().isRemoved()) {
                previousTailPartCenters.put(entry.getKey(), entry.getValue().getBoundingBox().getCenter());
            }
        }
    }

    private void tickTailSweepContacts(ServerLevel serverLevel) {
        Map<AncientDragonPartKind, Vec3> motions = new EnumMap<>(AncientDragonPartKind.class);
        for (Map.Entry<AncientDragonPartKind, AncientDragonPartEntity> entry : collisionParts.entrySet()) {
            if (!entry.getKey().isTail() || entry.getValue().isRemoved()) {
                continue;
            }
            Vec3 center = entry.getValue().getBoundingBox().getCenter();
            Vec3 previous = previousTailPartCenters.put(entry.getKey(), center);
            motions.put(entry.getKey(), previous == null ? Vec3.ZERO : center.subtract(previous));
        }
        if (!AncientDragonAttackGeometry.tailContactActive(stateTicks)) {
            return;
        }

        for (ServerPlayer player : activeParticipants(serverLevel)) {
            if (tailSweepHitPlayers.contains(player.getUUID())) {
                continue;
            }
            Vec3 launchDirection = null;
            double strongestMotion = -1.0D;
            for (Map.Entry<AncientDragonPartKind, AncientDragonPartEntity> entry : collisionParts.entrySet()) {
                if (!entry.getKey().isTail()
                        || entry.getValue().isRemoved()) {
                    continue;
                }
                Vec3 motion = motions.getOrDefault(entry.getKey(), Vec3.ZERO);
                Vec3 currentCenter = entry.getValue().getBoundingBox().getCenter();
                Vec3 previousCenter = currentCenter.subtract(motion);
                double sweptRadius = Math.max(entry.getKey().width(), entry.getKey().height()) * 0.5D
                        + AncientDragonAttackGeometry.MELEE_CONTACT_MARGIN_BLOCKS;
                boolean currentContact = entry.getValue()
                        .getBoundingBox()
                        .inflate(AncientDragonAttackGeometry.MELEE_CONTACT_MARGIN_BLOCKS)
                        .intersects(player.getBoundingBox());
                boolean sweptContact = AncientDragonAttackGeometry.sweptContact(
                        player.getBoundingBox().getCenter(), previousCenter, currentCenter, sweptRadius);
                if ((!currentContact && !sweptContact)
                        || !attackPathClear(
                                serverLevel, currentCenter, player.getBoundingBox().getCenter())) {
                    continue;
                }
                double horizontalMotion = (motion.x * motion.x) + (motion.z * motion.z);
                if (horizontalMotion > strongestMotion) {
                    strongestMotion = horizontalMotion;
                    launchDirection = new Vec3(motion.x, 0.0D, motion.z);
                    if (launchDirection.lengthSqr() <= 0.0001D) {
                        Vec3 radial = player.position().subtract(position());
                        launchDirection = new Vec3(radial.x, 0.0D, radial.z);
                    }
                }
            }
            if (launchDirection == null) {
                continue;
            }
            if (launchDirection.lengthSqr() <= 0.0001D) {
                launchDirection = flightController.orientation().right();
            }
            launchDirection = new Vec3(launchDirection.x, 0.0D, launchDirection.z).normalize();
            if (!hurtPlayer(serverLevel, player, Attack.TAIL_SWEEP.baseDamage())) {
                continue;
            }
            tailSweepHitPlayers.add(player.getUUID());
            player.push(
                    launchDirection.x * AncientDragonAttackGeometry.TAIL_HORIZONTAL_LAUNCH,
                    AncientDragonAttackGeometry.TAIL_VERTICAL_LAUNCH,
                    launchDirection.z * AncientDragonAttackGeometry.TAIL_HORIZONTAL_LAUNCH);
        }
    }

    private static void spawnStormTelegraph(ServerLevel serverLevel, Vec3 strike, double progress) {
        spawnParticleRing(
                serverLevel,
                ParticleTypes.ELECTRIC_SPARK,
                strike.add(0.0D, 0.15D, 0.0D),
                AncientDragonAttackGeometry.STORM_STRIKE_RADIUS_BLOCKS,
                20);
        double contractingRadius = Math.max(
                0.25D,
                AncientDragonAttackGeometry.STORM_STRIKE_RADIUS_BLOCKS * (1.0D - progress));
        spawnParticleRing(
                serverLevel,
                ParticleTypes.END_ROD,
                strike.add(0.0D, 0.22D, 0.0D),
                contractingRadius,
                12);
        if (progress >= 0.7D) {
            for (int sample = 0; sample <= 6; sample++) {
                double height = 1.0D + (sample * 2.0D);
                serverLevel.sendParticles(
                        ParticleTypes.ELECTRIC_SPARK,
                        strike.x,
                        strike.y + height,
                        strike.z,
                        1,
                        0.06D,
                        0.06D,
                        0.06D,
                        0.02D);
            }
        }
    }

    private static <T extends net.minecraft.core.particles.ParticleOptions> void spawnParticleRing(
            ServerLevel serverLevel, T particle, Vec3 center, double radius, int samples) {
        for (int sample = 0; sample < samples; sample++) {
            double angle = sample * (Math.PI * 2.0D / samples);
            serverLevel.sendParticles(
                    particle,
                    center.x + (Math.cos(angle) * radius),
                    center.y,
                    center.z + (Math.sin(angle) * radius),
                    1,
                    0.02D,
                    0.02D,
                    0.02D,
                    0.0D);
        }
    }

    private void electricStrike(
            ServerLevel serverLevel,
            Vec3 strike,
            Attack damageAttack,
            double radius,
            Set<UUID> damagedPlayers) {
        serverLevel.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                strike.x,
                strike.y + 1.0D,
                strike.z,
                90,
                1.3D,
                3.0D,
                1.3D,
                0.22D);
        serverLevel.sendParticles(
                ParticleTypes.END_ROD, strike.x, strike.y + 0.3D, strike.z, 18, 0.4D, 1.5D, 0.4D, 0.03D);
        for (ServerPlayer player : activeParticipants(serverLevel)) {
            if (player.position().distanceToSqr(strike) <= radius * radius
                    && !damagedPlayers.contains(player.getUUID())
                    && hurtPlayer(serverLevel, player, damageAttack.baseDamage())) {
                damagedPlayers.add(player.getUUID());
            }
        }
    }

    private void fireWindBlade(ServerLevel serverLevel, Vec3 origin, Vec3 direction) {
        Vec3 end = clipBeamToTerrain(
                serverLevel,
                origin,
                origin.add(direction.scale(AncientDragonAttackGeometry.WIND_BLADE_LENGTH_BLOCKS)));
        spawnWindBladeLine(serverLevel, origin, direction, origin.distanceTo(end), true);
        double radiusSquared = AncientDragonAttackGeometry.WIND_BLADE_RADIUS_BLOCKS
                * AncientDragonAttackGeometry.WIND_BLADE_RADIUS_BLOCKS;
        for (ServerPlayer player : activeParticipants(serverLevel)) {
            if (!playersHitByCurrentAttack.contains(player.getUUID())
                    && distanceSquaredToSegment(player.getBoundingBox().getCenter(), origin, end) <= radiusSquared) {
                if (hurtPlayer(serverLevel, player, Attack.WIND_BLADE.baseDamage())) {
                    playersHitByCurrentAttack.add(player.getUUID());
                }
            }
        }
    }

    private static void spawnWindBladeLine(
            ServerLevel serverLevel, Vec3 origin, Vec3 direction, double length, boolean impact) {
        int samples = impact ? 36 : 18;
        for (int sample = 1; sample <= samples; sample++) {
            double distance = length * sample / samples;
            Vec3 point = origin.add(direction.scale(distance));
            serverLevel.sendParticles(
                    impact ? ParticleTypes.CLOUD : ParticleTypes.END_ROD,
                    point.x,
                    point.y,
                    point.z,
                    impact ? 2 : 1,
                    impact ? 0.35D : 0.08D,
                    impact ? 0.2D : 0.08D,
                    impact ? 0.35D : 0.08D,
                    impact ? 0.08D : 0.0D);
        }
    }

    private void strikeRift(ServerLevel serverLevel, Vec3 origin, Vec3 direction) {
        spawnRiftLine(
                serverLevel,
                origin,
                direction,
                AncientDragonAttackGeometry.RIFT_CLAW_LENGTH_BLOCKS,
                true);
        Vec3 end = origin.add(direction.scale(AncientDragonAttackGeometry.RIFT_CLAW_LENGTH_BLOCKS));
        Vec3 flatStart = new Vec3(origin.x, 0.0D, origin.z);
        Vec3 flatEnd = new Vec3(end.x, 0.0D, end.z);
        double radiusSquared = AncientDragonAttackGeometry.RIFT_CLAW_RADIUS_BLOCKS
                * AncientDragonAttackGeometry.RIFT_CLAW_RADIUS_BLOCKS;
        for (ServerPlayer player : activeParticipants(serverLevel)) {
            Vec3 flatPlayer = new Vec3(player.getX(), 0.0D, player.getZ());
            if (!playersHitByCurrentAttack.contains(player.getUUID())
                    && distanceSquaredToSegment(flatPlayer, flatStart, flatEnd) <= radiusSquared) {
                if (hurtPlayer(serverLevel, player, Attack.RIFT_CLAW.baseDamage())) {
                    playersHitByCurrentAttack.add(player.getUUID());
                    Vec3 launch = direction.scale(1.25D);
                    player.push(launch.x, 0.65D, launch.z);
                }
            }
        }
    }

    private static void spawnRiftLine(
            ServerLevel serverLevel, Vec3 origin, Vec3 direction, double length, boolean impact) {
        int samples = impact ? 30 : 18;
        for (int sample = 1; sample <= samples; sample++) {
            double distance = length * sample / samples;
            Vec3 point = origin.add(direction.scale(distance));
            int blockX = (int) Math.floor(point.x);
            int blockZ = (int) Math.floor(point.z);
            double groundY = serverLevel.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ) + 0.15D;
            serverLevel.sendParticles(
                    impact ? ParticleTypes.EXPLOSION : ParticleTypes.ELECTRIC_SPARK,
                    point.x,
                    groundY,
                    point.z,
                    impact ? 1 : 2,
                    impact ? 0.35D : 0.12D,
                    impact ? 0.25D : 0.05D,
                    impact ? 0.35D : 0.12D,
                    impact ? 0.02D : 0.0D);
        }
    }

    private boolean hurtPlayer(ServerLevel serverLevel, ServerPlayer player, float baseDamage) {
        float damage = baseDamage * AncientDragonEncounterRules.damageMultiplier(currentActiveParticipants);
        boolean aliveBefore = player.isAlive();
        boolean hurt = player.hurtServer(serverLevel, serverLevel.damageSources().mobAttack(this), damage);
        if (aliveBefore && !player.isAlive()) {
            DragonChronicleService.recordDragonSlaying(player);
        }
        return hurt;
    }

    private static void spawnSolarChargeParticles(ServerLevel serverLevel, Vec3 origin, int tick) {
        double radius = 0.35D + (tick * 0.025D);
        serverLevel.sendParticles(
                ParticleTypes.END_ROD, origin.x, origin.y, origin.z, 5, radius, radius, radius, 0.02D);
        serverLevel.sendParticles(
                ParticleTypes.FLAME,
                origin.x,
                origin.y,
                origin.z,
                3,
                radius * 0.6D,
                radius * 0.6D,
                radius * 0.6D,
                0.01D);
    }

    private Vec3 clipBeamToTerrain(ServerLevel serverLevel, Vec3 start, Vec3 end) {
        HitResult hit = serverLevel.clip(
                new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    private static void spawnBeamParticles(
            ServerLevel serverLevel, Vec3 start, Vec3 end, int samples, boolean damaging) {
        Vec3 delta = end.subtract(start);
        for (int index = 0; index <= samples; index++) {
            double fraction = index / (double) samples;
            Vec3 point = start.add(delta.scale(fraction));
            serverLevel.sendParticles(
                    ParticleTypes.FLAME,
                    point.x,
                    point.y,
                    point.z,
                    damaging ? 3 : 1,
                    damaging ? 0.18D : 0.06D,
                    damaging ? 0.18D : 0.06D,
                    damaging ? 0.18D : 0.06D,
                    0.01D);
            if (index % 3 == 0) {
                serverLevel.sendParticles(
                        ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
            }
        }
    }

    static double distanceSquaredToSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSquared = segment.lengthSqr();
        if (!(lengthSquared > 0.0D)) {
            return point.distanceToSqr(start);
        }
        double fraction = Math.clamp(point.subtract(start).dot(segment) / lengthSquared, 0.0D, 1.0D);
        return point.distanceToSqr(start.add(segment.scale(fraction)));
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float amount) {
        if (bossState == AncientDragonBossState.DORMANT) {
            awaken(serverLevel);
            return false;
        }
        if (bossState == AncientDragonBossState.AWAKENING
                || bossState == AncientDragonBossState.TAKEOFF && initialTakeoff
                || bossState == AncientDragonBossState.DEPARTING && initialTakeoff
                || bossState == AncientDragonBossState.RETURNING
                || bossState == AncientDragonBossState.APPROACHING
                || bossState == AncientDragonBossState.LANDING
                || bossState == AncientDragonBossState.DEATH_RETURNING
                || bossState == AncientDragonBossState.DYING
                || bossState == AncientDragonBossState.CORPSE) {
            return false;
        }

        float adjustedAmount = bossState == AncientDragonBossState.COMBAT_LANDING && stateTicks <= 40
                ? amount * 0.5F
                : amount;

        float previousHealth = getHealth();
        boolean accepted = super.hurtServer(serverLevel, damageSource, adjustedAmount);
        if (accepted) {
            float appliedDamage = Math.max(0.0F, previousHealth - getHealth());
            Entity attacker = damageSource.getEntity();
            if (attacker != null) {
                recentAttackerEntityId = attacker.getId();
                recentAttackerTick = tickCount;
                recentAttackerLastKnownPosition = attacker.getEyePosition();
            }
            if (attacker instanceof ServerPlayer player) {
                participantLedger.recordAcceptedDamage(
                        player.getUUID(), appliedDamage, tickCount, incomingHitPart == AncientDragonPartKind.HEAD);
            }
            if (damageSource.getDirectEntity() instanceof Projectile
                    && bossState == AncientDragonBossState.CRUISING) {
                Maneuver evasion = ((tickCount ^ getUUID().getLeastSignificantBits()) & 1L) == 0L
                        ? Maneuver.EVADE_LEFT
                        : Maneuver.EVADE_RIGHT;
                requestManeuver(evasion);
            }
        }
        return accepted;
    }

    boolean hurtPartServer(
            ServerLevel serverLevel, AncientDragonPartKind partKind, DamageSource damageSource, float amount) {
        lastHitPart = partKind;
        if (bossState == AncientDragonBossState.CORPSE) {
            return false;
        }
        incomingHitPart = partKind;
        float previousHealth = getHealth();
        boolean accepted;
        try {
            accepted = hurtServer(serverLevel, damageSource, amount * partKind.damageMultiplier());
        } finally {
            incomingHitPart = null;
        }
        float appliedDamage = Math.max(0.0F, previousHealth - getHealth());
        if (accepted && stabilityActive() && stability.applyAcceptedDamage(partKind, appliedDamage)) {
            forcedLandingPending = true;
            Entity attacker = damageSource.getEntity();
            if (attacker instanceof ServerPlayer player && bossState == AncientDragonBossState.PERCHED) {
                participantLedger.recordChannelInterrupt(player.getUUID(), tickCount);
            }
            if (bossState != AncientDragonBossState.ATTACKING) {
                forcedLandingDelayTicks = 33;
                triggerAnimation(AncientDragonAnimations.HIT_REACT);
            }
            serverLevel.playSound(
                    null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 4.0F, 0.55F);
        }
        return accepted;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        return interactCorpse(player, hand, getBoundingBox().getCenter());
    }

    InteractionResult interactCorpse(Player player, InteractionHand hand, Vec3 harvestPosition) {
        if (bossState != AncientDragonBossState.CORPSE) {
            return InteractionResult.PASS;
        }
        if (isCorpseDissipating()) {
            if (!level().isClientSide()) {
                player.sendSystemMessage(Component.translatable("message.ancient_dragon.corpse_dissipating"));
            }
            return InteractionResult.FAIL;
        }
        ItemStack tool = player.getItemInHand(hand);
        if (!tool.is(ItemTags.AXES)) {
            if (!level().isClientSide()) {
                player.sendSystemMessage(Component.translatable("message.ancient_dragon.wrong_harvest_tool"));
            }
            return InteractionResult.FAIL;
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level() instanceof ServerLevel serverLevel)) {
            return InteractionResult.FAIL;
        }
        if (!harvestCorpse(serverLevel, serverPlayer, harvestPosition)) {
            return InteractionResult.FAIL;
        }
        tool.hurtAndBreak(1, serverPlayer, hand);
        return InteractionResult.SUCCESS_SERVER;
    }

    private boolean harvestCorpse(
            ServerLevel serverLevel, ServerPlayer player, Vec3 harvestPosition) {
        if (stateTicks < CORPSE_PARTICIPANT_PROTECTION_TICKS
                && !victoryEligiblePlayers.isEmpty()
                && !victoryEligiblePlayers.contains(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.ancient_dragon.corpse_protected"), true);
            return false;
        }
        List<CorpseHarvestNode> remainingNodes = new ArrayList<>();
        for (CorpseHarvestNode node : CorpseHarvestNode.values()) {
            if (!harvestedCorpseNodes.contains(node)) {
                remainingNodes.add(node);
            }
        }
        if (remainingNodes.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.ancient_dragon.corpse_already_harvested"), true);
            return false;
        }

        Set<UUID> recipients = victoryEligiblePlayers.isEmpty()
                ? Set.of(player.getUUID())
                : Set.copyOf(victoryEligiblePlayers);
        Set<ServerPlayer> notifiedPlayers = new HashSet<>();
        for (UUID recipientId : recipients) {
            ServerPlayer recipient = serverLevel.getServer().getPlayerList().getPlayer(recipientId);
            for (CorpseHarvestNode node : remainingNodes) {
                if (node == CorpseHarvestNode.HEART) {
                    continue;
                }
                int count = AncientDragonCorpseLoot.countForHarvestNode(node.serializedName(), 1);
                if (recipient != null) {
                    AncientDragonCorpseRewardService.giveLoot(recipient, node.serializedName(), count);
                    notifiedPlayers.add(recipient);
                } else {
                    pendingCorpseLoot.computeIfAbsent(
                                    recipientId, ignored -> new EnumMap<>(CorpseHarvestNode.class))
                            .merge(node, count, Math::addExact);
                }
            }
        }
        if (remainingNodes.contains(CorpseHarvestNode.HEART)) {
            giveCorpseHeart(player);
            notifiedPlayers.add(player);
        }

        int experiencePerEligiblePlayer = remainingNodes.stream()
                .mapToInt(CorpseHarvestNode::experiencePerEligibleParticipant)
                .sum();
        awardCorpseExperience(serverLevel, experiencePerEligiblePlayer);
        harvestedCorpseNodes.addAll(remainingNodes);
        corpseHarvestProgress.clear();

        serverLevel.sendParticles(
                ParticleTypes.CRIT,
                harvestPosition.x,
                harvestPosition.y,
                harvestPosition.z,
                10,
                0.45D,
                0.45D,
                0.45D,
                0.08D);
        serverLevel.playSound(
                null,
                harvestPosition.x,
                harvestPosition.y,
                harvestPosition.z,
                SoundEvents.IRON_GOLEM_DAMAGE,
                SoundSource.BLOCKS,
                1.2F,
                0.72F);
        for (ServerPlayer notifiedPlayer : notifiedPlayers) {
            notifiedPlayer.sendSystemMessage(
                    Component.translatable("message.ancient_dragon.corpse_loot_received"), true);
        }
        beginCorpseDissipation(serverLevel);
        player.sendSystemMessage(Component.translatable("message.ancient_dragon.corpse_dissipating"), true);
        return true;
    }

    private static void giveCorpseHeart(ServerPlayer player) {
        ItemStack heart = new ItemStack(AncientDragonItems.ANCIENT_DRAGON_HEART);
        player.getInventory().add(heart);
        if (!heart.isEmpty()) {
            var overflow = player.drop(heart, false);
            if (overflow != null) {
                overflow.setNoPickUpDelay();
            }
        }
    }

    private void awardCorpseExperience(ServerLevel serverLevel, int experience) {
        if (victoryEligiblePlayers.isEmpty()) {
            return;
        }
        for (UUID playerId : victoryEligiblePlayers) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                AncientDragonCorpseRewardService.giveExperience(player, experience);
            } else {
                pendingCorpseExperience.merge(playerId, experience, Math::addExact);
            }
        }
    }

    private void deliverPendingCorpseLoot(ServerLevel serverLevel) {
        var pendingIterator = pendingCorpseLoot.entrySet().iterator();
        while (pendingIterator.hasNext()) {
            Map.Entry<UUID, EnumMap<CorpseHarvestNode, Integer>> pending = pendingIterator.next();
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(pending.getKey());
            if (player == null) {
                continue;
            }
            pending.getValue().forEach((node, count) ->
                    AncientDragonCorpseRewardService.giveLoot(player, node.serializedName(), count));
            player.sendSystemMessage(Component.translatable("message.ancient_dragon.corpse_loot_received"), true);
            pendingIterator.remove();
        }
    }

    private void deliverPendingCorpseExperience(ServerLevel serverLevel) {
        var pendingIterator = pendingCorpseExperience.entrySet().iterator();
        while (pendingIterator.hasNext()) {
            Map.Entry<UUID, Integer> pending = pendingIterator.next();
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(pending.getKey());
            if (player == null) {
                continue;
            }
            AncientDragonCorpseRewardService.giveExperience(player, pending.getValue());
            pendingIterator.remove();
        }
    }

    private boolean allCorpseHarvestNodesClaimed() {
        return harvestedCorpseNodes.size() == CorpseHarvestNode.values().length;
    }

    private boolean isCorpseDissipating() {
        return corpseDissipationTicks >= 0;
    }

    /** Begins the one-way post-loot cleanup after every participant's share has been allocated. */
    private void beginCorpseDissipation(ServerLevel serverLevel) {
        if (isCorpseDissipating() || !allCorpseHarvestNodesClaimed()) {
            return;
        }
        persistDeferredCorpseRewards(serverLevel);
        corpseDissipationTicks = 0;
        discardCollisionParts();
        serverLevel.sendParticles(
                ParticleTypes.ASH,
                getX(),
                getY() + AncientDragonScale.blocks(2.0D),
                getZ(),
                24,
                AncientDragonScale.blocks(5.0D),
                AncientDragonScale.blocks(2.0D),
                AncientDragonScale.blocks(5.0D),
                0.01D);
    }

    /** Moves old entity-local offline shares into world data before the parent entity is removed. */
    private void persistDeferredCorpseRewards(ServerLevel serverLevel) {
        pendingCorpseExperience.forEach((playerId, experience) ->
                AncientDragonCorpseRewardService.queueExperience(serverLevel, playerId, experience));
        pendingCorpseLoot.forEach((playerId, loot) -> loot.forEach((node, count) ->
                AncientDragonCorpseRewardService.queueLoot(serverLevel, playerId, node.serializedName(), count)));
        pendingCorpseExperience.clear();
        pendingCorpseLoot.clear();
    }

    /** Uses explicit corpse physics because the dragon host normally remains gravity-free in flight. */
    private void tickCorpseGravity() {
        noPhysics = false;
        Vec3 velocity = getDeltaMovement();
        if (onGround() && velocity.y <= 0.0D) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        double nextVerticalVelocity = AncientDragonCorpseFallPhysics.nextVerticalVelocity(velocity.y);
        Vec3 movement = new Vec3(0.0D, nextVerticalVelocity, 0.0D);
        double previousY = getY();
        move(MoverType.SELF, movement);
        double appliedVerticalMovement = getY() - previousY;
        if (onGround() || appliedVerticalMovement > nextVerticalVelocity + CORPSE_GROUND_EPSILON) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        setDeltaMovement(movement);
    }

    private void tickCorpseDissipation(ServerLevel serverLevel) {
        if (!isCorpseDissipating()) {
            return;
        }
        corpseDissipationTicks = AncientDragonCorpseDissipation.advance(corpseDissipationTicks);
        if (corpseDissipationTicks % 4 == 0 || AncientDragonCorpseDissipation.isComplete(corpseDissipationTicks)) {
            float progress = AncientDragonCorpseDissipation.progress(corpseDissipationTicks);
            double horizontalSpread = AncientDragonScale.blocks(4.0D + progress * 10.0D);
            double verticalSpread = AncientDragonScale.blocks(1.5D + progress * 4.0D);
            serverLevel.sendParticles(
                    ParticleTypes.ASH,
                    getX(),
                    getY() + AncientDragonScale.blocks(2.5D),
                    getZ(),
                    AncientDragonCorpseDissipation.ashParticles(corpseDissipationTicks),
                    horizontalSpread,
                    verticalSpread,
                    horizontalSpread,
                    0.006D);
            serverLevel.sendParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    getX(),
                    getY() + AncientDragonScale.blocks(3.0D),
                    getZ(),
                    AncientDragonCorpseDissipation.soulFlameParticles(corpseDissipationTicks),
                    horizontalSpread * 0.7D,
                    verticalSpread,
                    horizontalSpread * 0.7D,
                    0.01D);
        }
        if (!AncientDragonCorpseDissipation.isComplete(corpseDissipationTicks)) {
            return;
        }
        serverLevel.sendParticles(
                ParticleTypes.CLOUD,
                getX(),
                getY() + AncientDragonScale.blocks(2.0D),
                getZ(),
                96,
                AncientDragonScale.blocks(12.0D),
                AncientDragonScale.blocks(4.0D),
                AncientDragonScale.blocks(12.0D),
                0.035D);
        discard();
    }

    private void discardCollisionParts() {
        for (AncientDragonPartEntity part : collisionParts.values()) {
            if (!part.isRemoved()) {
                part.discard();
            }
        }
        collisionParts.clear();
    }

    private boolean stabilityActive() {
        return bossState == AncientDragonBossState.CRUISING
                || bossState == AncientDragonBossState.PERCH_APPROACH
                || bossState == AncientDragonBossState.PERCHED
                || bossState == AncientDragonBossState.ATTACKING && currentAttackDomain == AttackDomain.AIR;
    }

    @Override
    public void die(DamageSource damageSource) {
        // Vanilla replays the death entity event on the logical client. The client must only
        // consume the animation state already synchronized by the server; calling BlendLib's
        // animation command API here disconnects it because that API requires a live server
        // entity.
        if (level().isClientSide()) {
            super.die(damageSource);
            return;
        }
        if (bossState == AncientDragonBossState.DYING
                || bossState == AncientDragonBossState.CORPSE
                || bossState == AncientDragonBossState.DEATH_RETURNING) {
            return;
        }
        victoryEligiblePlayers.clear();
        victoryEligiblePlayers.addAll(participantLedger.eligiblePlayers(getMaxHealth()));
        if (!debugEncounter && level() instanceof ServerLevel serverLevel) {
            AncientDragonEncounterData.get(serverLevel)
                    .updateStage(getUUID(), Stage.DYING, victoryEligiblePlayers);
            for (UUID playerId : victoryEligiblePlayers) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
                if (player != null) {
                    AncientDragonCriteria.VICTORY.trigger(player);
                }
            }
        }
        boolean platformGroundDeath = !isAirborneBossState()
                && SacredMountainFlightPath.isInsideCombatPlatform(
                        position(), encounterOrigin, encounterQuarterTurns);
        if (platformGroundDeath) {
            beginDeathLandmark(damageSource);
            return;
        }
        setHealth(1.0F);
        currentAttack = null;
        forcedLandingPending = false;
        bossState = AncientDragonBossState.DEATH_RETURNING;
        stateTicks = 0;
        publishPersistentAnimation(AncientDragonAnimations.FLY_CRUISE);
        triggerAnimation(AncientDragonAnimations.HIT_REACT);
        bossEvent.setProgress(0.0F);
        bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
        stabilityEvent.setVisible(false);
        synchronizePoseState();
    }

    private void beginDeathLandmark(DamageSource damageSource) {
        bossState = AncientDragonBossState.DYING;
        stateTicks = 0;
        deathTime = 0;
        currentAttack = null;
        setHealth(0.0F);
        publishPersistentAnimation(AncientDragonAnimations.DEATH_LANDMARK);
        bossEvent.setProgress(0.0F);
        bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
        stabilityEvent.setVisible(false);
        synchronizePoseState();
        if (level() instanceof ServerLevel serverLevel) {
            DragonAtmosphereDispatcher.snapshot(this, serverLevel);
            DragonAtmosphereDispatcher.cue(this, serverLevel, DragonAtmosphereCue.DEATH_IMPACT, position());
        }
        super.die(damageSource);
    }

    @Override
    protected void tickDeath() {
        if (bossState == AncientDragonBossState.CORPSE) {
            flightController.stop();
        } else {
            stopFlight();
        }
        if (bossState == AncientDragonBossState.DYING) {
            deathTime++;
            if (deathTime % 20 == 0 && level() instanceof ServerLevel serverLevel) {
                DragonAtmosphereDispatcher.snapshot(this, serverLevel);
            }
        } else if (bossState == AncientDragonBossState.CORPSE && stateTicks < Integer.MAX_VALUE) {
            stateTicks++;
            if (level() instanceof ServerLevel serverLevel) {
                tickCorpseGravity();
                // This also migrates fully-harvested corpse NBT written by older releases.
                beginCorpseDissipation(serverLevel);
                tickCorpseDissipation(serverLevel);
            }
        }
        if (bossState == AncientDragonBossState.DYING && deathTime >= DEATH_DURATION_TICKS) {
            if (debugEncounter) {
                setHealth(getMaxHealth());
                deathTime = 0;
                finishLanding();
                debugEncounter = false;
                return;
            }
            bossState = AncientDragonBossState.CORPSE;
            stateTicks = 0;
            bossEvent.removeAllPlayers();
            stabilityEvent.removeAllPlayers();
            publishPersistentAnimation(AncientDragonAnimations.CORPSE_STATIC);
            if (!debugEncounter && level() instanceof ServerLevel serverLevel) {
                AncientDragonEncounterData.get(serverLevel)
                        .updateStage(getUUID(), Stage.CORPSE, victoryEligiblePlayers);
                DragonChronicleService.grantOrQueue(
                        serverLevel.getServer(), victoryEligiblePlayers, DragonChronicleEvent.DRAGON_FALLEN);
                DragonAtmosphereDispatcher.snapshot(this, serverLevel);
                DragonAtmosphereDispatcher.cue(this, serverLevel, DragonAtmosphereCue.CORPSE_SETTLE, position());
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // A world Boss never despawns because players retreat.
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return entity instanceof Player
                ? entityData.get(DATA_BLOCKS_PLAYERS)
                : super.canBeCollidedWith(entity);
    }

    boolean blocksPlayerCollision() {
        return entityData.get(DATA_BLOCKS_PLAYERS);
    }

    @Override
    public void remove(RemovalReason reason) {
        discardCollisionParts();
        super.remove(reason);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
        stabilityEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
        stabilityEvent.removePlayer(player);
    }

    private void updateBossBar() {
        bossEvent.setProgress(bossState == AncientDragonBossState.DEATH_RETURNING
                ? 0.0F
                : Math.clamp(getHealth() / Math.max(getMaxHealth(), 1.0F), 0.0F, 1.0F));
        bossEvent.setName(Component.translatable(
                "bossbar.ancient_dragon." + phase.serializedName()));
        boolean showStability = isAlive() && stabilityActive();
        stabilityEvent.setVisible(showStability);
        stabilityEvent.setProgress(Math.clamp(
                stability.current() / Math.max(stability.maximum(), 1.0F), 0.0F, 1.0F));
    }

    public void setEncounterOrigin(Vec3 origin) {
        encounterOrigin = origin;
        encounterOriginSet = true;
    }

    public Vec3 encounterOrigin() {
        return encounterOrigin;
    }

    public void setEncounterRotation(int quarterTurns) {
        encounterQuarterTurns = Math.floorMod(quarterTurns, 4);
        boolean groundedAtRoost = encounterOriginSet
                && position().distanceToSqr(encounterOrigin) <= 4.0D * 4.0D
                && !isAirborneBossState();
        if (bossState == AncientDragonBossState.DORMANT || groundedAtRoost) {
            alignRoostFacing();
            poseDynamics.reset(DragonStagedPose.matching(authoritativePoseState()));
            synchronizePoseState();
        }
    }

    public int encounterQuarterTurns() {
        return encounterQuarterTurns;
    }

    public AncientDragonBossState bossState() {
        return bossState;
    }

    public Phase encounterPhase() {
        return phase;
    }

    public int stateTicks() {
        return stateTicks;
    }

    public String patrolWaypointName() {
        return SacredMountainFlightPath.waypoint(patrolWaypointIndex).id();
    }

    public int patrolWaypointIndex() {
        return SacredMountainFlightPath.normalizeIndex(patrolWaypointIndex);
    }

    public String flightTargetName() {
        return switch (bossState) {
            case RETURNING -> "A";
            case APPROACHING -> SacredMountainFlightPath.approachWaypoint(approachWaypointIndex).id();
            case LANDING, DORMANT -> "R";
            default -> patrolWaypointName();
        };
    }

    public int disengagedTicks() {
        return disengagedTicks;
    }

    public int scaledParticipants() {
        return scaledParticipants;
    }

    public Optional<Attack> currentAttack() {
        return Optional.ofNullable(currentAttack);
    }

    public float stabilityCurrent() {
        return stability.current();
    }

    public float stabilityMaximum() {
        return stability.maximum();
    }

    public int currentActiveParticipants() {
        return currentActiveParticipants;
    }

    public boolean debugAwaken() {
        if (!(level() instanceof ServerLevel serverLevel) || bossState != AncientDragonBossState.DORMANT) {
            return false;
        }
        debugEncounter = true;
        awaken(serverLevel);
        return true;
    }

    public boolean debugStopEncounter() {
        if (!debugEncounter || !isAlive() || bossState == AncientDragonBossState.DORMANT) {
            return false;
        }
        finishLanding();
        debugEncounter = false;
        return true;
    }

    public boolean debugForcePhase(Phase forcedPhase) {
        if (!debugEncounter || forcedPhase == null || bossState == AncientDragonBossState.DORMANT) {
            return false;
        }
        pendingPhase = forcedPhase;
        applyPendingPhase();
        return phase == forcedPhase;
    }

    public boolean debugForceAttack(Attack attack) {
        if (!debugEncounter || attack == null || !(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        boolean groundState = bossState == AncientDragonBossState.GROUND_COMBAT;
        Attack effectiveAttack = groundState && attack == Attack.SOLAR_BREATH
                ? Attack.GROUND_SOLAR_BREATH
                : groundState && attack == Attack.STORM_BURST
                        ? Attack.GROUND_STORM_BURST
                        : attack;
        if (groundState != (effectiveAttack.domain() == AttackDomain.GROUND)) {
            return false;
        }
        List<ServerPlayer> participants = activeParticipants(serverLevel);
        Optional<ServerPlayer> target = selectTarget(participants);
        if (target.isEmpty()) {
            return false;
        }
        lastDirectorScores = Map.of(effectiveAttack, Double.POSITIVE_INFINITY);
        startAttack(effectiveAttack, target.get(), effectiveAttack.domain());
        return true;
    }

    public boolean debugSetStability(float value) {
        boolean broken = stability.debugSetCurrent(value);
        if (broken && stabilityActive()) {
            forcedLandingPending = true;
            forcedLandingDelayTicks = bossState == AncientDragonBossState.ATTACKING ? 0 : 33;
            if (bossState != AncientDragonBossState.ATTACKING) {
                triggerAnimation(AncientDragonAnimations.HIT_REACT);
            }
        }
        return value >= 0.0F && value <= stability.maximum();
    }

    public void debugHitboxes(boolean enabled) {
        debugHitboxes = enabled;
    }

    public void debugRoute(boolean enabled) {
        debugRoute = enabled;
    }

    public void debugDirector(boolean enabled) {
        debugDirector = enabled;
    }

    public String combatDebugStatus() {
        String scores = lastDirectorScores.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().name().toLowerCase(java.util.Locale.ROOT)
                        + "=" + String.format(java.util.Locale.ROOT, "%.1f", entry.getValue()))
                .collect(java.util.stream.Collectors.joining(","));
        return String.format(
                java.util.Locale.ROOT,
                "state=%s phase=%s health=%.1f/%.1f active=%d locked_scale=%d stability=%.1f/%.1f "
                        + "air_actions=%d ground_ticks=%d target=%s attack=%s debug=%s hitboxes=%s route=%s director=%s scores=[%s]",
                bossState.serializedName(),
                phase.serializedName(),
                getHealth(),
                getMaxHealth(),
                currentActiveParticipants,
                scaledParticipants,
                stability.current(),
                stability.maximum(),
                airAttacksThisCycle,
                groundCombatTicksRemaining,
                currentTargetId().map(UUID::toString).orElse("none"),
                currentAttack == null ? "none" : currentAttack.name().toLowerCase(java.util.Locale.ROOT),
                debugEncounter,
                debugHitboxes,
                debugRoute,
                debugDirector,
                debugDirector ? scores : "hidden");
    }

    public String participantDebugStatus() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return "participants=unavailable";
        }
        List<ServerPlayer> active = activeParticipants(serverLevel);
        String details = active.stream()
                .map(player -> player.getGameProfile().name()
                        + "{damage=" + String.format(java.util.Locale.ROOT, "%.1f",
                                participantLedger.lifetimeDamage(player.getUUID()))
                        + ",threat=" + String.format(java.util.Locale.ROOT, "%.1f",
                                participantLedger.recentThreat(player.getUUID(), tickCount)) + "}")
                .collect(java.util.stream.Collectors.joining(","));
        return "active=" + active.size() + " max_seen=" + participantLedger.maxParticipantsSeen()
                + " empty_ticks=" + participantLedger.emptyTicks() + " [" + details + "]";
    }

    public Optional<AncientDragonPartKind> lastHitPart() {
        return Optional.ofNullable(lastHitPart);
    }

    public Optional<UUID> currentTargetId() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        return selectTarget(activeParticipants(serverLevel)).map(ServerPlayer::getUUID);
    }

    /** Current network pose; safe to consume on either logical side. */
    public DragonPoseState dragonPoseState() {
        DragonQuaternion orientation;
        try {
            orientation = new DragonQuaternion(
                    entityData.get(DATA_ORIENTATION_X),
                    entityData.get(DATA_ORIENTATION_Y),
                    entityData.get(DATA_ORIENTATION_Z),
                    entityData.get(DATA_ORIENTATION_W));
        } catch (IllegalArgumentException exception) {
            orientation = DragonQuaternion.fromMinecraftYaw(getYRot());
        }
        return new DragonPoseState(
                orientation,
                new Curve(
                        entityData.get(DATA_TORSO_PITCH),
                        entityData.get(DATA_TORSO_YAW),
                        entityData.get(DATA_TORSO_ROLL)),
                new Curve(
                        entityData.get(DATA_TAIL_PITCH),
                        entityData.get(DATA_TAIL_YAW),
                        entityData.get(DATA_TAIL_ROLL)),
                entityData.get(DATA_ATTENTION_YAW),
                entityData.get(DATA_ATTENTION_PITCH),
                AttentionMode.byNetworkId(Byte.toUnsignedInt(entityData.get(DATA_ATTENTION_MODE))),
                entityData.get(DATA_ATTENTION_TARGET),
                Maneuver.byNetworkId(Byte.toUnsignedInt(entityData.get(DATA_MANEUVER))));
    }

    /** Authoritative delayed joint inputs shared by client rendering and server collision FK. */
    public DragonStagedPose dragonStagedPose() {
        int[] packedNeck = new int[DATA_STAGED_NECK.size()];
        int[] packedTail = new int[DATA_STAGED_TAIL.size()];
        for (int index = 0; index < packedNeck.length; index++) {
            packedNeck[index] = entityData.get(DATA_STAGED_NECK.get(index));
        }
        for (int index = 0; index < packedTail.length; index++) {
            packedTail[index] = entityData.get(DATA_STAGED_TAIL.get(index));
        }
        return DragonStagedPose.unpack(packedNeck, packedTail);
    }

    public Maneuver currentManeuver() {
        return dragonPoseState().maneuver();
    }

    public int maneuverCooldownTicks() {
        return level().isClientSide() ? 0 : flightController.maneuverCooldown();
    }

    public void attentionNearest() {
        setAttentionOverride(DragonAttentionController.AttentionOverride.NEAREST);
    }

    public void attentionScan() {
        setAttentionOverride(DragonAttentionController.AttentionOverride.SCAN);
    }

    public void attentionClear() {
        setAttentionOverride(DragonAttentionController.AttentionOverride.CLEAR);
    }

    public void setPersistentAnimation(BlendAnimationKey animationKey) {
        publishPersistentAnimation(animationKey);
    }

    public void triggerAnimation(BlendAnimationKey animationKey) {
        if (level().isClientSide()) {
            return;
        }
        BlendAnimations.entity(this).trigger(animationKey, 1.0F, getUUID().getMostSignificantBits() ^ tickCount);
    }

    private void publishPersistentAnimation(BlendAnimationKey animationKey) {
        if (level().isClientSide()) {
            return;
        }
        BlendAnimations.entity(this).setPersistent(animationKey, 1.0F, getUUID().getLeastSignificantBits() ^ tickCount);
        initialAnimationPublished = true;
    }

    private BlendAnimationKey animationForState() {
        return switch (bossState) {
            case DORMANT -> AncientDragonAnimations.DORMANT_HOLD;
            case AWAKENING -> AncientDragonAnimations.AWAKEN_INTRO;
            case TAKEOFF -> AncientDragonAnimations.TAKEOFF;
            case DEPARTING -> AncientDragonAnimations.FLY_CRUISE;
            case CRUISING -> AncientDragonAnimations.FLY_CRUISE;
            case PERCH_APPROACH -> perchApproachStage == 0
                    ? AncientDragonAnimations.FLY_CRUISE
                    : AncientDragonAnimations.FLY_DESCEND;
            case PERCHED -> AncientDragonAnimations.COMBAT_STAND;
            case ATTACKING -> currentAttackDomain == AttackDomain.GROUND
                    ? AncientDragonAnimations.COMBAT_IDLE
                    : AncientDragonAnimations.FLY_CRUISE;
            case COMBAT_RETURNING -> AncientDragonAnimations.FLY_CRUISE;
            case COMBAT_APPROACHING -> stateTicks <= DESCEND_ANIMATION_TICKS
                    ? AncientDragonAnimations.FLY_DESCEND
                    : AncientDragonAnimations.FLY_CRUISE;
            case COMBAT_LANDING -> AncientDragonAnimations.LAND;
            case GROUND_COMBAT -> AncientDragonAnimations.COMBAT_IDLE;
            case RETURNING -> AncientDragonAnimations.FLY_CRUISE;
            case APPROACHING -> stateTicks <= DESCEND_ANIMATION_TICKS
                    ? AncientDragonAnimations.FLY_DESCEND
                    : AncientDragonAnimations.FLY_CRUISE;
            case LANDING -> AncientDragonAnimations.LAND;
            case DEATH_RETURNING -> AncientDragonAnimations.FLY_CRUISE;
            case DYING -> AncientDragonAnimations.DEATH_LANDMARK;
            case CORPSE -> AncientDragonAnimations.CORPSE_STATIC;
        };
    }

    private static BlendAnimationKey animationForAttack(Attack attack) {
        return switch (attack) {
            case DIVE_STRIKE -> AncientDragonAnimations.DIVE_STRIKE;
            case TAIL_SWEEP -> AncientDragonAnimations.AERIAL_TAIL_SWEEP;
            case STORM_BURST -> AncientDragonAnimations.AERIAL_STORM_BURST;
            case SOLAR_BREATH -> AncientDragonAnimations.AERIAL_SOLAR_BREATH;
            case LIGHTNING_CHAIN, STORM_CAGE -> AncientDragonAnimations.AERIAL_STORM_BURST;
            case WIND_BLADE -> AncientDragonAnimations.AERIAL_TAIL_SWEEP;
            case BITE_HEAVY -> AncientDragonAnimations.BITE_HEAVY;
            case WING_SLAM -> AncientDragonAnimations.WING_SLAM;
            case RIFT_CLAW -> AncientDragonAnimations.WING_SLAM;
            case GROUND_STORM_BURST -> AncientDragonAnimations.ROAR_STORM;
            case GROUND_SOLAR_BREATH -> AncientDragonAnimations.SOLAR_BREATH;
        };
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        bossState = AncientDragonBossState.fromSerializedName(input.getStringOr("BossState", "dormant"));
        phase = parsePhase(input.getStringOr("EncounterPhase", "mountain"));
        stateTicks = Math.max(0, input.getIntOr("StateTicks", 0));
        int savedCorpseDissipationTicks = input.getIntOr("CorpseDissipationTicks", -1);
        corpseDissipationTicks = bossState == AncientDragonBossState.CORPSE && savedCorpseDissipationTicks >= 0
                ? Math.min(savedCorpseDissipationTicks, AncientDragonCorpseDissipation.DURATION_TICKS)
                : -1;
        attackCooldownTicks = Math.max(0, input.getIntOr("AttackCooldown", 0));
        attackSequence = Math.max(0, input.getIntOr("AttackSequence", 0));
        currentAttack = parseAttack(input.getStringOr("CurrentAttack", ""));
        currentAttackDomain = parseAttackDomain(input.getStringOr("AttackDomain", "air"));
        pendingPhase = parseOptionalPhase(input.getStringOr("PendingPhase", ""));
        patrolWaypointIndex = SacredMountainFlightPath.normalizeIndex(
                input.getIntOr("PatrolWaypoint", SacredMountainFlightPath.ASSEMBLY_INDEX));
        approachWaypointIndex = SacredMountainFlightPath.normalizeApproachIndex(
                input.getIntOr("ApproachWaypoint", 0));
        disengagedTicks = Math.clamp(
                input.getIntOr("DisengagedTicks", 0), 0, RETURN_TO_ROOST_DELAY_TICKS);
        scaledParticipants = AncientDragonEncounterRules.normalizedParticipants(
                input.getIntOr("ScaledParticipants", 1));
        currentActiveParticipants = Math.max(0, input.getIntOr("ActiveParticipants", 0));
        airAttacksThisCycle = Math.clamp(
                input.getIntOr("AirAttacksThisCycle", 0),
                0,
                AncientDragonEncounterRules.AIR_ATTACKS_PER_CYCLE);
        groundCombatTicksRemaining = Math.max(0, input.getIntOr("GroundCombatTicks", 0));
        forcedLandingDelayTicks = Math.max(0, input.getIntOr("ForcedLandingDelay", 0));
        forcedLandingPending = input.getIntOr("ForcedLandingPending", 0) != 0;
        initialTakeoff = input.getIntOr("InitialTakeoff", 1) != 0;
        perchPending = input.getIntOr("PerchPending", 0) != 0;
        perchApproachStage = Math.clamp(input.getIntOr("PerchApproachStage", 0), 0, 1);
        takeoffTarget = new Vec3(
                input.getDoubleOr("TakeoffTargetX", 0.0D),
                input.getDoubleOr("TakeoffTargetY", 0.0D),
                input.getDoubleOr("TakeoffTargetZ", 0.0D));
        stability.restore(
                (float) input.getDoubleOr("StabilityCurrent", AncientDragonEncounterRules.SOLO_STABILITY),
                (float) input.getDoubleOr("StabilityMaximum", AncientDragonEncounterRules.SOLO_STABILITY),
                input.getIntOr("StabilityForcedLandingUsed", 0) != 0);
        debugEncounter = input.getIntOr("DebugEncounter", 0) != 0;
        debugHitboxes = input.getIntOr("DebugHitboxes", 0) != 0;
        debugRoute = input.getIntOr("DebugRoute", 0) != 0;
        debugDirector = input.getIntOr("DebugDirector", 0) != 0;
        encounterOrigin = new Vec3(
                input.getDoubleOr("EncounterOriginX", getX()),
                input.getDoubleOr("EncounterOriginY", getY()),
                input.getDoubleOr("EncounterOriginZ", getZ()));
        encounterOriginSet = true;
        int inferredQuarterTurns = Math.round((getYRot() - 90.0F) / 90.0F);
        encounterQuarterTurns = Math.floorMod(
                input.getIntOr("EncounterQuarterTurns", inferredQuarterTurns), 4);

        DragonQuaternion savedOrientation;
        try {
            savedOrientation = new DragonQuaternion(
                    input.getDoubleOr("PoseOrientationX", 0.0D),
                    input.getDoubleOr("PoseOrientationY", 0.0D),
                    input.getDoubleOr("PoseOrientationZ", 0.0D),
                    input.getDoubleOr("PoseOrientationW", 1.0D));
        } catch (IllegalArgumentException exception) {
            savedOrientation = DragonQuaternion.fromMinecraftYaw(getYRot());
        }
        Vec3 savedVelocity = new Vec3(
                input.getDoubleOr("FlightVelocityX", 0.0D),
                input.getDoubleOr("FlightVelocityY", 0.0D),
                input.getDoubleOr("FlightVelocityZ", 0.0D));
        Curve savedTorso = new Curve(
                input.getDoubleOr("TorsoCurvePitch", 0.0D),
                input.getDoubleOr("TorsoCurveYaw", 0.0D),
                input.getDoubleOr("TorsoCurveRoll", 0.0D));
        Curve savedTail = new Curve(
                input.getDoubleOr("TailCurvePitch", 0.0D),
                input.getDoubleOr("TailCurveYaw", 0.0D),
                input.getDoubleOr("TailCurveRoll", 0.0D));
        flightController.restore(
                savedOrientation,
                savedVelocity,
                savedTorso,
                savedTail,
                Math.max(0, input.getIntOr("ManeuverCooldown", 0)));
        DragonPoseState restoredCompact = new DragonPoseState(
                savedOrientation,
                savedTorso,
                savedTail,
                0.0D,
                0.0D,
                AttentionMode.SCAN,
                -1,
                Maneuver.NONE);
        DragonStagedPose stagedDefaults = DragonStagedPose.matching(restoredCompact);
        int stagedTailEncoding = input.getIntOr("StagedTailEncoding", 1);
        int[] packedNeck = new int[DATA_STAGED_NECK.size()];
        int[] packedTail = new int[DATA_STAGED_TAIL.size()];
        for (int index = 0; index < packedNeck.length; index++) {
            packedNeck[index] = input.getIntOr("StagedNeck" + index, stagedDefaults.packedNeck(index));
        }
        for (int index = 0; index < packedTail.length; index++) {
            int defaultTail = stagedTailEncoding >= DragonStagedPose.TAIL_ENCODING_VERSION
                    ? stagedDefaults.packedTail(index)
                    : stagedDefaults.packedTailLegacyV1(index);
            packedTail[index] = input.getIntOr("StagedTail" + index, defaultTail);
        }
        poseDynamics.reset(stagedTailEncoding >= DragonStagedPose.TAIL_ENCODING_VERSION
                ? DragonStagedPose.unpack(packedNeck, packedTail)
                : DragonStagedPose.unpackLegacyV1(packedNeck, packedTail));
        flightControllerInitialized = true;
        attentionController.reset();
        attentionOverride = DragonAttentionController.AttentionOverride.NONE;
        recentAttackerEntityId = -1;
        recentAttackerTick = Integer.MIN_VALUE;
        recentAttackerLastKnownPosition = Vec3.ZERO;

        List<AncientDragonParticipantLedger.SavedParticipant> savedParticipants = new ArrayList<>();
        for (ValueInput participantInput : input.childrenListOrEmpty("Participants")) {
            try {
                UUID playerId = UUID.fromString(participantInput.getStringOr("Player", ""));
                savedParticipants.add(new AncientDragonParticipantLedger.SavedParticipant(
                        playerId,
                        Math.max(0.0D, participantInput.getDoubleOr("LifetimeDamage", 0.0D)),
                        Math.max(0, participantInput.getIntOr("ActiveTicks", 0)),
                        Math.max(0.0D, participantInput.getDoubleOr("RecentThreat", 0.0D)),
                        participantInput.getLongOr("RecentThreatAt", 0L),
                        participantInput.getLongOr("ReachedAt", Long.MAX_VALUE)));
            } catch (IllegalArgumentException ignored) {
                // Ignore only the malformed participant; the encounter itself remains loadable.
            }
        }
        // One-way migration from the original lifetime-damage-only roster.
        for (ValueInput contributionInput : input.childrenListOrEmpty("DamageContributions")) {
            try {
                UUID playerId = UUID.fromString(contributionInput.getStringOr("Player", ""));
                double damage = Math.max(0.0D, contributionInput.getDoubleOr("Damage", 0.0D));
                long reachedAt = Math.max(0L, contributionInput.getLongOr("ReachedAt", 0L));
                if (savedParticipants.stream().noneMatch(saved -> saved.playerId().equals(playerId))) {
                    savedParticipants.add(new AncientDragonParticipantLedger.SavedParticipant(
                            playerId, damage, 0, 0.0D, 0L, reachedAt));
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore only the malformed contribution; the encounter itself remains loadable.
            }
        }
        UUID lockedTarget = parseUuid(input.getStringOr("LockedTarget", ""));
        participantLedger.restore(
                savedParticipants,
                input.getIntOr("MaxParticipantsSeen", scaledParticipants),
                input.getIntOr("ParticipantEmptyTicks", disengagedTicks),
                lockedTarget,
                input.getLongOr("TargetLockUntil", Long.MIN_VALUE));
        attackDirector.restoreHistory(parseAttackHistory(input.getStringOr("AttackHistory", "")));
        victoryEligiblePlayers.clear();
        for (ValueInput eligibleInput : input.childrenListOrEmpty("VictoryEligiblePlayers")) {
            UUID playerId = parseUuid(eligibleInput.getStringOr("Player", ""));
            if (playerId != null) {
                victoryEligiblePlayers.add(playerId);
            }
        }
        pendingCorpseExperience.clear();
        for (ValueInput pendingExperienceInput : input.childrenListOrEmpty("PendingCorpseExperience")) {
            UUID playerId = parseUuid(pendingExperienceInput.getStringOr("Player", ""));
            int experience = Math.max(0, pendingExperienceInput.getIntOr("Experience", 0));
            if (playerId != null && experience > 0) {
                pendingCorpseExperience.put(playerId, experience);
            }
        }
        pendingCorpseLoot.clear();
        for (ValueInput pendingLootInput : input.childrenListOrEmpty("PendingCorpseLoot")) {
            UUID playerId = parseUuid(pendingLootInput.getStringOr("Player", ""));
            CorpseHarvestNode node = CorpseHarvestNode.parse(pendingLootInput.getStringOr("Loot", ""));
            int count = Math.max(0, pendingLootInput.getIntOr("Count", 0));
            if (playerId != null && node != null && count > 0) {
                pendingCorpseLoot.computeIfAbsent(
                                playerId, ignored -> new EnumMap<>(CorpseHarvestNode.class))
                        .merge(node, count, Math::addExact);
            }
        }
        corpseHarvestProgress.clear();
        harvestedCorpseNodes.clear();
        for (ValueInput harvestInput : input.childrenListOrEmpty("CorpseHarvest")) {
            CorpseHarvestNode node = CorpseHarvestNode.parse(harvestInput.getStringOr("Node", ""));
            if (node == null) {
                continue;
            }
            int progress = Math.clamp(harvestInput.getIntOr("Progress", 0), 0, node.requiredHits());
            corpseHarvestProgress.put(node, progress);
            if (harvestInput.getIntOr("Harvested", 0) != 0 || progress >= node.requiredHits()) {
                harvestedCorpseNodes.add(node);
            }
        }
        initialAnimationPublished = false;
        bossEvent.setVisible(bossState != AncientDragonBossState.DORMANT
                && bossState != AncientDragonBossState.CORPSE);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("BossState", bossState.serializedName());
        output.putString("EncounterPhase", phase.serializedName());
        output.putInt("StateTicks", stateTicks);
        output.putInt("CorpseDissipationTicks", corpseDissipationTicks);
        output.putInt("AttackCooldown", attackCooldownTicks);
        output.putInt("AttackSequence", attackSequence);
        output.putString("CurrentAttack", currentAttack == null ? "" : currentAttack.name().toLowerCase(java.util.Locale.ROOT));
        output.putString("AttackDomain", currentAttackDomain.name().toLowerCase(java.util.Locale.ROOT));
        output.putString("PendingPhase", pendingPhase == null ? "" : pendingPhase.serializedName());
        output.putInt("PatrolWaypoint", SacredMountainFlightPath.normalizeIndex(patrolWaypointIndex));
        output.putInt("ApproachWaypoint", SacredMountainFlightPath.normalizeApproachIndex(approachWaypointIndex));
        output.putInt("DisengagedTicks", Math.clamp(disengagedTicks, 0, RETURN_TO_ROOST_DELAY_TICKS));
        output.putInt("ScaledParticipants", scaledParticipants);
        output.putInt("ActiveParticipants", currentActiveParticipants);
        output.putInt("AirAttacksThisCycle", airAttacksThisCycle);
        output.putInt("GroundCombatTicks", groundCombatTicksRemaining);
        output.putInt("ForcedLandingDelay", forcedLandingDelayTicks);
        output.putInt("ForcedLandingPending", forcedLandingPending ? 1 : 0);
        output.putInt("InitialTakeoff", initialTakeoff ? 1 : 0);
        output.putInt("PerchPending", perchPending ? 1 : 0);
        output.putInt("PerchApproachStage", perchApproachStage);
        output.putDouble("TakeoffTargetX", takeoffTarget.x);
        output.putDouble("TakeoffTargetY", takeoffTarget.y);
        output.putDouble("TakeoffTargetZ", takeoffTarget.z);
        output.putDouble("StabilityCurrent", stability.current());
        output.putDouble("StabilityMaximum", stability.maximum());
        output.putInt("StabilityForcedLandingUsed", stability.forcedLandingUsed() ? 1 : 0);
        output.putInt("DebugEncounter", debugEncounter ? 1 : 0);
        output.putInt("DebugHitboxes", debugHitboxes ? 1 : 0);
        output.putInt("DebugRoute", debugRoute ? 1 : 0);
        output.putInt("DebugDirector", debugDirector ? 1 : 0);
        output.putInt("MaxParticipantsSeen", participantLedger.maxParticipantsSeen());
        output.putInt("ParticipantEmptyTicks", participantLedger.emptyTicks());
        output.putString("LockedTarget", participantLedger.lockedTarget().map(UUID::toString).orElse(""));
        output.putLong("TargetLockUntil", participantLedger.targetLockUntilTick());
        output.putString("AttackHistory", attackDirector.history().stream()
                .map(attack -> attack.name().toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(",")));
        output.putDouble("EncounterOriginX", encounterOrigin.x);
        output.putDouble("EncounterOriginY", encounterOrigin.y);
        output.putDouble("EncounterOriginZ", encounterOrigin.z);
        output.putInt("EncounterQuarterTurns", encounterQuarterTurns);
        DragonQuaternion orientation = flightController.orientation();
        Vec3 velocity = flightController.velocity();
        Curve torso = flightController.torsoCurve();
        Curve tail = flightController.tailCurve();
        output.putDouble("PoseOrientationX", orientation.x());
        output.putDouble("PoseOrientationY", orientation.y());
        output.putDouble("PoseOrientationZ", orientation.z());
        output.putDouble("PoseOrientationW", orientation.w());
        output.putDouble("FlightVelocityX", velocity.x);
        output.putDouble("FlightVelocityY", velocity.y);
        output.putDouble("FlightVelocityZ", velocity.z);
        output.putDouble("TorsoCurvePitch", torso.pitchRadians());
        output.putDouble("TorsoCurveYaw", torso.yawRadians());
        output.putDouble("TorsoCurveRoll", torso.rollRadians());
        output.putDouble("TailCurvePitch", tail.pitchRadians());
        output.putDouble("TailCurveYaw", tail.yawRadians());
        output.putDouble("TailCurveRoll", tail.rollRadians());
        output.putInt("ManeuverCooldown", flightController.maneuverCooldown());
        DragonStagedPose staged = poseDynamics.snapshot();
        output.putInt("StagedTailEncoding", DragonStagedPose.TAIL_ENCODING_VERSION);
        for (int index = 0; index < DATA_STAGED_NECK.size(); index++) {
            output.putInt("StagedNeck" + index, staged.packedNeck(index));
        }
        for (int index = 0; index < DATA_STAGED_TAIL.size(); index++) {
            output.putInt("StagedTail" + index, staged.packedTail(index));
        }

        ValueOutput.ValueOutputList participantOutputs = output.childrenList("Participants");
        for (AncientDragonParticipantLedger.SavedParticipant participant : participantLedger.savedParticipants()) {
            ValueOutput participantOutput = participantOutputs.addChild();
            participantOutput.putString("Player", participant.playerId().toString());
            participantOutput.putDouble("LifetimeDamage", participant.lifetimeDamage());
            participantOutput.putInt("ActiveTicks", participant.activeTicks());
            participantOutput.putDouble("RecentThreat", participant.recentThreat());
            participantOutput.putLong("RecentThreatAt", participant.recentThreatAtTick());
            participantOutput.putLong("ReachedAt", participant.reachedAtTick());
        }
        ValueOutput.ValueOutputList eligibleOutputs = output.childrenList("VictoryEligiblePlayers");
        victoryEligiblePlayers.stream().sorted().forEach(playerId -> {
            ValueOutput eligibleOutput = eligibleOutputs.addChild();
            eligibleOutput.putString("Player", playerId.toString());
        });
        ValueOutput.ValueOutputList pendingExperienceOutputs = output.childrenList("PendingCorpseExperience");
        pendingCorpseExperience.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(pending -> {
            ValueOutput pendingExperienceOutput = pendingExperienceOutputs.addChild();
            pendingExperienceOutput.putString("Player", pending.getKey().toString());
            pendingExperienceOutput.putInt("Experience", pending.getValue());
        });
        ValueOutput.ValueOutputList pendingLootOutputs = output.childrenList("PendingCorpseLoot");
        pendingCorpseLoot.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(pending ->
                pending.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(loot -> {
                    ValueOutput pendingLootOutput = pendingLootOutputs.addChild();
                    pendingLootOutput.putString("Player", pending.getKey().toString());
                    pendingLootOutput.putString("Loot", loot.getKey().serializedName());
                    pendingLootOutput.putInt("Count", loot.getValue());
                }));
        ValueOutput.ValueOutputList harvestOutputs = output.childrenList("CorpseHarvest");
        for (CorpseHarvestNode node : CorpseHarvestNode.values()) {
            ValueOutput harvestOutput = harvestOutputs.addChild();
            harvestOutput.putString("Node", node.serializedName());
            harvestOutput.putInt("Progress", corpseHarvestProgress.getOrDefault(node, 0));
            harvestOutput.putInt("Harvested", harvestedCorpseNodes.contains(node) ? 1 : 0);
        }
    }

    private static Phase parsePhase(String value) {
        for (Phase candidate : Phase.values()) {
            if (candidate.serializedName().equals(value)) {
                return candidate;
            }
        }
        return Phase.MOUNTAIN;
    }

    private static Phase parseOptionalPhase(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parsePhase(value);
    }

    private static Attack parseAttack(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (Attack attack : Attack.values()) {
            if (attack.name().equalsIgnoreCase(value)) {
                return attack;
            }
        }
        return null;
    }

    private static AttackDomain parseAttackDomain(String value) {
        return "ground".equalsIgnoreCase(value) ? AttackDomain.GROUND : AttackDomain.AIR;
    }

    private static List<Attack> parseAttackHistory(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<Attack> attacks = new ArrayList<>();
        for (String token : value.split(",")) {
            Attack attack = parseAttack(token);
            if (attack != null) {
                attacks.add(attack);
            }
        }
        return List.copyOf(attacks);
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record FlightTrailSample(Vec3 position, int createdTick) {
    }

    private enum CorpseHarvestNode {
        HORNS("horns", 8),
        SCALES("scales", 12),
        LEFT_MEMBRANE("left_membrane", 10),
        RIGHT_MEMBRANE("right_membrane", 10),
        BONES("bones", 12),
        HEART("heart", 16);

        private final String serializedName;
        private final int requiredHits;

        CorpseHarvestNode(String serializedName, int requiredHits) {
            this.serializedName = serializedName;
            this.requiredHits = requiredHits;
        }

        String serializedName() {
            return serializedName;
        }

        int requiredHits() {
            return requiredHits;
        }

        int experiencePerEligibleParticipant() {
            return AncientDragonCorpseExperience.forHarvestNode(serializedName);
        }

        static CorpseHarvestNode parse(String value) {
            for (CorpseHarvestNode node : values()) {
                if (node.serializedName.equals(value)) {
                    return node;
                }
            }
            return null;
        }
    }
}

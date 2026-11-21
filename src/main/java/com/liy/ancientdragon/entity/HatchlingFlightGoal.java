package com.liy.ancientdragon.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A bounded sightseeing flight, with vanilla navigation left in charge of catching up to the owner. */
final class HatchlingFlightGoal extends Goal {
    private static final int SECTORS_PER_LAP = 10;
    private static final double START_DISTANCE_SQUARED = 6.5 * 6.5;
    private static final double CONTINUE_DISTANCE_SQUARED = 8.0 * 8.0;
    private static final double TAU = Math.PI * 2;
    private final AncientDragonHatchling hatchling;
    private List<Vec3> route = List.of();
    private Vec3 perch;
    private Vec3 velocity = Vec3.ZERO;
    private int waypoint;
    private int flightTicks;
    private int nextTakeoff = 120;
    private boolean landing;
    private boolean gliding;

    HatchlingFlightGoal(AncientDragonHatchling hatchling) {
        this.hatchling = hatchling;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    void scheduleFirstFlight() {
        nextTakeoff = hatchling.tickCount + 70 + hatchling.getRandom().nextInt(65);
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = hatchling.getOwner();
        if (hatchling.tickCount < nextTakeoff || hatchling.isOrderedToSit() || !hatchling.isTame()
                || !hatchling.onGround() || !canFly() || !usableOwner(owner)
                || hatchling.distanceToSqr(owner) > START_DISTANCE_SQUARED
                || Math.abs(owner.getY() - hatchling.getY()) > 2.0) {
            return false;
        }
        RandomSource random = hatchling.getRandom();
        nextTakeoff = hatchling.tickCount + 90 + random.nextInt(60);
        ServerLevel level = (ServerLevel) hatchling.level();
        perch = hatchling.position().add(0.0, 0.06, 0.0);
        if (!hasSafeFloor(level, perch)) {
            return false;
        }

        double angle = Math.atan2(perch.z - owner.getZ(), perch.x - owner.getX());
        if (perch.subtract(owner.position()).horizontalDistanceSqr() < 0.25) {
            angle = random.nextDouble() * TAU;
        }
        int turn = random.nextBoolean() ? 1 : -1;
        double radius = 3.7 + random.nextDouble() * 0.8;
        double height = 2.6 + random.nextDouble() * 0.6;
        // A lower and narrower route lets the dragon fly under a high roof or around a nearby tree.
        for (int attempt = 0; attempt < 3; attempt++) {
            var candidate = makeRoute(owner.position(), perch, angle, turn,
                    radius - 0.5 * attempt, height - 0.45 * attempt);
            if (clearRoute(level, candidate)) {
                route = candidate;
                return true;
            }
        }
        return false;
    }

    private static List<Vec3> makeRoute(Vec3 owner, Vec3 perch, double angle, int turn, double radius, double height) {
        double altitude = Math.max(owner.y, perch.y) + height;
        Vec3 lift = new Vec3(perch.x, altitude - 0.25, perch.z);
        List<Vec3> points = new ArrayList<>();
        points.add(lift);
        for (int step = 0; step <= SECTORS_PER_LAP; step++) {
            double phase = TAU * step / SECTORS_PER_LAP;
            double heading = angle + turn * phase;
            double contour = radius + Math.sin(phase) * 0.3;
            points.add(new Vec3(owner.x + Math.cos(heading) * contour,
                    altitude + Math.sin(phase - 0.2) * 0.48, owner.z + Math.sin(heading) * contour));
        }
        points.add(lift);
        points.add(perch);
        return List.copyOf(points);
    }

    private boolean clearRoute(ServerLevel level, List<Vec3> points) {
        Vec3 previous = hatchling.position();
        for (Vec3 point : points) {
            if (!clearLeg(level, previous, point)) {
                return false;
            }
            previous = point;
        }
        return true;
    }

    private boolean clearLeg(ServerLevel level, Vec3 from, Vec3 to) {
        int samples = Math.max(1, Mth.ceil(from.distanceTo(to) / 0.55));
        for (int i = 1; i <= samples; i++) {
            if (!hasClearance(level, from.lerp(to, (double) i / samples))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasClearance(ServerLevel level, Vec3 feet) {
        // Reserve a little extra room beside the body for its wings.
        AABB box = hatchling.getBoundingBox().move(feet.subtract(hatchling.position())).inflate(0.24, 0.0, 0.24);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        if (level.isOutsideBuildHeight(min) || level.isOutsideBuildHeight(max) || !level.getWorldBorder().isWithinBounds(box)) {
            return false;
        }
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return !level.getBlockCollisions(hatchling, box).iterator().hasNext() && !level.containsAnyLiquid(box);
    }

    @Override
    public void start() {
        waypoint = 0;
        flightTicks = 0;
        landing = false;
        velocity = Vec3.ZERO;
        hatchling.getNavigation().stop();
        hatchling.playSound(SoundEvents.ENDER_DRAGON_FLAP, 0.20F, 1.5F);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity owner = hatchling.getOwner();
        return isFlying() && canFly() && flightTicks < 20 * 20 && (landing
                || hatchling.isOrderedToSit()
                || usableOwner(owner) && hatchling.distanceToSqr(owner) <= CONTINUE_DISTANCE_SQUARED);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        var level = (ServerLevel) hatchling.level();
        flightTicks++;
        if (hatchling.isOrderedToSit() && !landing) {
            startLanding(level);
        }
        if (!isFlying()) {
            return;
        }

        Vec3 target = route.get(waypoint);
        boolean last = waypoint == route.size() - 1;
        Vec3 delta = target.subtract(hatchling.position());
        if (delta.lengthSqr() <= (last ? 0.035 : 0.20)) {
            waypoint++;
            if (last) {
                stop();
                return;
            }
            target = route.get(waypoint);
            last = waypoint == route.size() - 1;
            delta = target.subtract(hatchling.position());
        }
        gliding = !landing && !last && (waypoint >= 4 && waypoint <= 6 || waypoint >= 8 && waypoint <= 9);
        double cruise = hatchling.getAttributeValue(Attributes.FLYING_SPEED) * (gliding ? 0.68 : 0.59);
        double speed = Math.min(cruise, Math.max(0.045, delta.length() * (last ? 0.43 : 0.78)));
        Vec3 desired = delta.normalize().scale(speed);
        velocity = velocity.lerp(desired, last ? 0.38 : 0.32);
        if (!hasClearance(level, hatchling.position().add(velocity))) {
            stop();
        }
    }

    private void startLanding(ServerLevel level) {
        Vec3 here = hatchling.position();
        int top = Mth.floor(here.y);
        for (int offset = 0; offset <= 7; offset++) {
            Vec3 candidate = new Vec3(here.x, top - offset + 0.06, here.z);
            if (candidate.y <= here.y && hasSafeFloor(level, candidate) && clearLeg(level, here, candidate)) {
                setLandingTarget(candidate);
                return;
            }
        }
        // If the owner calls it down over an edge, try the original ledge before handing off to gravity.
        if (hasSafeFloor(level, perch) && clearLeg(level, here, perch)) {
            setLandingTarget(perch);
        } else {
            stop();
        }
    }

    private void setLandingTarget(Vec3 point) {
        route = List.of(point);
        waypoint = 0;
        landing = true;
    }

    private static boolean hasSafeFloor(ServerLevel level, Vec3 feet) {
        BlockPos floor = BlockPos.containing(feet).below();
        return level.getChunkSource().hasChunk(floor.getX() >> 4, floor.getZ() >> 4)
                && level.getFluidState(floor).isEmpty()
                && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    private boolean canFly() {
        return hatchling.isAlive() && !hatchling.isPassenger() && !hatchling.isLeashed() && !hatchling.isInWater();
    }

    private boolean usableOwner(LivingEntity owner) {
        return owner != null && owner.isAlive() && !owner.isSpectator() && owner.level() == hatchling.level();
    }

    boolean isFlying() {
        return waypoint < route.size();
    }

    boolean isGliding() {
        return isFlying() && gliding;
    }

    /** Movement is still resolved by normal Minecraft collision handling. */
    void travel() {
        if (velocity.horizontalDistanceSqr() > 0.0004) {
            float heading = (float) Math.toDegrees(Mth.atan2(velocity.z, velocity.x)) - 90.0F;
            float yaw = hatchling.getYRot() + Mth.clamp(Mth.wrapDegrees(heading - hatchling.getYRot()), -11.5F, 11.5F);
            hatchling.setYRot(yaw);
            hatchling.setYBodyRot(yaw);
            hatchling.setYHeadRot(yaw);
        }
        float pitch = (float) -Math.toDegrees(Mth.atan2(velocity.y, Math.max(0.07, velocity.horizontalDistance())));
        hatchling.setXRot(Mth.lerp(0.24F, hatchling.getXRot(), Mth.clamp(pitch, -24.0F, 21.0F)));
        Vec3 before = hatchling.position();
        hatchling.move(MoverType.SELF, velocity);
        hatchling.setDeltaMovement(hatchling.position().subtract(before));
    }

    @Override
    public void stop() {
        if (route.isEmpty()) {
            return;
        }
        route = List.of();
        waypoint = 0;
        velocity = Vec3.ZERO;
        gliding = false;
        landing = false;
        hatchling.setDeltaMovement(Vec3.ZERO);
        hatchling.setNoGravity(false);
        nextTakeoff = hatchling.tickCount + 290 + hatchling.getRandom().nextInt(310);
    }
}

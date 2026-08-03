package com.liy.ancientdragon.entity;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Shared, deterministic geometry and timing for the server-authoritative dragon attacks. */
final class AncientDragonAttackGeometry {
    static final double MELEE_CONTACT_MARGIN_BLOCKS = 2.5D;
    static final double BITE_FORWARD_REACH_BLOCKS = 28.0D;
    static final double BITE_CONTACT_RADIUS_BLOCKS = 7.5D;
    static final double WING_SLAM_SHOCKWAVE_RADIUS_BLOCKS = 42.0D;
    static final double WING_SLAM_VERTICAL_REACH_BLOCKS = 18.0D;

    static final int TAIL_CONTACT_START_TICK = 16;
    static final int TAIL_CONTACT_END_TICK = 64;
    static final double TAIL_HORIZONTAL_LAUNCH = 2.4D;
    static final double TAIL_VERTICAL_LAUNCH = 1.05D;

    static final int STORM_TELEGRAPH_START_TICK = 32;
    static final int STORM_TARGET_LOCK_TICK = 48;
    static final int STORM_STRIKE_TICK = 58;
    static final double STORM_STRIKE_RADIUS_BLOCKS = 5.0D;

    static final int SOLAR_PREVIEW_START_TICK = 32;
    static final int SOLAR_ACTIVE_START_TICK = 54;
    static final int SOLAR_ACTIVE_END_TICK = 94;
    static final double SOLAR_BEAM_RADIUS_BLOCKS = 4.5D;
    static final double SOLAR_MINIMUM_BEAM_LENGTH = 90.0D;
    static final double SOLAR_MAXIMUM_BEAM_LENGTH = 280.0D;
    static final double SOLAR_SWEEP_HALF_ANGLE_RADIANS = Math.toRadians(35.0D);

    static final int[] LIGHTNING_CHAIN_STRIKE_TICKS = {40, 62, 84};
    static final int LIGHTNING_CHAIN_TELEGRAPH_TICKS = 18;
    static final int LIGHTNING_CHAIN_LOCK_LEAD_TICKS = 8;
    static final double LIGHTNING_CHAIN_RADIUS_BLOCKS = 4.25D;

    static final int WIND_BLADE_TELEGRAPH_START_TICK = 18;
    static final int[] WIND_BLADE_FIRE_TICKS = {36, 48, 60};
    static final double WIND_BLADE_LENGTH_BLOCKS = 120.0D;
    static final double WIND_BLADE_RADIUS_BLOCKS = 3.25D;
    static final double WIND_BLADE_SPREAD_RADIANS = Math.toRadians(13.0D);

    static final int RIFT_CLAW_TARGET_LOCK_TICK = 28;
    static final int RIFT_CLAW_IMPACT_TICK = 52;
    static final double RIFT_CLAW_LENGTH_BLOCKS = 72.0D;
    static final double RIFT_CLAW_RADIUS_BLOCKS = 4.5D;
    static final double RIFT_CLAW_SPREAD_RADIANS = Math.toRadians(11.0D);

    static final int STORM_CAGE_TARGET_LOCK_TICK = 28;
    static final int STORM_CAGE_RING_STRIKE_TICK = 58;
    static final int STORM_CAGE_CENTER_STRIKE_TICK = 78;
    static final double STORM_CAGE_RING_RADIUS_BLOCKS = 13.0D;
    static final double STORM_CAGE_PILLAR_RADIUS_BLOCKS = 3.0D;
    static final double STORM_CAGE_CENTER_RADIUS_BLOCKS = 6.0D;
    static final int STORM_CAGE_PILLARS = 8;

    private AncientDragonAttackGeometry() {
    }

    static boolean tailContactActive(int tick) {
        return tick >= TAIL_CONTACT_START_TICK && tick <= TAIL_CONTACT_END_TICK;
    }

    static boolean sweptContact(Vec3 target, Vec3 previous, Vec3 current, double radius) {
        if (target == null || previous == null || current == null || !(radius >= 0.0D)) {
            throw new IllegalArgumentException("Swept-contact values must be present and non-negative");
        }
        Vec3 segment = current.subtract(previous);
        double lengthSquared = segment.lengthSqr();
        if (!(lengthSquared > 0.0D)) {
            return target.distanceToSqr(current) <= radius * radius;
        }
        double fraction = Math.clamp(
                target.subtract(previous).dot(segment) / lengthSquared, 0.0D, 1.0D);
        return target.distanceToSqr(previous.add(segment.scale(fraction))) <= radius * radius;
    }

    static boolean groundShockwaveContact(
            Vec3 origin, Vec3 target, double horizontalRadius, double verticalReach) {
        if (origin == null || target == null || !(horizontalRadius >= 0.0D) || !(verticalReach >= 0.0D)) {
            throw new IllegalArgumentException("Shockwave values must be present and non-negative");
        }
        double dx = target.x - origin.x;
        double dz = target.z - origin.z;
        return Math.abs(target.y - origin.y) <= verticalReach
                && dx * dx + dz * dz <= horizontalRadius * horizontalRadius;
    }

    static double stormTelegraphProgress(int tick) {
        return normalizedProgress(tick, STORM_TELEGRAPH_START_TICK, STORM_STRIKE_TICK);
    }

    static boolean stormTargetTracksPlayer(int tick) {
        return tick >= STORM_TELEGRAPH_START_TICK && tick <= STORM_TARGET_LOCK_TICK;
    }

    static Vec3 leadTarget(Vec3 position, Vec3 velocity, int leadTicks, double maximumLead) {
        if (position == null || velocity == null || leadTicks < 0 || !(maximumLead >= 0.0D)) {
            throw new IllegalArgumentException("Target prediction values must be present and non-negative");
        }
        Vec3 lead = velocity.scale(leadTicks);
        if (lead.lengthSqr() > maximumLead * maximumLead) {
            lead = lead.normalize().scale(maximumLead);
        }
        return position.add(lead);
    }

    static boolean lightningChainTracksTarget(int tick) {
        for (int strikeTick : LIGHTNING_CHAIN_STRIKE_TICKS) {
            int previousStrike = previousStrikeTick(strikeTick);
            if (tick > previousStrike && tick <= strikeTick - LIGHTNING_CHAIN_LOCK_LEAD_TICKS) {
                return true;
            }
        }
        return false;
    }

    static boolean lightningChainTelegraphActive(int tick) {
        for (int strikeTick : LIGHTNING_CHAIN_STRIKE_TICKS) {
            if (tick >= strikeTick - LIGHTNING_CHAIN_TELEGRAPH_TICKS && tick < strikeTick) {
                return true;
            }
        }
        return false;
    }

    static double lightningChainTelegraphProgress(int tick) {
        for (int strikeTick : LIGHTNING_CHAIN_STRIKE_TICKS) {
            int startTick = strikeTick - LIGHTNING_CHAIN_TELEGRAPH_TICKS;
            if (tick >= startTick && tick <= strikeTick) {
                return normalizedProgress(tick, startTick, strikeTick);
            }
        }
        return 0.0D;
    }

    static boolean lightningChainStrikes(int tick) {
        return containsTick(LIGHTNING_CHAIN_STRIKE_TICKS, tick);
    }

    static boolean windBladeFires(int tick) {
        return containsTick(WIND_BLADE_FIRE_TICKS, tick);
    }

    static List<Vec3> spreadDirections(Vec3 direction, double spreadRadians) {
        if (direction == null || direction.horizontalDistanceSqr() <= 1.0e-12D
                || !Double.isFinite(spreadRadians) || spreadRadians < 0.0D) {
            throw new IllegalArgumentException("Spread direction must be horizontal, finite and non-zero");
        }
        Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z).normalize();
        return List.of(
                rotateHorizontal(horizontal, -spreadRadians),
                horizontal,
                rotateHorizontal(horizontal, spreadRadians));
    }

    static List<Vec3> ringPositions(Vec3 center, double radius, int count) {
        if (center == null || !(radius > 0.0D) || count < 3) {
            throw new IllegalArgumentException("Ring values must be present and positive");
        }
        java.util.ArrayList<Vec3> positions = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double angle = index * Math.TAU / count;
            positions.add(center.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius));
        }
        return List.copyOf(positions);
    }

    static Vec3 solarSweepTarget(Vec3 pivot, Vec3 lockedCenter, int tick) {
        double progress = normalizedProgress(tick, SOLAR_ACTIVE_START_TICK, SOLAR_ACTIVE_END_TICK);
        double eased = progress * progress * (3.0D - (2.0D * progress));
        double angle = -SOLAR_SWEEP_HALF_ANGLE_RADIANS
                + (SOLAR_SWEEP_HALF_ANGLE_RADIANS * 2.0D * eased);
        Vec3 relative = lockedCenter.subtract(pivot);
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        double rotatedX = (relative.x * cosine) - (relative.z * sine);
        double rotatedZ = (relative.x * sine) + (relative.z * cosine);
        return new Vec3(pivot.x + rotatedX, lockedCenter.y, pivot.z + rotatedZ);
    }

    static double solarBeamLength(Vec3 beamStart, Vec3 aimPoint) {
        return Math.clamp(
                beamStart.distanceTo(aimPoint) + 18.0D,
                SOLAR_MINIMUM_BEAM_LENGTH,
                SOLAR_MAXIMUM_BEAM_LENGTH);
    }

    private static double normalizedProgress(int tick, int startTick, int endTick) {
        return Math.clamp((tick - startTick) / (double) (endTick - startTick), 0.0D, 1.0D);
    }

    private static int previousStrikeTick(int strikeTick) {
        int previous = -1;
        for (int candidate : LIGHTNING_CHAIN_STRIKE_TICKS) {
            if (candidate >= strikeTick) {
                return previous;
            }
            previous = candidate;
        }
        return previous;
    }

    private static boolean containsTick(int[] ticks, int tick) {
        for (int candidate : ticks) {
            if (candidate == tick) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 rotateHorizontal(Vec3 direction, double radians) {
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Vec3(
                direction.x * cosine - direction.z * sine,
                0.0D,
                direction.x * sine + direction.z * cosine);
    }
}

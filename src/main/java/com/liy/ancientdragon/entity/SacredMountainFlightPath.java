package com.liy.ancientdragon.entity;

import com.liy.ancientdragon.boss.AncientDragonEncounterRules.Phase;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Authored patrol, return, and combat-platform geometry stored from the live roost origin. */
final class SacredMountainFlightPath {
    // The dormant clip's forward axis faces the authored mountainside at Minecraft yaw 90°.
    // 270° turns the resting mesh outward, making the dragon visibly lie backwards in its roost.
    private static final float AUTHORED_ROOST_YAW = 90.0F;
    static final int ASSEMBLY_INDEX = 0;
    static final double ARRIVAL_RADIUS = 22.0D;
    static final double APPROACH_ARRIVAL_RADIUS = 14.0D;
    private static final double TAKEOFF_ANIMATION_SPEED = 0.32D;
    private static final double PROTECTED_CLIMB_SPEED = 0.68D;

    /*
     * The marked green flight box, measured from the live dragon rest anchor. Drawing thickness
     * accounts for the one-block rounding at each edge. Destinations and actual airborne root
     * movement are both constrained to this rectangle by AncientDragonEntity.
     */
    private static final double FLIGHT_MINIMUM_X = -288.0D;
    private static final double FLIGHT_MAXIMUM_X = 100.0D;
    private static final double FLIGHT_MINIMUM_Z = -179.0D;
    private static final double FLIGHT_MAXIMUM_Z = 188.0D;

    /** The high point is only an exit gate; normal patrol immediately descends to Y ~= 200. */
    private static final Vec3 TAKEOFF_CLEARANCE_OFFSET = new Vec3(0.0D, 141.0D, 0.0D);

    /*
     * White platform center in the frozen live asset. Its surface is local Y=209. The current
     * oriented collision rig needs a 21-block root lift there: (38,230,0) versus dragonRest
     * (139,205,1).
     */
    private static final Vec3 COMBAT_PLATFORM_ROOT_OFFSET = new Vec3(-101.0D, 25.0D, -1.0D);
    private static final double PLATFORM_MINIMUM_X = -165.0D;
    private static final double PLATFORM_MAXIMUM_X = -37.0D;
    private static final double PLATFORM_MINIMUM_Z = -51.0D;
    private static final double PLATFORM_MAXIMUM_Z = 51.0D;

    /** Exact authored summit coordinates measured from the extracted dragon rest anchor. */
    private static final Perch STORM_PERCH = perch("storm_peak", -227, 79, -169, 90.0F);
    private static final Perch CROWN_PERCH = perch("broken_crown_inner", -274, 34, 111, 90.0F);
    private static final Perch SOLAR_PERCH = perch("solar_ridge", -51, 40, 175, 90.0F);

    /**
     * Stable aerial holds above the three authored summits in the frozen asset.
     * The 36-block lift keeps the full wing rig clear while making the dragon read as stationary
     * over the summit instead of as another patrol pass.
     */
    private static final List<PeakHold> PEAK_HOLDS = List.of(
            peakHold("storm", -227, 114, -169),
            peakHold("crown", -274, 60, 111),
            peakHold("solar", -51, 75, 175));

    private static final List<Waypoint> WAYPOINTS = List.of(
            waypoint("A", "low_assembly", 0, 60, 0, Segment.ASSEMBLY, 0.58D),
            waypoint("S1", "inner_northeast", 40, 62, -80, Segment.STORM, 0.56D),
            waypoint("S2", "inner_north", -40, 58, -140, Segment.STORM, 0.56D),
            waypoint("S3", "storm_inner_arc", -140, 62, -165, Segment.STORM, 0.56D),
            waypoint("S4", "storm_south_saddle", -230, 60, -120, Segment.STORM, 0.54D),
            waypoint("C1", "crown_inner_north", -270, 58, -40, Segment.CROWN, 0.52D),
            waypoint("C2", "crown_inner_west", -260, 56, 60, Segment.CROWN, 0.52D),
            waypoint("C3", "crown_inner_south", -210, 54, 130, Segment.CROWN, 0.52D),
            waypoint("C4", "platform_southwest", -130, 56, 165, Segment.CROWN, 0.54D),
            waypoint("T1", "solar_inner_south", -50, 58, 160, Segment.SOLAR, 0.54D),
            waypoint("T2", "solar_inner_arc", 20, 60, 120, Segment.SOLAR, 0.56D),
            waypoint("T3", "roost_southeast", 70, 62, 50, Segment.SOLAR, 0.56D),
            waypoint("T4", "roost_northeast", 55, 60, -20, Segment.SOLAR, 0.56D));

    /**
     * East-side return corridor.
     *
     * <p>The outer turn is deliberately complete before the descent begins. The old diagonal
     * E1-to-E2 drop cut across the east cliff with an open left wing. The return instead rounds
     * the north side of that cliff and uses the natural north notch into the roost.</p>
     */
    private static final List<ApproachWaypoint> APPROACH = List.of(
            approach("N3", "north_notch_entry", 70, 86, -140, 0.30D),
            approach("N4", "north_notch_upper", 30, 76, -120, 0.26D),
            approach("N5", "north_notch_middle", 5, 71, -90, 0.22D),
            approach("N6", "north_notch_inner", 0, 56, -60, 0.20D),
            approach("N7", "roost_north_gate", 0, 36, -30, 0.18D),
            approach("N8", "roost_final_align", 10, 21, -10, 0.16D),
            approach("N9", "west_final", 18, 8, 0, 0.14D));

    /** Dedicated descent to the white combat platform; it never reuses the roost touchdown. */
    private static final List<ApproachWaypoint> COMBAT_APPROACH = List.of(
            approach("P1", "platform_northwest_entry", -160, 60, -80, 0.34D),
            approach("P2", "platform_upper_descent", -150, 48, -45, 0.30D),
            approach("P3", "platform_middle_descent", -130, 36, -20, 0.26D),
            approach("P4", "platform_final_align", -91, 40, -11, 0.22D),
            approach("P5", "platform_landing_gate", -83, 27, -1, 0.18D));

    private SacredMountainFlightPath() {
    }

    static int size() {
        return WAYPOINTS.size();
    }

    static Waypoint waypoint(int index) {
        return WAYPOINTS.get(normalizeIndex(index));
    }

    static Waypoint waypoint(int index, int quarterTurns) {
        Waypoint authored = waypoint(index);
        return new Waypoint(
                authored.id(),
                authored.name(),
                rotateOffset(authored.offset(), quarterTurns),
                authored.segment(),
                authored.maximumSpeed());
    }

    static Vec3 worldPosition(Vec3 dormantOrigin, int index) {
        return worldPosition(dormantOrigin, index, 0);
    }

    static Vec3 worldPosition(Vec3 dormantOrigin, int index, int quarterTurns) {
        if (dormantOrigin == null) {
            throw new IllegalArgumentException("Dormant origin is required");
        }
        return dormantOrigin.add(waypoint(index, quarterTurns).offset());
    }

    static int nextIndex(int index) {
        return (normalizeIndex(index) + 1) % WAYPOINTS.size();
    }

    static Vec3 takeoffWorldPosition(Vec3 dormantOrigin) {
        return takeoffWorldPosition(dormantOrigin, 0);
    }

    static Vec3 takeoffWorldPosition(Vec3 dormantOrigin, int quarterTurns) {
        if (dormantOrigin == null) {
            throw new IllegalArgumentException("Dormant origin is required");
        }
        return dormantOrigin.add(rotateOffset(TAKEOFF_CLEARANCE_OFFSET, quarterTurns));
    }

    static double takeoffMaximumSpeed(boolean animationComplete) {
        return animationComplete ? PROTECTED_CLIMB_SPEED : TAKEOFF_ANIMATION_SPEED;
    }

    static boolean takeoffClearForCruise(
            Vec3 position, Vec3 dormantOrigin, boolean animationComplete) {
        return takeoffClearForCruise(position, dormantOrigin, animationComplete, 0);
    }

    static boolean takeoffClearForCruise(
            Vec3 position, Vec3 dormantOrigin, boolean animationComplete, int quarterTurns) {
        if (position == null) {
            throw new IllegalArgumentException("Takeoff position is required");
        }
        return animationComplete
                && position.distanceToSqr(takeoffWorldPosition(dormantOrigin, quarterTurns))
                        <= ARRIVAL_RADIUS * ARRIVAL_RADIUS;
    }

    static Perch perchFor(Phase phase) {
        return perchFor(phase, 0);
    }

    static Perch perchFor(Phase phase, int quarterTurns) {
        Perch authored = switch (phase) {
            case MOUNTAIN -> CROWN_PERCH;
            case STORM -> STORM_PERCH;
            case SOLAR -> SOLAR_PERCH;
        };
        return new Perch(
                authored.id(),
                rotateOffset(authored.rootOffset(), quarterTurns),
                rotateOffset(authored.skyApproachOffset(), quarterTurns),
                rotateYaw(authored.yaw(), quarterTurns));
    }

    private static Perch perch(String id, double x, double y, double z, float yaw) {
        Vec3 root = new Vec3(x, y, z);
        return new Perch(id, root, root.add(0.0D, 64.0D, 0.0D), yaw);
    }

    static int normalizeIndex(int index) {
        return Math.floorMod(index, WAYPOINTS.size());
    }

    static int peakHoldCount() {
        return PEAK_HOLDS.size();
    }

    static PeakHold peakHold(int index) {
        return PEAK_HOLDS.get(Math.floorMod(index, PEAK_HOLDS.size()));
    }

    static Vec3 nearestPeakHoldWorldPosition(Vec3 dormantOrigin, Vec3 currentPosition) {
        return nearestPeakHoldWorldPosition(dormantOrigin, currentPosition, 0);
    }

    static Vec3 nearestPeakHoldWorldPosition(
            Vec3 dormantOrigin, Vec3 currentPosition, int quarterTurns) {
        if (dormantOrigin == null || currentPosition == null) {
            throw new IllegalArgumentException("Dormant origin and current position are required");
        }
        PeakHold nearest = PEAK_HOLDS.getFirst();
        double nearestDistance = horizontalDistanceSquared(
                currentPosition, dormantOrigin.add(rotateOffset(nearest.offset(), quarterTurns)));
        for (int index = 1; index < PEAK_HOLDS.size(); index++) {
            PeakHold candidate = PEAK_HOLDS.get(index);
            double distance = horizontalDistanceSquared(
                    currentPosition, dormantOrigin.add(rotateOffset(candidate.offset(), quarterTurns)));
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return dormantOrigin.add(rotateOffset(nearest.offset(), quarterTurns));
    }

    static int approachSize() {
        return APPROACH.size();
    }

    static ApproachWaypoint approachWaypoint(int index) {
        return APPROACH.get(normalizeApproachIndex(index));
    }

    static ApproachWaypoint approachWaypoint(int index, int quarterTurns) {
        ApproachWaypoint authored = approachWaypoint(index);
        return new ApproachWaypoint(
                authored.id(),
                authored.name(),
                rotateOffset(authored.offset(), quarterTurns),
                authored.maximumSpeed());
    }

    static Vec3 approachWorldPosition(Vec3 dormantOrigin, int index) {
        return approachWorldPosition(dormantOrigin, index, 0);
    }

    static Vec3 approachWorldPosition(Vec3 dormantOrigin, int index, int quarterTurns) {
        if (dormantOrigin == null) {
            throw new IllegalArgumentException("Dormant origin is required");
        }
        return dormantOrigin.add(approachWaypoint(index, quarterTurns).offset());
    }

    static int nextApproachIndex(int index) {
        return normalizeApproachIndex(index) + 1;
    }

    static int normalizeApproachIndex(int index) {
        return Math.clamp(index, 0, APPROACH.size() - 1);
    }

    static int combatApproachSize() {
        return COMBAT_APPROACH.size();
    }

    static ApproachWaypoint combatApproachWaypoint(int index, int quarterTurns) {
        ApproachWaypoint authored = COMBAT_APPROACH.get(normalizeCombatApproachIndex(index));
        return new ApproachWaypoint(
                authored.id(),
                authored.name(),
                rotateOffset(authored.offset(), quarterTurns),
                authored.maximumSpeed());
    }

    static Vec3 combatApproachWorldPosition(Vec3 dormantOrigin, int index, int quarterTurns) {
        if (dormantOrigin == null) {
            throw new IllegalArgumentException("Dormant origin is required");
        }
        return dormantOrigin.add(combatApproachWaypoint(index, quarterTurns).offset());
    }

    static int nextCombatApproachIndex(int index) {
        return normalizeCombatApproachIndex(index) + 1;
    }

    static int normalizeCombatApproachIndex(int index) {
        return Math.clamp(index, 0, COMBAT_APPROACH.size() - 1);
    }

    static Vec3 combatPlatformWorldPosition(Vec3 dormantOrigin, int quarterTurns) {
        if (dormantOrigin == null) {
            throw new IllegalArgumentException("Dormant origin is required");
        }
        return dormantOrigin.add(rotateOffset(COMBAT_PLATFORM_ROOT_OFFSET, quarterTurns));
    }

    static Vec3 deathApproachWorldPosition(Vec3 dormantOrigin, int quarterTurns) {
        Vec3 approach = COMBAT_PLATFORM_ROOT_OFFSET.add(24.0D, 12.0D, 0.0D);
        return dormantOrigin.add(rotateOffset(approach, quarterTurns));
    }

    static boolean isInsideCombatPlatform(Vec3 position, Vec3 dormantOrigin, int quarterTurns) {
        Vec3 local = rotateOffset(position.subtract(dormantOrigin), -quarterTurns);
        return local.x >= PLATFORM_MINIMUM_X && local.x <= PLATFORM_MAXIMUM_X
                && local.z >= PLATFORM_MINIMUM_Z && local.z <= PLATFORM_MAXIMUM_Z;
    }

    static boolean isInsideFlightBounds(Vec3 position, Vec3 dormantOrigin, int quarterTurns) {
        Vec3 local = rotateOffset(position.subtract(dormantOrigin), -quarterTurns);
        return local.x >= FLIGHT_MINIMUM_X && local.x <= FLIGHT_MAXIMUM_X
                && local.z >= FLIGHT_MINIMUM_Z && local.z <= FLIGHT_MAXIMUM_Z;
    }

    static Vec3 clampToFlightBounds(Vec3 position, Vec3 dormantOrigin, int quarterTurns) {
        if (position == null || dormantOrigin == null) {
            throw new IllegalArgumentException("Flight position and dormant origin are required");
        }
        Vec3 local = rotateOffset(position.subtract(dormantOrigin), -quarterTurns);
        Vec3 clamped = new Vec3(
                Math.clamp(local.x, FLIGHT_MINIMUM_X, FLIGHT_MAXIMUM_X),
                local.y,
                Math.clamp(local.z, FLIGHT_MINIMUM_Z, FLIGHT_MAXIMUM_Z));
        return dormantOrigin.add(rotateOffset(clamped, quarterTurns));
    }

    private static Waypoint waypoint(
            String id, String name, double x, double y, double z,
            Segment segment, double maximumSpeed) {
        return new Waypoint(id, name, new Vec3(x, y, z), segment, maximumSpeed);
    }

    private static ApproachWaypoint approach(
            String id, String name, double x, double y, double z, double maximumSpeed) {
        return new ApproachWaypoint(id, name, new Vec3(x, y, z), maximumSpeed);
    }

    private static PeakHold peakHold(String id, double x, double y, double z) {
        return new PeakHold(id, new Vec3(x, y, z));
    }

    static Vec3 rotateOffset(Vec3 authoredOffset, int quarterTurns) {
        if (authoredOffset == null) {
            throw new IllegalArgumentException("Authored offset is required");
        }
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> authoredOffset;
            case 1 -> new Vec3(-authoredOffset.z, authoredOffset.y, authoredOffset.x);
            case 2 -> new Vec3(-authoredOffset.x, authoredOffset.y, -authoredOffset.z);
            case 3 -> new Vec3(authoredOffset.z, authoredOffset.y, -authoredOffset.x);
            default -> throw new AssertionError();
        };
    }

    static float rotateYaw(float authoredYaw, int quarterTurns) {
        float rotated = (authoredYaw + Math.floorMod(quarterTurns, 4) * 90.0F) % 360.0F;
        return rotated < 0.0F ? rotated + 360.0F : rotated;
    }

    static float roostYaw(int quarterTurns) {
        return rotateYaw(AUTHORED_ROOST_YAW, quarterTurns);
    }

    private static double horizontalDistanceSquared(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return (x * x) + (z * z);
    }

    enum Segment {
        ASSEMBLY,
        STORM,
        CROWN,
        SOLAR
    }

    record Waypoint(String id, String name, Vec3 offset, Segment segment, double maximumSpeed) {
        Waypoint {
            if (id == null || id.isBlank() || name == null || name.isBlank()
                    || offset == null || segment == null) {
                throw new IllegalArgumentException("Flight waypoint fields are required");
            }
            if (!Double.isFinite(maximumSpeed) || maximumSpeed <= 0.0D) {
                throw new IllegalArgumentException("Flight waypoint speed must be positive and finite");
            }
        }
    }

    record ApproachWaypoint(String id, String name, Vec3 offset, double maximumSpeed) {
        ApproachWaypoint {
            if (id == null || id.isBlank() || name == null || name.isBlank() || offset == null) {
                throw new IllegalArgumentException("Approach waypoint fields are required");
            }
            if (!Double.isFinite(maximumSpeed) || maximumSpeed <= 0.0D) {
                throw new IllegalArgumentException("Approach waypoint speed must be positive and finite");
            }
        }
    }

    record PeakHold(String id, Vec3 offset) {
        PeakHold {
            if (id == null || id.isBlank() || offset == null) {
                throw new IllegalArgumentException("Peak hold fields are required");
            }
        }
    }

    record Perch(String id, Vec3 rootOffset, Vec3 skyApproachOffset, float yaw) {
        Perch {
            if (id == null || id.isBlank() || rootOffset == null || skyApproachOffset == null
                    || !Float.isFinite(yaw)) {
                throw new IllegalArgumentException("Perch fields are required");
            }
        }
    }
}

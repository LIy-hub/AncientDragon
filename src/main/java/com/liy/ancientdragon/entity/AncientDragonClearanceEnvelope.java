package com.liy.ancientdragon.entity;

import com.liy.ancientdragon.animation.pose.DragonPoseState;

/** Conservative local bounds sampled from every dormant, awakening and takeoff collision frame. */
public final class AncientDragonClearanceEnvelope {
    private static final Bounds BOUNDS = calculate();

    private AncientDragonClearanceEnvelope() {
    }

    public static Bounds bounds() {
        return BOUNDS;
    }

    private static Bounds calculate() {
        AncientDragonCollisionRig rig = AncientDragonCollisionRig.instance();
        MutableBounds bounds = new MutableBounds();
        sample(rig, AncientDragonCollisionRig.DORMANT, 2, true, "ancient_dragon:dormant_hold", bounds);
        sample(rig, AncientDragonCollisionRig.AWAKEN, AncientDragonEntity.AWAKEN_DURATION_TICKS,
                false, "ancient_dragon:awaken_intro", bounds);
        sample(rig, AncientDragonCollisionRig.TAKEOFF, AncientDragonEntity.TAKEOFF_DURATION_TICKS,
                false, "ancient_dragon:takeoff", bounds);
        return bounds.freeze();
    }

    private static void sample(
            AncientDragonCollisionRig rig,
            String clip,
            int durationTicks,
            boolean loop,
            String animationKey,
            MutableBounds bounds) {
        for (int tick = 0; tick <= durationTicks; tick++) {
            AncientDragonCollisionRig.SolvedFrame solved = rig.solve(
                    rig.sample(clip, tick, loop), DragonPoseState.IDENTITY, animationKey);
            for (AncientDragonPartKind kind : AncientDragonPartKind.values()) {
                var position = solved.position(kind);
                var projected = kind.project(solved.rotation(kind));
                double xRadius = projected.xSize() * 0.5D;
                double verticalRadius = projected.ySize() * 0.5D;
                double zRadius = projected.zSize() * 0.5D;
                bounds.include(
                        position.x - xRadius,
                        position.y - verticalRadius,
                        position.z - zRadius,
                        position.x + xRadius,
                        position.y + verticalRadius,
                        position.z + zRadius);
            }
        }
    }

    public record Bounds(
            double minimumX,
            double minimumY,
            double minimumZ,
            double maximumX,
            double maximumY,
            double maximumZ) {
        public double width() {
            return maximumX - minimumX;
        }

        public double height() {
            return maximumY - minimumY;
        }

        public double depth() {
            return maximumZ - minimumZ;
        }

        /** Width after the model's +Z forward axis is rotated to the mountain's local +X. */
        public double eastFacingWidth() {
            return depth();
        }

        /** Depth after the model's +Z forward axis is rotated to the mountain's local +X. */
        public double eastFacingDepth() {
            return width();
        }
    }

    private static final class MutableBounds {
        private double minimumX = Double.POSITIVE_INFINITY;
        private double minimumY = Double.POSITIVE_INFINITY;
        private double minimumZ = Double.POSITIVE_INFINITY;
        private double maximumX = Double.NEGATIVE_INFINITY;
        private double maximumY = Double.NEGATIVE_INFINITY;
        private double maximumZ = Double.NEGATIVE_INFINITY;

        private void include(
                double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ) {
            minimumX = Math.min(minimumX, minX);
            minimumY = Math.min(minimumY, minY);
            minimumZ = Math.min(minimumZ, minZ);
            maximumX = Math.max(maximumX, maxX);
            maximumY = Math.max(maximumY, maxY);
            maximumZ = Math.max(maximumZ, maxZ);
        }

        private Bounds freeze() {
            if (!Double.isFinite(minimumX) || !Double.isFinite(maximumX)) {
                throw new IllegalStateException("Ancient Dragon clearance envelope is empty");
            }
            return new Bounds(minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ);
        }
    }
}

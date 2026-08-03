package com.liy.ancientdragon.entity;

import com.liy.ancientdragon.animation.pose.DragonQuaternion;
import net.minecraft.world.phys.Vec3;

/** Ordered collision-bone proxy definitions; the order is frozen by the baked rig resource. */
public enum AncientDragonPartKind {
    HEAD("head", 6.0F, 5.0F, 6.0F, 1.25F),
    NECK_FRONT("neck_front", 5.5F, 5.5F, 5.5F, 1.0F),
    NECK_BASE("neck_base", 6.5F, 6.0F, 6.5F, 0.95F),
    CHEST("chest", 10.0F, 8.0F, 10.0F, 0.85F),
    PELVIS("pelvis", 11.0F, 8.0F, 11.0F, 0.8F),
    HIND_THIGH_L("hind_thigh_l", 5.0F, 6.0F, 5.0F, 0.75F),
    HIND_FOOT_L("hind_foot_l", 4.0F, 3.0F, 4.0F, 0.8F),
    HIND_THIGH_R("hind_thigh_r", 5.0F, 6.0F, 5.0F, 0.75F),
    HIND_FOOT_R("hind_foot_r", 4.0F, 3.0F, 4.0F, 0.8F),
    PRIMARY_WING_L_INNER("primary_wing_l_inner", 14.0F, 6.0F, 4.0F, 0.65F),
    PRIMARY_WING_L_MID("primary_wing_l_mid", 18.0F, 5.5F, 3.0F, 0.55F),
    PRIMARY_WING_L_TIP("primary_wing_l_tip", 20.0F, 5.0F, 2.5F, 0.5F),
    PRIMARY_WING_R_INNER("primary_wing_r_inner", 14.0F, 6.0F, 4.0F, 0.65F),
    PRIMARY_WING_R_MID("primary_wing_r_mid", 18.0F, 5.5F, 3.0F, 0.55F),
    PRIMARY_WING_R_TIP("primary_wing_r_tip", 20.0F, 5.0F, 2.5F, 0.5F),
    TAIL_1("tail_1", 8.0F, 6.0F, 8.0F, 0.7F),
    TAIL_2("tail_2", 6.0F, 5.0F, 6.0F, 0.6F),
    TAIL_TIP("tail_tip", 3.0F, 3.0F, 3.0F, 0.5F);

    private final String serializedName;
    private final float width;
    private final float height;
    private final float depth;
    private final float halfHeight;
    private final float damageMultiplier;

    AncientDragonPartKind(
            String serializedName, float width, float height, float depth, float damageMultiplier) {
        this.serializedName = serializedName;
        this.width = AncientDragonScale.blocks(width);
        this.height = AncientDragonScale.blocks(height);
        this.depth = AncientDragonScale.blocks(depth);
        this.halfHeight = this.height * 0.5F;
        this.damageMultiplier = damageMultiplier;
    }

    public String serializedName() {
        return serializedName;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float depth() {
        return depth;
    }

    float halfHeight() {
        return halfHeight;
    }

    public float damageMultiplier() {
        return damageMultiplier;
    }

    public boolean isHead() {
        return this == HEAD;
    }

    public boolean isTail() {
        return name().startsWith("TAIL_");
    }

    public boolean isWing() {
        return name().contains("WING_");
    }

    /** Stability weight is intentionally independent from health damage multipliers. */
    public float stabilityMultiplier() {
        if (!isWing()) {
            return 0.0F;
        }
        if (name().endsWith("_TIP")) {
            return 1.6F;
        }
        if (name().contains("_MID")) {
            return 1.35F;
        }
        return 1.1F;
    }

    ProjectedDimensions project(DragonQuaternion orientation) {
        Vec3 right = orientation.right();
        Vec3 up = orientation.up();
        Vec3 forward = orientation.forward();
        double halfWidth = width * 0.5D;
        double halfHeight = height * 0.5D;
        double halfDepth = depth * 0.5D;
        double halfX = Math.abs(right.x) * halfWidth
                + Math.abs(up.x) * halfHeight
                + Math.abs(forward.x) * halfDepth;
        double halfY = Math.abs(right.y) * halfWidth
                + Math.abs(up.y) * halfHeight
                + Math.abs(forward.y) * halfDepth;
        double halfZ = Math.abs(right.z) * halfWidth
                + Math.abs(up.z) * halfHeight
                + Math.abs(forward.z) * halfDepth;
        return new ProjectedDimensions(
                (float) (halfX * 2.0D), (float) (halfY * 2.0D), (float) (halfZ * 2.0D));
    }

    record ProjectedDimensions(float xSize, float ySize, float zSize) {
        ProjectedDimensions {
            if (!Float.isFinite(xSize)
                    || !Float.isFinite(ySize)
                    || !Float.isFinite(zSize)
                    || xSize <= 0.0F
                    || ySize <= 0.0F
                    || zSize <= 0.0F) {
                throw new IllegalArgumentException("Projected part dimensions must be finite and positive");
            }
        }

        float horizontalDiameter() {
            return Math.max(xSize, zSize);
        }
    }

    static AncientDragonPartKind byNetworkId(int networkId) {
        AncientDragonPartKind[] values = values();
        if (networkId < 0 || networkId >= values.length) {
            return HEAD;
        }
        return values[networkId];
    }
}

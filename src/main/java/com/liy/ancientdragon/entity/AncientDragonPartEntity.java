package com.liy.ancientdragon.entity;

import com.liy.ancientdragon.animation.pose.DragonQuaternion;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Invisible, networked hurtbox attached to one server collision-bone proxy. */
public final class AncientDragonPartEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_PARENT_ID =
            SynchedEntityData.defineId(AncientDragonPartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_PART_KIND =
            SynchedEntityData.defineId(AncientDragonPartEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_DYNAMIC_WIDTH =
            SynchedEntityData.defineId(AncientDragonPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DYNAMIC_HEIGHT =
            SynchedEntityData.defineId(AncientDragonPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DYNAMIC_DEPTH =
            SynchedEntityData.defineId(AncientDragonPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_BLOCKS_PLAYERS =
            SynchedEntityData.defineId(AncientDragonPartEntity.class, EntityDataSerializers.BOOLEAN);

    private int orphanTicks;

    public AncientDragonPartEntity(EntityType<? extends AncientDragonPartEntity> entityType, Level level) {
        super(entityType, level);
        setNoGravity(true);
        noPhysics = true;
    }

    void attach(AncientDragonEntity parent, AncientDragonPartKind kind) {
        entityData.set(DATA_PARENT_ID, parent.getId());
        entityData.set(DATA_PART_KIND, (byte) kind.ordinal());
        entityData.set(DATA_DYNAMIC_WIDTH, kind.width());
        entityData.set(DATA_DYNAMIC_HEIGHT, kind.height());
        entityData.set(DATA_DYNAMIC_DEPTH, kind.depth());
        entityData.set(DATA_BLOCKS_PLAYERS, parent.blocksPlayerCollision());
        refreshDimensions();
    }

    void updatePose(Vec3 center, DragonQuaternion orientation) {
        AncientDragonPartKind kind = partKind();
        AncientDragonEntity parent = parent();
        boolean blocksPlayers = parent == null || parent.blocksPlayerCollision();
        if (entityData.get(DATA_BLOCKS_PLAYERS) != blocksPlayers) {
            entityData.set(DATA_BLOCKS_PLAYERS, blocksPlayers);
        }
        AncientDragonPartKind.ProjectedDimensions projected = kind.project(orientation);
        float previousWidth = entityData.get(DATA_DYNAMIC_WIDTH);
        float previousHeight = entityData.get(DATA_DYNAMIC_HEIGHT);
        float previousDepth = entityData.get(DATA_DYNAMIC_DEPTH);
        if (Math.abs(projected.xSize() - previousWidth) > 0.01F
                || Math.abs(projected.ySize() - previousHeight) > 0.01F
                || Math.abs(projected.zSize() - previousDepth) > 0.01F) {
            entityData.set(DATA_DYNAMIC_WIDTH, projected.xSize());
            entityData.set(DATA_DYNAMIC_HEIGHT, projected.ySize());
            entityData.set(DATA_DYNAMIC_DEPTH, projected.zSize());
            refreshDimensions();
        }
        setPos(center.x, center.y - projected.ySize() * 0.5D, center.z);
        setYRot(orientation.minecraftYawDegrees());
        setXRot(orientation.minecraftPitchDegrees());
    }

    AncientDragonPartKind partKind() {
        return AncientDragonPartKind.byNetworkId(Byte.toUnsignedInt(entityData.get(DATA_PART_KIND)));
    }

    private AncientDragonEntity parent() {
        Entity candidate = level().getEntity(entityData.get(DATA_PARENT_ID));
        return candidate instanceof AncientDragonEntity dragon ? dragon : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PARENT_ID, -1);
        builder.define(DATA_PART_KIND, (byte) 0);
        builder.define(DATA_DYNAMIC_WIDTH, AncientDragonPartKind.HEAD.width());
        builder.define(DATA_DYNAMIC_HEIGHT, AncientDragonPartKind.HEAD.height());
        builder.define(DATA_DYNAMIC_DEPTH, AncientDragonPartKind.HEAD.depth());
        builder.define(DATA_BLOCKS_PLAYERS, true);
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        setDeltaMovement(0.0D, 0.0D, 0.0D);
        if (level().isClientSide()) {
            // These positions are already server-sampled from the collision skeleton. Keeping an
            // additional render-tick history makes F3+B interpolate the proxy a second time and
            // visibly trail the independently interpolated parent model.
            setOldPosAndRot(position(), getYRot(), getXRot());
            return;
        }
        if (parent() == null) {
            orphanTicks++;
            if (orphanTicks > 20) {
                discard();
            }
        } else {
            orphanTicks = 0;
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_PART_KIND.equals(accessor)
                || DATA_DYNAMIC_WIDTH.equals(accessor)
                || DATA_DYNAMIC_HEIGHT.equals(accessor)
                || DATA_DYNAMIC_DEPTH.equals(accessor)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(
                Math.max(0.1F, Math.max(
                        entityData.get(DATA_DYNAMIC_WIDTH), entityData.get(DATA_DYNAMIC_DEPTH))),
                Math.max(0.1F, entityData.get(DATA_DYNAMIC_HEIGHT)));
    }

    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        double width = Math.max(0.1F, entityData.get(DATA_DYNAMIC_WIDTH));
        double height = Math.max(0.1F, entityData.get(DATA_DYNAMIC_HEIGHT));
        double depth = Math.max(0.1F, entityData.get(DATA_DYNAMIC_DEPTH));
        return new AABB(
                position.x - width * 0.5D,
                position.y,
                position.z - depth * 0.5D,
                position.x + width * 0.5D,
                position.y + height,
                position.z + depth * 0.5D);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return entity instanceof Player && entityData.get(DATA_BLOCKS_PLAYERS);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (level().isClientSide() && player.getItemInHand(hand).is(ItemTags.AXES)) {
            return InteractionResult.SUCCESS;
        }
        AncientDragonEntity parent = parent();
        return parent == null
                ? InteractionResult.PASS
                : parent.interactCorpse(player, hand, getBoundingBox().getCenter());
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isInvulnerableToBase(source)) {
            return false;
        }
        AncientDragonEntity parent = parent();
        return parent != null && parent.hurtPartServer(level, partKind(), source, amount);
    }

    @Override
    public boolean is(Entity entity) {
        AncientDragonEntity parent = parent();
        return super.is(entity) || parent == entity;
    }

    @Override
    public ItemStack getPickResult() {
        AncientDragonEntity parent = parent();
        return parent == null ? ItemStack.EMPTY : parent.getPickResult();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        // Part entities are rebuilt from the parent and are never saved.
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        // Part entities are rebuilt from the parent and are never saved.
    }

}

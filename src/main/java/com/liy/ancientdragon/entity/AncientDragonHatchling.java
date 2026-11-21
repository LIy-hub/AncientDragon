package com.liy.ancientdragon.entity;

import com.liy.blendlib.api.BlendAnimationKey;
import com.liy.blendlib.fabric.common.animation.BlendAnimations;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class AncientDragonHatchling extends TamableAnimal {
    public static final BlendAnimationKey PERCH = BlendAnimationKey.parse("ancient_dragon:hatchling_perch");
    public static final BlendAnimationKey FLIGHT = BlendAnimationKey.parse("ancient_dragon:hatchling_flight");
    public static final BlendAnimationKey GREET = BlendAnimationKey.parse("ancient_dragon:hatchling_greet");

    private UUID keeper;
    private BlendAnimationKey animation;
    private boolean ownerWasNear;

    public AncientDragonHatchling(EntityType<? extends AncientDragonHatchling> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 15, false);
    }

    public void setKeeper(UUID keeper) {
        this.keeper = keeper;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0, 5.0F, 2.0F));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    protected boolean canFlyToOwner() {
        return true;
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.COD) || stack.is(Items.SALMON);
    }

    @Override
    public boolean canFallInLove() {
        return false;
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean food = isFood(held);
        if (!isTame() && food) {
            if (level() instanceof ServerLevel level) {
                if (keeper != null && !keeper.equals(player.getUUID())) {
                    tell(player, "reserved");
                } else {
                    held.consume(1, player);
                    tame(player);
                    setOrderedToSit(false);
                    level.broadcastEntityEvent(this, (byte) 7);
                    tell(player, "bonded");
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (isTame() && isOwnedBy(player)) {
            if (food && getHealth() < getMaxHealth()) {
                if (level() instanceof ServerLevel) {
                    held.consume(1, player);
                    heal(3.0F);
                }
                return InteractionResult.SUCCESS;
            }
            if (held.isEmpty()) {
                if (level() instanceof ServerLevel) {
                    setOrderedToSit(!isOrderedToSit());
                    getNavigation().stop();
                    tell(player, isOrderedToSit() ? "stay" : "follow");
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.mobInteract(player, hand);
    }

    private static void tell(Player player, String message) {
        player.sendSystemMessage(Component.translatable("message.ancient_dragon.hatchling." + message));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel)) {
            return;
        }
        BlendAnimationKey now = onGround() ? PERCH : FLIGHT;
        if (!now.equals(animation)) {
            animation = now;
            BlendAnimations.entity(this).setPersistent(now, 1.0F, getUUID().getLeastSignificantBits() ^ tickCount);
        }

        LivingEntity owner = getOwner();
        boolean nearby = owner != null && owner.level() == level() && owner.isAlive()
                && distanceToSqr(owner) < 5.0 * 5.0;
        if (nearby && !ownerWasNear && onGround()) {
            getLookControl().setLookAt(owner);
            BlendAnimations.entity(this).trigger(GREET, 1.0F, tickCount);
        }
        ownerWasNear = nearby;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (keeper != null) {
            output.store("Keeper", UUIDUtil.CODEC, keeper);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        keeper = input.read("Keeper", UUIDUtil.CODEC).orElse(null);
    }
}

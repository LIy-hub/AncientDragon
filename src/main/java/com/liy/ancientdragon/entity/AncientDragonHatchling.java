package com.liy.ancientdragon.entity;

import com.liy.blendlib.api.BlendAnimationKey;
import com.liy.blendlib.fabric.common.animation.BlendAnimations;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
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
import net.minecraft.world.phys.Vec3;

public final class AncientDragonHatchling extends TamableAnimal {
    public static final BlendAnimationKey PERCH = BlendAnimationKey.parse("ancient_dragon:hatchling_perch");
    public static final BlendAnimationKey FLIGHT = BlendAnimationKey.parse("ancient_dragon:hatchling_flight");
    public static final BlendAnimationKey GLIDE = BlendAnimationKey.parse("ancient_dragon:hatchling_glide");
    public static final BlendAnimationKey GREET = BlendAnimationKey.parse("ancient_dragon:hatchling_greet");

    private UUID keeper;
    private BlendAnimationKey animation;
    private boolean ownerWasNear;
    private int nextIdleGesture = 180;
    private HatchlingFlightGoal freeFlight;
    private float previousBank;
    private float bank;
    private float previousAirborneBlend;
    private float airborneBlend;

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
        goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0, 7.5F, 2.5F));
        freeFlight = new HatchlingFlightGoal(this);
        goalSelector.addGoal(3, freeFlight);
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
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
                    setHealth(getMaxHealth());
                    setOrderedToSit(false);
                    freeFlight.scheduleFirstFlight();
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
                    heal(4.0F);
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
    public void travel(Vec3 acceleration) {
        if (level() instanceof ServerLevel && freeFlight != null && freeFlight.isFlying()) {
            freeFlight.travel();
        } else {
            super.travel(acceleration);
        }
    }

    @Override
    public void tick() {
        float initialHeading = getYRot();
        super.tick();
        if (!(level() instanceof ServerLevel)) {
            previousBank = bank;
            previousAirborneBlend = airborneBlend;
            float lean = onGround() ? 0.0F : Mth.clamp(Mth.wrapDegrees(getYRot() - initialHeading) * 4.3F, -27.0F, 27.0F);
            bank = Mth.lerp(0.24F, bank, lean);
            airborneBlend = Mth.lerp(0.36F, airborneBlend, onGround() ? 0.0F : 1.0F);
            return;
        }
        BlendAnimationKey now = flightAnimation();
        if (!now.equals(animation)) {
            animation = now;
            BlendAnimations.entity(this).setPersistent(now, 1.0F, getUUID().getLeastSignificantBits() ^ tickCount);
        }
        updateIdleGesture();
    }

    private BlendAnimationKey flightAnimation() {
        if (onGround()) {
            return PERCH;
        }
        return freeFlight != null && freeFlight.isGliding() ? GLIDE : FLIGHT;
    }

    private void updateIdleGesture() {
        LivingEntity owner = getOwner();
        boolean nearby = onGround() && owner != null && owner.level() == level() && owner.isAlive()
                && distanceToSqr(owner) < 5.0 * 5.0;
        boolean greeting = nearby && !ownerWasNear;
        ownerWasNear = nearby;
        if (!onGround() || getDeltaMovement().horizontalDistanceSqr() > 0.012
                || (!greeting && tickCount < nextIdleGesture)) {
            return;
        }
        if (nearby) {
            getLookControl().setLookAt(owner);
        }
        if (greeting) {
            playSound(SoundEvents.PARROT_AMBIENT, 0.22F, 0.96F + getRandom().nextFloat() * 0.18F);
        }
        BlendAnimations.entity(this).trigger(GREET, 0.92F + getRandom().nextFloat() * 0.19F, tickCount);
        nextIdleGesture = tickCount + 220 + getRandom().nextInt(360);
    }

    public float renderBank(float partialTick) {
        return Mth.lerp(partialTick, previousBank, bank);
    }

    public float renderAirborneBlend(float partialTick) {
        return Mth.lerp(partialTick, previousAirborneBlend, airborneBlend);
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

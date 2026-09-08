package com.liy.ancientdragon.compat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Backports held kinetic contact and piercing jab while the target game lacks spear components. */
public final class LegacySpearItem extends Item {
    private static final Map<LivingEntity, Map<UUID, Long>> CONTACTS = new WeakHashMap<>();
    public LegacySpearItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        if (!level.isClientSide()) {
            CONTACTS.remove(player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.7F, 0.8F);
        }
        return InteractionResult.CONSUME;
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.SPEAR; }

    @Override
    public void onUseTick(Level level, LivingEntity attacker, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server)) return;
        int elapsed = getUseDuration(stack, attacker) - remaining;
        if (elapsed < LegacySpearRules.DELAY) return;
        Vec3 look = attacker.getLookAngle();
        double speed = motion(attacker).dot(look) * 20.0;
        long tick = server.getGameTime();
        Map<UUID, Long> contacts = CONTACTS.computeIfAbsent(attacker, ignored -> new HashMap<>());
        contacts.values().removeIf(previous -> tick - previous >= LegacySpearRules.CONTACT_COOLDOWN);
        boolean hit = false;
        for (Entity target : targets(attacker)) {
            if (contacts.containsKey(target.getUUID())) continue;
            var result = LegacySpearRules.kinetic(elapsed, speed, motion(target).dot(look) * 20.0, attacker instanceof Player);
            if (!result.damage() && !result.knockback() && !result.dismount()) continue;
            contacts.put(target.getUUID(), tick);
            boolean damaged = false;
            var source = attacker instanceof Player player ? attacker.damageSources().playerAttack(player)
                    : attacker.damageSources().mobAttack(attacker);
            if (result.damage()) {
                float damage = (float) attacker.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) + result.kineticDamage();
                damage = EnchantmentHelper.modifyDamage(server, stack, target, source, damage);
                damaged = target.hurtServer(server, source, damage);
            }
            if (result.knockback() && target instanceof LivingEntity living) {
                float knockback = EnchantmentHelper.modifyKnockback(server, stack, target, source,
                        (float) attacker.getAttributeValue(Attributes.ATTACK_KNOCKBACK));
                living.knockback(0.4 + knockback, -look.x, -look.z);
                target.hurtMarked = true;
            }
            if (result.dismount() && target.isPassenger()) target.stopRiding();
            if (damaged) {
                EnchantmentHelper.doPostAttackEffects(server, target, source);
                attacker.setLastHurtMob(target);
                stack.hurtAndBreak(1, attacker, LivingEntity.getSlotForHand(attacker.getUsedItemHand()));
            }
            hit = true;
        }
        if (hit) level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.7F, 0.8F);
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    private static Vec3 motion(Entity entity) {
        Entity owner = entity.isPassenger() ? entity.getRootVehicle() : entity;
        return owner.getKnownMovement();
    }

    /** Server ray stops at solid blocks, excludes allies/PvP restrictions, and honors the 2-block dead zone. */
    public static List<Entity> targets(LivingEntity attacker) {
        boolean creative = attacker instanceof Player player && player.isCreative();
        Vec3 start = attacker.getEyePosition();
        Vec3 end = start.add(attacker.getLookAngle().scale(creative
                ? LegacySpearRules.CREATIVE_RANGE : LegacySpearRules.SURVIVAL_RANGE));
        var blockHit = attacker.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, attacker));
        if (blockHit.getType() != HitResult.Type.MISS) end = blockHit.getLocation();
        List<Entity> targets = new ArrayList<>();
        for (Entity target : attacker.level().getEntities(attacker, new AABB(start, end).inflate(0.5),
                entity -> entity.isPickable() && entity.isAttackable() && entity.isAlive()
                        && !entity.isSpectator() && !entity.isAlliedTo(attacker)
                        && entity.getRootVehicle() != attacker.getRootVehicle())) {
            if (attacker instanceof Player player && target instanceof Player other && !player.canHarmPlayer(other)) continue;
            var intersection = target.getBoundingBox().inflate(0.125).clip(start, end);
            if (intersection.isPresent() && start.distanceTo(intersection.get()) >= LegacySpearRules.MIN_RANGE) targets.add(target);
        }
        targets.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(attacker)));
        return targets;
    }
}

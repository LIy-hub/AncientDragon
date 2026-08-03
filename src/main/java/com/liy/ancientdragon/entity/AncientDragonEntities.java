package com.liy.ancientdragon.entity;

import com.liy.ancientdragon.AncientDragonMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Server-authoritative entity registration for the host and its bone-proxy hurtboxes. */
public final class AncientDragonEntities {
    public static final float GAMEPLAY_WIDTH = AncientDragonScale.blocks(2.0F);
    public static final float GAMEPLAY_HEIGHT = AncientDragonScale.blocks(2.0F);
    private static final int CLIENT_TRACKING_RANGE = 64;

    public static final Identifier ANCIENT_DRAGON_ID =
            Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "ancient_dragon");
    public static final Identifier ANCIENT_DRAGON_PART_ID =
            Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "ancient_dragon_part");

    public static final EntityType<AncientDragonEntity> ANCIENT_DRAGON = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ANCIENT_DRAGON_ID,
            EntityType.Builder.of(AncientDragonEntity::new, MobCategory.MISC)
                    .sized(GAMEPLAY_WIDTH, GAMEPLAY_HEIGHT)
                    .clientTrackingRange(CLIENT_TRACKING_RANGE)
                    .updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, ANCIENT_DRAGON_ID)));

    public static final EntityType<AncientDragonPartEntity> ANCIENT_DRAGON_PART = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ANCIENT_DRAGON_PART_ID,
            EntityType.Builder.of(AncientDragonPartEntity::new, MobCategory.MISC)
                    .sized(AncientDragonScale.blocks(1.0F), AncientDragonScale.blocks(1.0F))
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(CLIENT_TRACKING_RANGE)
                    .updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, ANCIENT_DRAGON_PART_ID)));

    private AncientDragonEntities() {
    }

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(ANCIENT_DRAGON, Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 600.0D)
                .add(Attributes.ARMOR, 16.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 12.0D)
                .add(Attributes.ATTACK_DAMAGE, 24.0D)
                .add(Attributes.FLYING_SPEED, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 256.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D));
    }
}

package com.liy.ancientdragon.block;

import com.liy.ancientdragon.AncientDragonMod;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Block registrations for the permanent relics made from Ancient Dragon remains. */
public final class AncientDragonBlocks {
    public static final Identifier ETERNAL_SOUL_FIRE_ID =
            Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "eternal_soul_fire");
    public static final Block ETERNAL_SOUL_FIRE = Registry.register(
            BuiltInRegistries.BLOCK,
            ETERNAL_SOUL_FIRE_ID,
            new EternalSoulFireBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SOUL_FIRE)
                    .setId(ResourceKey.create(Registries.BLOCK, ETERNAL_SOUL_FIRE_ID))
                    .noLootTable()));

    public static final Identifier ANCIENT_CITY_GATEWAY_ID =
            Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "ancient_city_gateway");
    public static final Block ANCIENT_CITY_GATEWAY = Registry.register(
            BuiltInRegistries.BLOCK,
            ANCIENT_CITY_GATEWAY_ID,
            new AncientCityGatewayBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, ANCIENT_CITY_GATEWAY_ID))
                    .strength(-1.0F, 3_600_000.0F)
                    .noCollision()
                    .noOcclusion()
                    .lightLevel(state -> 7)
                    .sound(SoundType.SCULK)));

    public static final Identifier SUNHEART_ALTAR_ID =
            Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "sunheart_altar");
    public static final Block SUNHEART_ALTAR = Registry.register(
            BuiltInRegistries.BLOCK,
            SUNHEART_ALTAR_ID,
            new SunheartAltarBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, SUNHEART_ALTAR_ID))
                    .strength(5.0F, 1_200.0F)
                    .lightLevel(state -> 12)
                    .sound(SoundType.AMETHYST)));

    public static final Identifier DRAGON_EGG_ID =
            Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "dragon_egg");
    public static final AncientDragonEggBlock DRAGON_EGG = Registry.register(
            BuiltInRegistries.BLOCK, DRAGON_EGG_ID,
            new AncientDragonEggBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, DRAGON_EGG_ID))
                    .strength(-1.0F, 3_600_000.0F)
                    .noCollision()
                    .noLootTable()
                    .lightLevel(state -> state.getValue(AncientDragonEggBlock.CRACKED) ? 5 : 0)
                    .sound(SoundType.AMETHYST)));
    public static final BlockEntityType<AncientDragonEggBlockEntity> DRAGON_EGG_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, DRAGON_EGG_ID,
            FabricBlockEntityTypeBuilder.create(AncientDragonEggBlockEntity::new, DRAGON_EGG).build());

    private AncientDragonBlocks() {
    }

    public static void initialize() {
        // Class loading performs registration.
    }
}

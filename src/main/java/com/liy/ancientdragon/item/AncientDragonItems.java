package com.liy.ancientdragon.item;

import com.liy.ancientdragon.AncientDragonMod;
import com.liy.ancientdragon.block.AncientDragonBlocks;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

/** Finite materials harvested from the one world-unique Ancient Dragon corpse. */
public final class AncientDragonItems {
    private static final TagKey<Item> DRAGONBONE_NETHERITE_REPAIR_ITEMS =
            TagKey.create(Registries.ITEM, id("repairs_dragonbone_netherite_tools"));
    private static final ResourceKey<EquipmentAsset> SKY_WING_EQUIPMENT_ASSET = ResourceKey.create(
            EquipmentAssets.ROOT_ID,
            Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "sky_wing"));
    private static final ResourceKey<EquipmentAsset> MOUNTAINFORGED_EQUIPMENT_ASSET = ResourceKey.create(
            EquipmentAssets.ROOT_ID,
            Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "mountainforged"));
    private static final ResourceKey<EquipmentAsset> MOUNTAINFORGED_SKY_WING_EQUIPMENT_ASSET = ResourceKey.create(
            EquipmentAssets.ROOT_ID,
            Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "mountainforged_sky_wing"));

    private static final ToolMaterial DRAGONBONE_NETHERITE = new ToolMaterial(
            ToolMaterial.NETHERITE.incorrectBlocksForDrops(),
            AncientDragonRelicStats.upgradedDurability(ToolMaterial.NETHERITE.durability()),
            ToolMaterial.NETHERITE.speed(),
            ToolMaterial.NETHERITE.attackDamageBonus(),
            ToolMaterial.NETHERITE.enchantmentValue(),
            DRAGONBONE_NETHERITE_REPAIR_ITEMS);

    public static final Item ANCIENT_SCALE = register("ancient_scale", Rarity.UNCOMMON, 64, false);
    public static final Item ANCIENT_HORN = register("ancient_horn", Rarity.RARE, 16, false);
    public static final Item ANCIENT_WING_MEMBRANE =
            register("ancient_wing_membrane", Rarity.RARE, 64, false);
    public static final Item ANCIENT_BONE = register("ancient_bone", Rarity.UNCOMMON, 64, false);
    public static final Item ANCIENT_DRAGON_HEART =
            register("ancient_dragon_heart", Rarity.EPIC, 1, true);
    public static final Item SUNHEART_ALTAR = register(
            "sunheart_altar",
            Rarity.EPIC,
            1,
            true,
            properties -> new BlockItem(
                    AncientDragonBlocks.SUNHEART_ALTAR,
                    properties.useBlockDescriptionPrefix()));
    public static final Item SKY_WING = register(
            "sky_wing",
            Rarity.EPIC,
            1,
            true,
            properties -> new Item(properties
                    .durability(432)
                    .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                    .component(DataComponents.GLIDER, Unit.INSTANCE)
                    .component(
                            DataComponents.EQUIPPABLE,
                            Equippable.builder(EquipmentSlot.CHEST)
                                    .setEquipSound(SoundEvents.ARMOR_EQUIP_ELYTRA)
                                    .setAsset(SKY_WING_EQUIPMENT_ASSET)
                                    .setEquipOnInteract(true)
                                    .build())));
    public static final Item MOUNTAINFORGED_NETHERITE_HELMET = register(
            "mountainforged_netherite_helmet",
            Rarity.EPIC,
            1,
            true,
            properties -> new Item(reinforcedArmorProperties(properties, ArmorType.HELMET)));
    public static final Item MOUNTAINFORGED_NETHERITE_CHESTPLATE = register(
            "mountainforged_netherite_chestplate",
            Rarity.EPIC,
            1,
            true,
            properties -> new Item(reinforcedArmorProperties(properties, ArmorType.CHESTPLATE)));
    public static final Item MOUNTAINFORGED_SKY_WING = register(
            "mountainforged_sky_wing",
            Rarity.EPIC,
            1,
            true,
            properties -> new Item(mountainforgedSkyWingProperties(properties)));
    public static final Item MOUNTAINFORGED_NETHERITE_LEGGINGS = register(
            "mountainforged_netherite_leggings",
            Rarity.EPIC,
            1,
            true,
            properties -> new Item(reinforcedArmorProperties(properties, ArmorType.LEGGINGS)));
    public static final Item MOUNTAINFORGED_NETHERITE_BOOTS = register(
            "mountainforged_netherite_boots",
            Rarity.EPIC,
            1,
            true,
            properties -> new Item(reinforcedArmorProperties(properties, ArmorType.BOOTS)));
    public static final Item DRAGONBONE_NETHERITE_SWORD = register(
            "dragonbone_netherite_sword",
            Rarity.EPIC,
            1,
            true,
            properties -> new Item(properties.sword(DRAGONBONE_NETHERITE, 8.0F, -2.4F)));
    public static final Item DRAGONBONE_NETHERITE_AXE = register(
            "dragonbone_netherite_axe",
            Rarity.EPIC,
            1,
            true,
            properties -> new AxeItem(DRAGONBONE_NETHERITE, 10.0F, -3.0F, properties));
    public static final Item DRAGONBONE_NETHERITE_PICKAXE = register(
            "dragonbone_netherite_pickaxe",
            Rarity.EPIC,
            1,
            true,
            properties -> new Item(properties.pickaxe(DRAGONBONE_NETHERITE, 6.0F, -2.8F)));
    public static final Item DRAGONBONE_NETHERITE_SHOVEL = register(
            "dragonbone_netherite_shovel",
            Rarity.EPIC,
            1,
            true,
            properties -> new ShovelItem(DRAGONBONE_NETHERITE, 6.5F, -3.0F, properties));
    public static final Item DRAGONBONE_NETHERITE_HOE = register(
            "dragonbone_netherite_hoe",
            Rarity.EPIC,
            1,
            true,
            properties -> new HoeItem(DRAGONBONE_NETHERITE, 1.0F, 0.0F, properties));
    public static final Item DRAGONBONE_NETHERITE_SPEAR = register(
            "dragonbone_netherite_spear",
            Rarity.EPIC,
            1,
            true,
            properties -> new Item(dragonboneSpearProperties(properties)));
    public static final Item STORM_HORN = register(
            "storm_horn", Rarity.EPIC, 1, true, StormHornItem::new);

    static final List<Item> CREATIVE_TAB_ITEMS = List.of(
            ANCIENT_SCALE,
            ANCIENT_HORN,
            ANCIENT_WING_MEMBRANE,
            ANCIENT_BONE,
            ANCIENT_DRAGON_HEART,
            SUNHEART_ALTAR,
            SKY_WING,
            MOUNTAINFORGED_NETHERITE_HELMET,
            MOUNTAINFORGED_NETHERITE_CHESTPLATE,
            MOUNTAINFORGED_SKY_WING,
            MOUNTAINFORGED_NETHERITE_LEGGINGS,
            MOUNTAINFORGED_NETHERITE_BOOTS,
            DRAGONBONE_NETHERITE_SWORD,
            DRAGONBONE_NETHERITE_AXE,
            DRAGONBONE_NETHERITE_PICKAXE,
            DRAGONBONE_NETHERITE_SHOVEL,
            DRAGONBONE_NETHERITE_HOE,
            DRAGONBONE_NETHERITE_SPEAR,
            STORM_HORN);

    private AncientDragonItems() {
    }

    public static void initialize() {
        Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                id("ancient_dragon"),
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.ancient_dragon"))
                        .icon(() -> new ItemStack(ANCIENT_DRAGON_HEART))
                        .displayItems((parameters, output) -> CREATIVE_TAB_ITEMS.forEach(output::accept))
                        .build());
    }

    public static boolean grantsSkyWingFlight(ItemStack stack) {
        return stack.is(SKY_WING) || stack.is(MOUNTAINFORGED_SKY_WING);
    }

    private static Item.Properties reinforcedArmorProperties(Item.Properties properties, ArmorType armorType) {
        return properties
                .humanoidArmor(ArmorMaterials.NETHERITE, armorType)
                .durability(AncientDragonRelicStats.upgradedDurability(
                        armorType.getDurability(ArmorMaterials.NETHERITE.durability())))
                .attributes(reinforcedArmorAttributes(armorType))
                .component(DataComponents.EQUIPPABLE, mountainforgedEquippable(armorType));
    }

    private static Equippable mountainforgedEquippable(ArmorType armorType) {
        return Equippable.builder(armorType.getSlot())
                .setEquipSound(ArmorMaterials.NETHERITE.equipSound())
                .setAsset(MOUNTAINFORGED_EQUIPMENT_ASSET)
                .setDamageOnHurt(true)
                .setEquipOnInteract(true)
                .build();
    }

    private static Item.Properties mountainforgedSkyWingProperties(Item.Properties properties) {
        return reinforcedArmorProperties(properties, ArmorType.CHESTPLATE)
                .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                .component(DataComponents.GLIDER, Unit.INSTANCE)
                .component(
                        DataComponents.EQUIPPABLE,
                        Equippable.builder(EquipmentSlot.CHEST)
                                .setEquipSound(SoundEvents.ARMOR_EQUIP_ELYTRA)
                                .setAsset(MOUNTAINFORGED_SKY_WING_EQUIPMENT_ASSET)
                                .setDamageOnHurt(true)
                                .setEquipOnInteract(true)
                                .build());
    }

    private static ItemAttributeModifiers reinforcedArmorAttributes(ArmorType armorType) {
        EquipmentSlotGroup slot = EquipmentSlotGroup.bySlot(armorType.getSlot());
        String armourPart = armorType.getSerializedName();
        return ArmorMaterials.NETHERITE.createAttributes(armorType)
                .withModifierAdded(
                        Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(
                                id("mountainforged_" + armourPart + "_toughness"),
                                AncientDragonRelicStats.MOUNTAINFORGED_ARMOUR_TOUGHNESS_BONUS,
                                AttributeModifier.Operation.ADD_VALUE),
                        slot)
                .withModifierAdded(
                        Attributes.KNOCKBACK_RESISTANCE,
                        new AttributeModifier(
                                id("mountainforged_" + armourPart + "_knockback_resistance"),
                                AncientDragonRelicStats.MOUNTAINFORGED_KNOCKBACK_RESISTANCE_BONUS,
                                AttributeModifier.Operation.ADD_VALUE),
                        slot);
    }

    private static Item.Properties dragonboneSpearProperties(Item.Properties properties) {
        return properties
                .spear(DRAGONBONE_NETHERITE, 1.15F, 1.2F, 0.4F, 2.5F, 9.0F, 5.5F, 5.1F, 8.75F, 4.6F)
                .attributes(dragonboneSpearAttributes());
    }

    private static ItemAttributeModifiers dragonboneSpearAttributes() {
        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                Item.BASE_ATTACK_DAMAGE_ID,
                                DRAGONBONE_NETHERITE.attackDamageBonus()
                                        + AncientDragonRelicStats.DRAGONBONE_WEAPON_DAMAGE_BONUS,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(
                                Item.BASE_ATTACK_SPEED_ID,
                                1.0D / 1.15D - 4.0D,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, path);
    }

    private static Item register(String path, Rarity rarity, int stackSize, boolean fireResistant) {
        return register(path, rarity, stackSize, fireResistant, Item::new);
    }

    private static Item register(
            String path,
            Rarity rarity,
            int stackSize,
            boolean fireResistant,
            Function<Item.Properties, Item> factory) {
        Identifier id = Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, path);
        Item.Properties properties = new Item.Properties()
                .setId(ResourceKey.create(BuiltInRegistries.ITEM.key(), id))
                .rarity(rarity)
                .stacksTo(stackSize)
                .component(DataComponents.LORE, loreFor(path));
        if (fireResistant) {
            properties.fireResistant();
        }
        return Registry.register(BuiltInRegistries.ITEM, id, factory.apply(properties));
    }

    private static ItemLore loreFor(String path) {
        Component description = Component.translatable("tooltip.ancient_dragon." + path + ".description")
                .withStyle(loreColor(path));
        Component ability = abilityLoreFor(path);
        return ability == null
                ? new ItemLore(List.of(description))
                : new ItemLore(List.of(description, ability));
    }

    private static Component abilityLoreFor(String path) {
        String key = switch (path) {
            case "sunheart_altar" -> "tooltip.ancient_dragon.sunheart_altar.ability";
            case "sky_wing" -> "tooltip.ancient_dragon.sky_wing.ability";
            case "mountainforged_netherite_helmet",
                    "mountainforged_netherite_chestplate",
                    "mountainforged_netherite_leggings",
                    "mountainforged_netherite_boots" -> "tooltip.ancient_dragon.mountainforged_armour.ability";
            case "mountainforged_sky_wing" -> "tooltip.ancient_dragon.mountainforged_sky_wing.ability";
            case "dragonbone_netherite_sword",
                    "dragonbone_netherite_axe",
                    "dragonbone_netherite_pickaxe",
                    "dragonbone_netherite_shovel",
                    "dragonbone_netherite_hoe",
                    "dragonbone_netherite_spear" -> "tooltip.ancient_dragon.dragonbone_tools.ability";
            case "storm_horn" -> "tooltip.ancient_dragon.storm_horn.ability";
            default -> null;
        };
        return key == null ? null : Component.translatable(key).withStyle(ChatFormatting.GOLD);
    }

    private static ChatFormatting loreColor(String path) {
        return switch (path) {
            case "ancient_horn", "storm_horn" -> ChatFormatting.BLUE;
            case "ancient_wing_membrane", "sky_wing" -> ChatFormatting.AQUA;
            case "ancient_bone" -> ChatFormatting.GRAY;
            case "ancient_dragon_heart", "sunheart_altar" -> ChatFormatting.YELLOW;
            case "mountainforged_netherite_helmet",
                    "mountainforged_netherite_chestplate",
                    "mountainforged_netherite_leggings",
                    "mountainforged_netherite_boots",
                    "mountainforged_sky_wing" -> ChatFormatting.LIGHT_PURPLE;
            case "dragonbone_netherite_sword",
                    "dragonbone_netherite_axe",
                    "dragonbone_netherite_pickaxe",
                    "dragonbone_netherite_shovel",
                    "dragonbone_netherite_hoe",
                    "dragonbone_netherite_spear" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.LIGHT_PURPLE;
        };
    }
}

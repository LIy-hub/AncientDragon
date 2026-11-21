package com.liy.ancientdragon.entity;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

public enum HatchlingVariant {
    AZURE("azure", "cod", () -> Items.COD, new Stats(20, 2, 0.26, 0.36), 4),
    SCARLET("scarlet", "salmon", () -> Items.SALMON, new Stats(24, 3, 0.27, 0.38), 4),
    VERDANT("verdant", "chicken", () -> Items.CHICKEN, new Stats(22, 2, 0.30, 0.46), 4),
    VIOLET("violet", "beef", () -> Items.BEEF, new Stats(28, 5, 0.26, 0.37), 8),
    AMBER("amber", "golden_apple", () -> Items.GOLDEN_APPLE, new Stats(26, 4, 0.29, 0.43), 8),
    PRISMATIC("prismatic", "enchanted_golden_apple", () -> Items.ENCHANTED_GOLDEN_APPLE,
            new Stats(32, 6, 0.32, 0.50), 12);

    static final List<HatchlingVariant> ALL = List.of(values());

    private final String id;
    private final String foodId;
    private final Supplier<Item> food;
    private final Stats stats;
    private final float healing;

    HatchlingVariant(String id, String foodId, Supplier<Item> food, Stats stats, float healing) {
        this.id = id;
        this.foodId = foodId;
        this.food = food;
        this.stats = stats;
        this.healing = healing;
    }

    public String id() {
        return id;
    }

    public Stats stats() {
        return stats;
    }

    String foodId() {
        return foodId;
    }

    float healing() {
        return healing;
    }

    boolean canTame() {
        return this == AZURE || this == SCARLET;
    }

    static @Nullable HatchlingVariant forFood(ItemStack stack) {
        for (HatchlingVariant variant : ALL) {
            if (stack.is(variant.food.get())) {
                return variant;
            }
        }
        return null;
    }

    static HatchlingVariant fromNetworkId(int index) {
        return index >= 0 && index < ALL.size() ? ALL.get(index) : AZURE;
    }

    public record Stats(double maxHealth, double armor, double groundSpeed, double flightSpeed) {
    }
}

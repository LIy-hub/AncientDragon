package com.liy.ancientdragon.entity;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Lifetime feeding history. Variant and bonuses are derived from the accepted portions. */
final class HatchlingDiet {
    private static final String SAVE_KEY = "Diet";

    private final int[] portions = new int[HatchlingVariant.ALL.size()];

    void record(HatchlingVariant food) {
        int index = food.ordinal();
        if (portions[index] < Integer.MAX_VALUE) {
            portions[index]++;
        }
    }

    long total() {
        long total = 0;
        for (int count : portions) {
            total += count;
        }
        return total;
    }

    int enchantedApples() {
        return count(HatchlingVariant.PRISMATIC);
    }

    HatchlingVariant variant() {
        if (2L * enchantedApples() > total()) {
            return HatchlingVariant.PRISMATIC;
        }
        // With no feed history or a tie, the first of the leading foods wins in this fixed order.
        HatchlingVariant leader = HatchlingVariant.AZURE;
        for (HatchlingVariant candidate : HatchlingVariant.ALL) {
            if (candidate != HatchlingVariant.PRISMATIC && count(candidate) > count(leader)) {
                leader = candidate;
            }
        }
        return leader;
    }

    private int count(HatchlingVariant food) {
        return portions[food.ordinal()];
    }

    void save(ValueOutput output) {
        if (total() == 0) {
            return;
        }
        ValueOutput saved = output.child(SAVE_KEY);
        for (HatchlingVariant food : HatchlingVariant.ALL) {
            if (count(food) > 0) {
                saved.putInt(food.foodId(), count(food));
            }
        }
    }

    void load(ValueInput input) {
        ValueInput saved = input.childOrEmpty(SAVE_KEY);
        for (HatchlingVariant food : HatchlingVariant.ALL) {
            portions[food.ordinal()] = Math.max(0, saved.getIntOr(food.foodId(), 0));
        }
    }
}

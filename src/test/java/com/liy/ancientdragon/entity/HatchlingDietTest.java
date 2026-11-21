package com.liy.ancientdragon.entity;

import static com.liy.ancientdragon.entity.HatchlingVariant.*;
import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.RegistryAccess;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.Test;

class HatchlingDietTest {
    @Test
    void enchantedApplesMustRemainAnAbsoluteMajority() {
        var diet = new HatchlingDiet();
        assertEquals(AZURE, diet.variant());
        diet.record(SCARLET);
        diet.record(PRISMATIC);
        assertEquals(SCARLET, diet.variant(), "Exactly one half does not qualify");
        diet.record(PRISMATIC);
        assertEquals(PRISMATIC, diet.variant());
        diet.record(VERDANT);
        assertEquals(SCARLET, diet.variant(), "Dropping to one half reverts the color");
        diet.record(PRISMATIC);
        assertEquals(PRISMATIC, diet.variant());
    }

    @Test
    void mostFedOrdinaryFoodWinsAndTiesAreDeterministic() {
        var diet = new HatchlingDiet();
        diet.record(VERDANT);
        assertEquals(VERDANT, diet.variant());
        diet.record(VIOLET);
        assertEquals(VERDANT, diet.variant());
        diet.record(VIOLET);
        diet.record(PRISMATIC);
        diet.record(PRISMATIC);
        assertEquals(VIOLET, diet.variant(), "Apples below half do not participate in the ordinary plurality");
        diet.record(AMBER);
        diet.record(AMBER);
        diet.record(AMBER);
        assertEquals(AMBER, diet.variant());
    }

    @Test
    void keepsExactCountersAcrossNbtReloadsWithoutOverflow() {
        var source = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        var saved = source.child("Diet");
        saved.putInt(VIOLET.foodId(), Integer.MAX_VALUE);
        saved.putInt(PRISMATIC.foodId(), Integer.MAX_VALUE);
        saved.putInt(AZURE.foodId(), -10);
        var diet = new HatchlingDiet();
        diet.load(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, source.buildResult()));
        diet.record(PRISMATIC);
        assertEquals(2L * Integer.MAX_VALUE, diet.total());
        assertEquals(VIOLET, diet.variant());

        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        diet.save(output);
        var restored = new HatchlingDiet();
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, output.buildResult()));
        assertEquals(Integer.MAX_VALUE, restored.enchantedApples());
        assertEquals(diet.total(), restored.total());
        assertEquals(diet.variant(), restored.variant());
    }

    @Test
    void prismaticHasTheHighestDietStats() {
        var prism = PRISMATIC.stats();
        for (var other : HatchlingVariant.values()) {
            if (other == PRISMATIC) {
                continue;
            }
            assertTrue(prism.maxHealth() > other.stats().maxHealth());
            assertTrue(prism.armor() > other.stats().armor());
            assertTrue(prism.groundSpeed() > other.stats().groundSpeed());
            assertTrue(prism.flightSpeed() > other.stats().flightSpeed());
        }
    }
}

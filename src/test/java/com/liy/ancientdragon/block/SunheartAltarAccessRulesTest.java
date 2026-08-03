package com.liy.ancientdragon.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SunheartAltarAccessRulesTest {
    @Test
    void forgeRequestsMustTargetTheAltarAndStayWithinEightBlocks() {
        assertTrue(SunheartAltarAccessRules.mayUse(true, 64.0D));
        assertFalse(SunheartAltarAccessRules.mayUse(true, 64.01D));
        assertFalse(SunheartAltarAccessRules.mayUse(false, 0.0D));
    }
}

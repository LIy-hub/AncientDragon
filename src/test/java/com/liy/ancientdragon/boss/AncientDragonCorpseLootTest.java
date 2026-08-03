package com.liy.ancientdragon.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class AncientDragonCorpseLootTest {
    @Test
    void twoEligibleParticipantsReceiveTheOriginalCorpseYield() {
        assertEquals(2, AncientDragonCorpseLoot.BASELINE_PARTICIPANTS);
        assertEquals(2, AncientDragonCorpseLoot.countForHarvestNode("horns", 2));
        assertEquals(24, AncientDragonCorpseLoot.countForHarvestNode("scales", 2));
        assertEquals(16, AncientDragonCorpseLoot.countForHarvestNode("left_membrane", 2));
        assertEquals(16, AncientDragonCorpseLoot.countForHarvestNode("right_membrane", 2));
        assertEquals(20, AncientDragonCorpseLoot.countForHarvestNode("bones", 2));
        assertEquals(1, AncientDragonCorpseLoot.countForHarvestNode("heart", 2));
    }

    @Test
    void ordinaryMaterialsScalePerEligibleParticipantButTheHeartRemainsUnique() {
        assertEquals(1, AncientDragonCorpseLoot.countForHarvestNode("horns", 1));
        assertEquals(12, AncientDragonCorpseLoot.countForHarvestNode("scales", 1));
        assertEquals(8, AncientDragonCorpseLoot.countForHarvestNode("left_membrane", 1));
        assertEquals(8, AncientDragonCorpseLoot.countForHarvestNode("right_membrane", 1));
        assertEquals(10, AncientDragonCorpseLoot.countForHarvestNode("bones", 1));

        assertEquals(8, AncientDragonCorpseLoot.countForHarvestNode("horns", 8));
        assertEquals(96, AncientDragonCorpseLoot.countForHarvestNode("scales", 8));
        assertEquals(64, AncientDragonCorpseLoot.countForHarvestNode("left_membrane", 8));
        assertEquals(64, AncientDragonCorpseLoot.countForHarvestNode("right_membrane", 8));
        assertEquals(80, AncientDragonCorpseLoot.countForHarvestNode("bones", 8));
        assertEquals(1, AncientDragonCorpseLoot.countForHarvestNode("heart", 8));
    }

    @Test
    void missingEligibleParticipantsStillUseTheSoloYieldAndUnknownNodesFailClosed() {
        assertEquals(12, AncientDragonCorpseLoot.countForHarvestNode("scales", 0));
        assertThrows(IllegalArgumentException.class,
                () -> AncientDragonCorpseLoot.countForHarvestNode("not_a_corpse_part", 1));
    }

    @Test
    void unifiedAxeHarvestAllocatesOneCompleteOrdinarySharePerParticipant() {
        int soloOrdinaryShare = AncientDragonCorpseLoot.countForHarvestNode("horns", 1)
                + AncientDragonCorpseLoot.countForHarvestNode("scales", 1)
                + AncientDragonCorpseLoot.countForHarvestNode("left_membrane", 1)
                + AncientDragonCorpseLoot.countForHarvestNode("right_membrane", 1)
                + AncientDragonCorpseLoot.countForHarvestNode("bones", 1);
        int fourPlayerOrdinaryYield = AncientDragonCorpseLoot.countForHarvestNode("horns", 4)
                + AncientDragonCorpseLoot.countForHarvestNode("scales", 4)
                + AncientDragonCorpseLoot.countForHarvestNode("left_membrane", 4)
                + AncientDragonCorpseLoot.countForHarvestNode("right_membrane", 4)
                + AncientDragonCorpseLoot.countForHarvestNode("bones", 4);

        assertEquals(39, soloOrdinaryShare);
        assertEquals(soloOrdinaryShare * 4, fourPlayerOrdinaryYield);
        assertEquals(1, AncientDragonCorpseLoot.countForHarvestNode("heart", 4));
    }
}

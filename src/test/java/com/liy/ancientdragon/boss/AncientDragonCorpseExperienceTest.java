package com.liy.ancientdragon.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class AncientDragonCorpseExperienceTest {
    @Test
    void oneEligibleParticipantReceivesExactlyTheExperienceForLevel256FromZero() {
        assertEquals(256, AncientDragonCorpseExperience.TARGET_LEVEL);
        assertEquals(255_532, AncientDragonCorpseExperience.perEligibleParticipant());
        assertEquals(255_532, AncientDragonCorpseExperience.experienceFromZeroTo(256));
    }

    @Test
    void everyCorpseNodeTogetherPaysTheFullPerParticipantBudget() {
        int total = AncientDragonCorpseExperience.forHarvestNode("horns")
                + AncientDragonCorpseExperience.forHarvestNode("scales")
                + AncientDragonCorpseExperience.forHarvestNode("left_membrane")
                + AncientDragonCorpseExperience.forHarvestNode("right_membrane")
                + AncientDragonCorpseExperience.forHarvestNode("bones")
                + AncientDragonCorpseExperience.forHarvestNode("heart");

        assertEquals(AncientDragonCorpseExperience.perEligibleParticipant(), total);
        assertEquals(766_596L, AncientDragonCorpseExperience.totalForParticipants(3));
        assertEquals(0L, AncientDragonCorpseExperience.totalForParticipants(0));
    }

    @Test
    void unknownCorpseNodesCannotSilentlyAwardZeroExperience() {
        assertThrows(IllegalArgumentException.class,
                () -> AncientDragonCorpseExperience.forHarvestNode("not_a_corpse_part"));
    }
}

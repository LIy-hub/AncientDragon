package com.liy.ancientdragon.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.liy.ancientdragon.boss.AncientDragonEncounterRules.Attack;
import com.liy.ancientdragon.boss.AncientDragonEncounterRules.AttackDomain;
import com.liy.ancientdragon.boss.AncientDragonEncounterRules.Phase;
import org.junit.jupiter.api.Test;

final class AncientDragonAttackDirectorTest {
    @Test
    void repeatedIdenticalContextDoesNotRepeatTheSameAction() {
        AncientDragonAttackDirector director = new AncientDragonAttackDirector();
        AncientDragonAttackDirector.Context context =
                new AncientDragonAttackDirector.Context(Phase.MOUNTAIN, AttackDomain.AIR, 0, 0, 0, 2, 90.0D);
        Attack first = director.choose(context).attack();
        Attack second = director.choose(context).attack();
        assertEquals(Attack.DIVE_STRIKE, first);
        assertEquals(Attack.WIND_BLADE, second);
        assertNotEquals(first, second);
    }

    @Test
    void domainAndPhaseStrictlyLimitCandidateActions() {
        AncientDragonAttackDirector director = new AncientDragonAttackDirector();
        AncientDragonAttackDirector.Context ground =
                new AncientDragonAttackDirector.Context(Phase.MOUNTAIN, AttackDomain.GROUND, 3, 3, 0, 3, 12.0D);
        assertEquals(Attack.BITE_HEAVY, director.choose(ground).attack());

        AncientDragonAttackDirector.Context stormGround =
                new AncientDragonAttackDirector.Context(Phase.STORM, AttackDomain.GROUND, 4, 0, 0, 4, 35.0D);
        assertEquals(Attack.GROUND_STORM_BURST, director.choose(stormGround).attack());

        AncientDragonAttackDirector.Context solarGround =
                new AncientDragonAttackDirector.Context(Phase.SOLAR, AttackDomain.GROUND, 4, 0, 0, 4, 55.0D);
        assertEquals(Attack.GROUND_SOLAR_BREATH, director.choose(solarGround).attack());
    }

    @Test
    void newActionsFillDistinctRangeAndControlRoles() {
        AncientDragonAttackDirector director = new AncientDragonAttackDirector();
        AncientDragonAttackDirector.Context farMountain =
                new AncientDragonAttackDirector.Context(Phase.MOUNTAIN, AttackDomain.AIR, 0, 0, 0, 0, 200.0D);
        assertEquals(Attack.WIND_BLADE, director.choose(farMountain).attack());

        AncientDragonAttackDirector.Context clusteredStorm =
                new AncientDragonAttackDirector.Context(Phase.STORM, AttackDomain.AIR, 4, 4, 0, 1, 24.0D);
        assertEquals(Attack.STORM_CAGE, director.choose(clusteredStorm).attack());

        AncientDragonAttackDirector.Context farGround =
                new AncientDragonAttackDirector.Context(Phase.MOUNTAIN, AttackDomain.GROUND, 0, 0, 0, 1, 100.0D);
        assertEquals(Attack.RIFT_CLAW, director.choose(farGround).attack());
    }

    @Test
    void groundMeleeIsNotSelectedWhenNoParticipantIsWithinMeleeRange() {
        AncientDragonAttackDirector director = new AncientDragonAttackDirector();
        AncientDragonAttackDirector.Context farGround =
                new AncientDragonAttackDirector.Context(Phase.MOUNTAIN, AttackDomain.GROUND, 0, 0, 0, 1, 70.0D);

        AncientDragonAttackDirector.Decision first = director.choose(farGround);
        AncientDragonAttackDirector.Decision second = director.choose(farGround);

        assertEquals(Attack.RIFT_CLAW, first.attack());
        assertEquals(Attack.RIFT_CLAW, second.attack());
        assertFalse(first.scores().containsKey(Attack.BITE_HEAVY));
        assertFalse(first.scores().containsKey(Attack.WING_SLAM));
    }
}

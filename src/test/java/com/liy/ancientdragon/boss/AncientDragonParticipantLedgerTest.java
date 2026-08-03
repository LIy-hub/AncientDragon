package com.liy.ancientdragon.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AncientDragonParticipantLedgerTest {
    private static final UUID ALPHA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRAVO = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void recentThreatTargetsForFiveSecondsBeforeAllowingAChange() {
        AncientDragonParticipantLedger ledger = new AncientDragonParticipantLedger();
        ledger.tick(List.of(ALPHA, BRAVO), 0);
        ledger.recordAcceptedDamage(ALPHA, 10.0D, 1, false);
        ledger.recordAcceptedDamage(BRAVO, 20.0D, 1, false);
        assertEquals(BRAVO, ledger.selectTarget(List.of(ALPHA, BRAVO), 1).orElseThrow());

        ledger.recordAcceptedDamage(ALPHA, 100.0D, 2, true);
        assertEquals(BRAVO, ledger.selectTarget(List.of(ALPHA, BRAVO), 100).orElseThrow());
        assertEquals(ALPHA, ledger.selectTarget(List.of(ALPHA, BRAVO), 101).orElseThrow());
    }

    @Test
    void scalingOnlyMovesUpAndEmptyRosterCountsToThirtySeconds() {
        AncientDragonParticipantLedger ledger = new AncientDragonParticipantLedger();
        ledger.tick(List.of(ALPHA, BRAVO), 0);
        ledger.tick(List.of(ALPHA), 1);
        assertEquals(2, ledger.maxParticipantsSeen());
        for (int tick = 0; tick < 600; tick++) {
            ledger.tick(List.of(), tick + 2L);
        }
        assertEquals(600, ledger.emptyTicks());
        ledger.tick(List.of(ALPHA), 602);
        assertEquals(0, ledger.emptyTicks());
        assertEquals(2, ledger.maxParticipantsSeen());
    }

    @Test
    void rewardEligibilityAcceptsTimeOrMeaningfulDamage() {
        AncientDragonParticipantLedger ledger = new AncientDragonParticipantLedger();
        ledger.recordAcceptedDamage(BRAVO, 6.0D, 0, false);
        assertTrue(ledger.eligibleForReward(BRAVO, 600.0D));
        assertFalse(ledger.eligibleForReward(ALPHA, 600.0D));
        for (int tick = 0; tick < 1_200; tick++) {
            ledger.tick(List.of(ALPHA), tick);
        }
        assertTrue(ledger.eligibleForReward(ALPHA, 600.0D));
    }
}

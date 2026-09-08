package com.liy.ancientdragon.compat;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class LegacyEncounterNbtTest {
    @Test void encounterAndParticipantFieldsRoundTripWithoutChangingNamesOrNumericWidths() {
        CompoundTag tag = new CompoundTag();
        LegacyValueOutput output = new LegacyValueOutput(tag);
        output.putString("BossState", "corpse");
        output.putInt("CorpseDissipationTicks", -1);
        output.putLong("TargetLockUntil", Long.MIN_VALUE);
        output.putDouble("FlightVelocityX", 0.125);
        var participant = output.childrenList("Participants").addChild();
        participant.putString("Player", "00000000-0000-0000-0000-000000000001");
        participant.putDouble("LifetimeDamage", 123.5);
        var input = new LegacyValueInput(tag);
        assertEquals("corpse", input.getStringOr("BossState", "dormant"));
        assertEquals(-1, input.getIntOr("CorpseDissipationTicks", 0));
        assertEquals(Long.MIN_VALUE, input.getLongOr("TargetLockUntil", 0));
        assertEquals(0.125, input.getDoubleOr("FlightVelocityX", 0));
        assertEquals(123.5, input.childrenListOrEmpty("Participants").getFirst().getDoubleOr("LifetimeDamage", 0));
    }
    @Test void oldOrMalformedFieldsUseTheExistingEncounterDefaults() {
        CompoundTag tag = new CompoundTag();
        tag.putString("StateTicks", "bad");
        tag.putInt("Participants", 9);
        LegacyValueInput input = new LegacyValueInput(tag);
        assertEquals(7, input.getIntOr("StateTicks", 7));
        assertEquals("dormant", input.getStringOr("BossState", "dormant"));
        assertTrue(input.childrenListOrEmpty("Participants").isEmpty());
        assertTrue(input.childrenListOrEmpty("PendingCorpseLoot").isEmpty());
    }
}

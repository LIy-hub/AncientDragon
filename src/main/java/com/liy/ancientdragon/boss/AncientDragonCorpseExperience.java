package com.liy.ancientdragon.boss;

import java.util.Map;

/**
 * Fixed experience budget paid to every eligible victor as the dragon corpse is
 * harvested. The per-player budget exactly equals the vanilla experience required to progress
 * from level 0 to level 256.
 */
public final class AncientDragonCorpseExperience {
    public static final int TARGET_LEVEL = 256;
    private static final int EXPERIENCE_PER_ELIGIBLE_PARTICIPANT = experienceFromZeroTo(TARGET_LEVEL);
    private static final Map<String, Integer> EXPERIENCE_BY_NODE = Map.of(
            "horns", 38_330,
            "scales", 25_553,
            "left_membrane", 19_165,
            "right_membrane", 19_165,
            "bones", 25_553,
            "heart", 127_766);

    static {
        int allocated = EXPERIENCE_BY_NODE.values().stream().mapToInt(Integer::intValue).sum();
        if (allocated != EXPERIENCE_PER_ELIGIBLE_PARTICIPANT) {
            throw new IllegalStateException("Ancient Dragon corpse experience allocation must match its player budget");
        }
    }

    private AncientDragonCorpseExperience() {
    }

    public static int perEligibleParticipant() {
        return EXPERIENCE_PER_ELIGIBLE_PARTICIPANT;
    }

    public static long totalForParticipants(int eligibleParticipants) {
        return (long) EXPERIENCE_PER_ELIGIBLE_PARTICIPANT * Math.max(0, eligibleParticipants);
    }

    public static int forHarvestNode(String serializedNode) {
        Integer experience = EXPERIENCE_BY_NODE.get(serializedNode);
        if (experience == null) {
            throw new IllegalArgumentException("Unknown Ancient Dragon corpse harvest node: " + serializedNode);
        }
        return experience;
    }

    static int experienceFromZeroTo(int targetLevel) {
        if (targetLevel < 0) {
            throw new IllegalArgumentException("Target level cannot be negative");
        }
        int total = 0;
        for (int level = 0; level < targetLevel; level++) {
            total = Math.addExact(total, experienceRequiredForNextLevel(level));
        }
        return total;
    }

    private static int experienceRequiredForNextLevel(int level) {
        if (level >= 30) {
            return 9 * level - 158;
        }
        if (level >= 15) {
            return 5 * level - 38;
        }
        return 2 * level + 7;
    }
}

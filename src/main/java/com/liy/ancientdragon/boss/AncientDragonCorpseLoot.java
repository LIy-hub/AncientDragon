package com.liy.ancientdragon.boss;

/**
 * Material yield from the world-unique Ancient Dragon corpse. The original authored quantities are
 * the two-eligible-player baseline; ordinary materials scale linearly per eligible victor while
 * the dragon heart remains a single world relic.
 */
public final class AncientDragonCorpseLoot {
    public static final int BASELINE_PARTICIPANTS = 2;

    private AncientDragonCorpseLoot() {
    }

    public static int countForHarvestNode(String serializedNode, int eligibleParticipants) {
        int participants = Math.max(1, eligibleParticipants);
        return switch (serializedNode) {
            case "horns" -> participants;
            case "scales" -> Math.multiplyExact(12, participants);
            case "left_membrane", "right_membrane" -> Math.multiplyExact(8, participants);
            case "bones" -> Math.multiplyExact(10, participants);
            case "heart" -> 1;
            default -> throw new IllegalArgumentException(
                    "Unknown Ancient Dragon corpse harvest node: " + serializedNode);
        };
    }
}

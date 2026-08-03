package com.liy.ancientdragon.flight;

/** Pure eligibility rules for the server-side Sky Wing flight lifecycle. */
public final class SkyWingFlightRules {
    private SkyWingFlightRules() {
    }

    public static boolean grantsCreativeFlight(boolean creative, boolean spectator, boolean wearingSkyWing) {
        return !creative && !spectator && wearingSkyWing;
    }
}

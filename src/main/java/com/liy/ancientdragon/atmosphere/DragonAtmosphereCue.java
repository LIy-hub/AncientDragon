package com.liy.ancientdragon.atmosphere;

/** One-shot presentational landmarks emitted by the authoritative encounter. */
public enum DragonAtmosphereCue {
    AWAKEN,
    TAKEOFF,
    PHASE_STORM,
    PHASE_SOLAR,
    DIVE_IMPACT,
    WING_IMPACT,
    STORM_STRIKE,
    SOLAR_IGNITION,
    DEATH_IMPACT,
    CORPSE_SETTLE;

    public static DragonAtmosphereCue fromNetworkId(int networkId) {
        DragonAtmosphereCue[] values = values();
        return networkId >= 0 && networkId < values.length ? values[networkId] : AWAKEN;
    }
}

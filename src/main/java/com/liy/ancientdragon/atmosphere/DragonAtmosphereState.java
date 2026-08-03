package com.liy.ancientdragon.atmosphere;

/** Coarse encounter lifecycle used by the client atmosphere controller. */
public enum DragonAtmosphereState {
    DORMANT,
    AWAKENING,
    ACTIVE,
    DYING,
    CORPSE;

    public static DragonAtmosphereState fromNetworkId(int networkId) {
        DragonAtmosphereState[] values = values();
        return networkId >= 0 && networkId < values.length ? values[networkId] : DORMANT;
    }
}

package com.liy.ancientdragon.atmosphere;

/**
 * Compact phase identifiers sent to clients for presentation only.
 *
 * <p>The encounter rules remain server-authoritative; this enum exists so the client can choose
 * a local atmosphere without inferring gameplay state from animations.</p>
 */
public enum DragonAtmospherePhase {
    DORMANT,
    MOUNTAIN,
    STORM,
    SOLAR,
    DEATH;

    public static DragonAtmospherePhase fromNetworkId(int networkId) {
        DragonAtmospherePhase[] values = values();
        return networkId >= 0 && networkId < values.length ? values[networkId] : DORMANT;
    }
}

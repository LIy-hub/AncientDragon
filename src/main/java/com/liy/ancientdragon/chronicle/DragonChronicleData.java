package com.liy.ancientdragon.chronicle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Per-player, world-persistent unlock and deferred-delivery state for the four chronicle books. */
public final class DragonChronicleData extends SavedData {
    public static final Codec<DragonChronicleData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PlayerProgress.CODEC.listOf().optionalFieldOf("players", List.of())
                    .forGetter(DragonChronicleData::serializedPlayers)
    ).apply(instance, DragonChronicleData::new));

    public static final SavedDataType<DragonChronicleData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("ancient_dragon", "dragon_chronicle"),
            DragonChronicleData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, Progress> players;

    public DragonChronicleData() {
        this(List.of());
    }

    private DragonChronicleData(List<PlayerProgress> serializedPlayers) {
        players = new HashMap<>();
        for (PlayerProgress entry : serializedPlayers) {
            players.merge(entry.playerId(), new Progress(entry.unlockedBits(), entry.pendingBits()), Progress::merge);
        }
    }

    public static DragonChronicleData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Returns true exactly when this player unlocks this chapter for the first time. */
    public boolean unlock(UUID playerId, DragonChronicleEvent event) {
        if (playerId == null || event == null) {
            return false;
        }
        Progress current = players.getOrDefault(playerId, Progress.EMPTY);
        if ((current.unlockedBits() & event.bit()) != 0) {
            return false;
        }
        players.put(playerId, new Progress(current.unlockedBits() | event.bit(), current.pendingBits()));
        setDirty();
        return true;
    }

    /**
     * Unlocks a chapter and queues its physical book for a later safe delivery, such as after a
     * player respawns or returns to the server.
     */
    public boolean unlockAndQueue(UUID playerId, DragonChronicleEvent event) {
        if (playerId == null || event == null) {
            return false;
        }
        Progress current = players.getOrDefault(playerId, Progress.EMPTY);
        boolean newlyUnlocked = (current.unlockedBits() & event.bit()) == 0;
        int unlocked = current.unlockedBits() | event.bit();
        int pending = current.pendingBits() | event.bit();
        if (newlyUnlocked || pending != current.pendingBits()) {
            players.put(playerId, new Progress(unlocked, pending));
            setDirty();
        }
        return newlyUnlocked;
    }

    /** Removes and returns all books awaiting safe delivery for this player. */
    public List<DragonChronicleEvent> takePending(UUID playerId) {
        Progress current = players.get(playerId);
        if (current == null || current.pendingBits() == 0) {
            return List.of();
        }
        players.put(playerId, new Progress(current.unlockedBits(), 0));
        setDirty();
        return eventsForBits(current.pendingBits());
    }

    public boolean isUnlocked(UUID playerId, DragonChronicleEvent event) {
        Progress current = players.get(playerId);
        return current != null && (current.unlockedBits() & event.bit()) != 0;
    }

    public boolean isPending(UUID playerId, DragonChronicleEvent event) {
        Progress current = players.get(playerId);
        return current != null && (current.pendingBits() & event.bit()) != 0;
    }

    public List<DragonChronicleEvent> unlockedEvents(UUID playerId) {
        Progress current = players.get(playerId);
        return current == null ? List.of() : eventsForBits(current.unlockedBits());
    }

    private List<PlayerProgress> serializedPlayers() {
        return players.entrySet().stream()
                .map(entry -> new PlayerProgress(
                        entry.getKey(), entry.getValue().unlockedBits(), entry.getValue().pendingBits()))
                .sorted(Comparator.comparing(PlayerProgress::playerId))
                .toList();
    }

    private static List<DragonChronicleEvent> eventsForBits(int bits) {
        List<DragonChronicleEvent> events = new ArrayList<>();
        for (DragonChronicleEvent event : DragonChronicleEvent.values()) {
            if ((bits & event.bit()) != 0) {
                events.add(event);
            }
        }
        return List.copyOf(events);
    }

    /** Codec-stable persisted form. Pending bits are always a subset of unlocked bits. */
    public record PlayerProgress(UUID playerId, int unlockedBits, int pendingBits) {
        private static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerProgress::playerId),
                Codec.INT.optionalFieldOf("unlocked", 0).forGetter(PlayerProgress::unlockedBits),
                Codec.INT.optionalFieldOf("pending", 0).forGetter(PlayerProgress::pendingBits)
        ).apply(instance, PlayerProgress::new));

        public PlayerProgress {
            if (playerId == null) {
                throw new IllegalArgumentException("Chronicle progress requires a player UUID");
            }
            unlockedBits &= DragonChronicleEvent.allBits();
            pendingBits &= unlockedBits;
        }
    }

    private record Progress(int unlockedBits, int pendingBits) {
        private static final Progress EMPTY = new Progress(0, 0);

        private Progress merge(Progress other) {
            int unlocked = unlockedBits | other.unlockedBits;
            return new Progress(unlocked, (pendingBits | other.pendingBits) & unlocked);
        }
    }
}

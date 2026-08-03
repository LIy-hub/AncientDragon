package com.liy.ancientdragon.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World-persistent delivery queue for corpse shares allocated to players who were offline when
 * the corpse was harvested. Keeping this outside the entity allows the physical corpse to fade
 * without losing a valid participant's reward.
 */
public final class AncientDragonCorpseRewardData extends SavedData {
    public static final Codec<AncientDragonCorpseRewardData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PlayerRewards.CODEC.listOf().optionalFieldOf("players", List.of())
                    .forGetter(AncientDragonCorpseRewardData::serializedPlayers)
    ).apply(instance, AncientDragonCorpseRewardData::new));

    public static final SavedDataType<AncientDragonCorpseRewardData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("ancient_dragon", "ancient_dragon_corpse_rewards"),
            AncientDragonCorpseRewardData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, PendingRewards> pendingByPlayer;

    public AncientDragonCorpseRewardData() {
        this(List.of());
    }

    private AncientDragonCorpseRewardData(List<PlayerRewards> serializedPlayers) {
        pendingByPlayer = new HashMap<>();
        for (PlayerRewards playerRewards : serializedPlayers) {
            mergeLoaded(playerRewards);
        }
    }

    public static AncientDragonCorpseRewardData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public void queueExperience(UUID playerId, int experience) {
        if (playerId == null || experience <= 0) {
            return;
        }
        PendingRewards rewards = pendingByPlayer.computeIfAbsent(playerId, ignored -> new PendingRewards());
        rewards.experience = Math.addExact(rewards.experience, experience);
        setDirty();
    }

    public void queueLoot(UUID playerId, String loot, int count) {
        String normalizedLoot = normalizeLoot(loot);
        if (playerId == null || normalizedLoot == null || count <= 0) {
            return;
        }
        PendingRewards rewards = pendingByPlayer.computeIfAbsent(playerId, ignored -> new PendingRewards());
        rewards.loot.merge(normalizedLoot, count, Math::addExact);
        setDirty();
    }

    /** Removes one player's queued share after the live player has been selected for delivery. */
    public Optional<PlayerRewards> take(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        PendingRewards rewards = pendingByPlayer.remove(playerId);
        if (rewards == null) {
            return Optional.empty();
        }
        setDirty();
        return Optional.of(toRecord(playerId, rewards));
    }

    public boolean hasPending(UUID playerId) {
        return playerId != null && pendingByPlayer.containsKey(playerId);
    }

    private void mergeLoaded(PlayerRewards playerRewards) {
        if (playerRewards == null) {
            return;
        }
        PendingRewards rewards = pendingByPlayer.computeIfAbsent(
                playerRewards.playerId(), ignored -> new PendingRewards());
        if (playerRewards.experience() > 0) {
            rewards.experience = Math.addExact(rewards.experience, playerRewards.experience());
        }
        for (LootEntry loot : playerRewards.loot()) {
            rewards.loot.merge(loot.loot(), loot.count(), Math::addExact);
        }
    }

    private List<PlayerRewards> serializedPlayers() {
        return pendingByPlayer.entrySet().stream()
                .map(entry -> toRecord(entry.getKey(), entry.getValue()))
                .filter(rewards -> rewards.experience() > 0 || !rewards.loot().isEmpty())
                .sorted(Comparator.comparing(PlayerRewards::playerId))
                .toList();
    }

    private static PlayerRewards toRecord(UUID playerId, PendingRewards rewards) {
        List<LootEntry> loot = rewards.loot.entrySet().stream()
                .map(entry -> new LootEntry(entry.getKey(), entry.getValue()))
                .toList();
        return new PlayerRewards(playerId, rewards.experience, loot);
    }

    private static String normalizeLoot(String loot) {
        return switch (loot == null ? "" : loot) {
            case "horns", "scales", "left_membrane", "right_membrane", "bones" -> loot;
            default -> null;
        };
    }

    /** Codec-stable, immutable delivery form. */
    public record PlayerRewards(UUID playerId, int experience, List<LootEntry> loot) {
        private static final Codec<PlayerRewards> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerRewards::playerId),
                Codec.INT.optionalFieldOf("experience", 0).forGetter(PlayerRewards::experience),
                LootEntry.CODEC.listOf().optionalFieldOf("loot", List.of()).forGetter(PlayerRewards::loot)
        ).apply(instance, PlayerRewards::new));

        public PlayerRewards {
            Objects.requireNonNull(playerId, "Corpse reward requires a player UUID");
            experience = Math.max(0, experience);
            Map<String, Integer> canonicalLoot = new TreeMap<>();
            if (loot != null) {
                for (LootEntry entry : loot) {
                    if (entry != null && entry.isValid()) {
                        canonicalLoot.merge(entry.loot(), entry.count(), Math::addExact);
                    }
                }
            }
            loot = canonicalLoot.entrySet().stream()
                    .map(entry -> new LootEntry(entry.getKey(), entry.getValue()))
                    .toList();
        }
    }

    /** One queued ordinary-material bundle. The unique heart is always handed over immediately. */
    public record LootEntry(String loot, int count) {
        private static final Codec<LootEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("loot").forGetter(LootEntry::loot),
                Codec.INT.optionalFieldOf("count", 0).forGetter(LootEntry::count)
        ).apply(instance, LootEntry::new));

        public LootEntry {
            loot = normalizeLoot(loot);
            count = Math.max(0, count);
        }

        private boolean isValid() {
            return loot != null && count > 0;
        }
    }

    private static final class PendingRewards {
        private int experience;
        private final Map<String, Integer> loot = new TreeMap<>();
    }
}

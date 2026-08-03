package com.liy.ancientdragon.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Idempotent world record for the one placed Sacred Mountain encounter. */
public final class AncientDragonEncounterData extends SavedData {
    public static final Codec<AncientDragonEncounterData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Encounter.CODEC.optionalFieldOf("encounter").forGetter(data -> data.encounter)
    ).apply(instance, AncientDragonEncounterData::new));

    public static final SavedDataType<AncientDragonEncounterData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("ancient_dragon", "ancient_dragon_encounter"),
            AncientDragonEncounterData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private Optional<Encounter> encounter;

    public AncientDragonEncounterData() {
        this(Optional.empty());
    }

    private AncientDragonEncounterData(Optional<Encounter> encounter) {
        this.encounter = encounter == null ? Optional.empty() : encounter;
    }

    public static AncientDragonEncounterData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<Encounter> encounter() {
        return encounter;
    }

    public void bindPlacement(String placementId, String dimension, UUID dragonUuid) {
        if (placementId == null || placementId.isBlank() || dimension == null || dimension.isBlank()
                || dragonUuid == null) {
            throw new IllegalArgumentException("Incomplete Ancient Dragon encounter binding");
        }
        if (encounter.isEmpty()) {
            encounter = Optional.of(new Encounter(
                    placementId, dimension, dragonUuid.toString(), Stage.DORMANT, List.of()));
            setDirty();
            return;
        }
        Encounter current = encounter.get();
        if (!current.placementId().equals(placementId)) {
            throw new IllegalStateException("A different Sacred Mountain encounter is already bound");
        }
        if (!current.dragonUuid().equals(dragonUuid)) {
            throw new IllegalStateException("A bound Ancient Dragon cannot be replaced automatically");
        }
    }

    public void updateStage(UUID dragonUuid, Stage stage, Collection<UUID> eligiblePlayers) {
        if (dragonUuid == null || stage == null) {
            return;
        }
        Encounter current = encounter.orElse(null);
        if (current == null || !current.dragonUuid().equals(dragonUuid)) {
            return; // Debug-spawned dragons are deliberately outside the world-unique record.
        }
        if (current.stage() == Stage.CORPSE && stage != Stage.CORPSE) {
            throw new IllegalStateException("A corpse encounter cannot return to a live stage");
        }
        List<String> eligible = eligiblePlayers == null
                ? current.eligiblePlayers()
                : eligiblePlayers.stream().sorted().map(UUID::toString).toList();
        encounter = Optional.of(new Encounter(
                current.placementId(), current.dimension(), current.dragonUuidText(), stage, eligible));
        setDirty();
    }

    public enum Stage {
        DORMANT("dormant"),
        ACTIVE("active"),
        DYING("dying"),
        CORPSE("corpse");

        public static final Codec<Stage> CODEC = Codec.STRING.xmap(Stage::parse, Stage::serializedName);
        private final String serializedName;

        Stage(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        private static Stage parse(String value) {
            for (Stage stage : values()) {
                if (stage.serializedName.equals(value)) {
                    return stage;
                }
            }
            return DORMANT;
        }
    }

    public record Encounter(
            String placementId,
            String dimension,
            String dragonUuidText,
            Stage stage,
            List<String> eligiblePlayers) {
        public static final Codec<Encounter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("placement_id").forGetter(Encounter::placementId),
                Codec.STRING.fieldOf("dimension").forGetter(Encounter::dimension),
                Codec.STRING.fieldOf("dragon_uuid").forGetter(Encounter::dragonUuidText),
                Stage.CODEC.fieldOf("stage").forGetter(Encounter::stage),
                Codec.STRING.listOf().optionalFieldOf("eligible_players", List.of())
                        .forGetter(Encounter::eligiblePlayers)
        ).apply(instance, Encounter::new));

        public Encounter {
            if (placementId == null || placementId.isBlank() || dimension == null || dimension.isBlank()
                    || dragonUuidText == null || stage == null || eligiblePlayers == null) {
                throw new IllegalArgumentException("Incomplete Ancient Dragon encounter record");
            }
            UUID.fromString(dragonUuidText);
            eligiblePlayers = eligiblePlayers.stream().map(value -> UUID.fromString(value).toString()).distinct().toList();
        }

        public UUID dragonUuid() {
            return UUID.fromString(dragonUuidText);
        }
    }
}

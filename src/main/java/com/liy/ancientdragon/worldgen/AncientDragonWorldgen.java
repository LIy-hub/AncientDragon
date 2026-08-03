package com.liy.ancientdragon.worldgen;

import com.liy.ancientdragon.AncientDragonMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/** Registries and stable identifiers for the natural Sacred Mountain. */
public final class AncientDragonWorldgen {
    public static final Identifier SACRED_MOUNTAIN_ID =
            Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "sacred_mountain");
    public static final ResourceKey<Structure> SACRED_MOUNTAIN_KEY =
            ResourceKey.create(Registries.STRUCTURE, SACRED_MOUNTAIN_ID);

    public static final StructureType<SacredMountainStructure> SACRED_MOUNTAIN_STRUCTURE_TYPE = Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            SACRED_MOUNTAIN_ID,
            () -> SacredMountainStructure.CODEC);

    public static final StructurePieceType.ContextlessType SACRED_MOUNTAIN_PIECE_TYPE = Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE,
            SACRED_MOUNTAIN_ID,
            (StructurePieceType.ContextlessType) SacredMountainStructurePiece::new);

    public static final StructurePlacementType<SacredMountainPlacement> SACRED_MOUNTAIN_PLACEMENT_TYPE =
            Registry.register(
                    BuiltInRegistries.STRUCTURE_PLACEMENT,
                    SACRED_MOUNTAIN_ID,
                    () -> SacredMountainPlacement.CODEC);

    private AncientDragonWorldgen() {
    }

    public static void initialize() {
        // Class initialization performs the built-in registry writes above.
    }
}

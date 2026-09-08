package com.liy.ancientdragon.compat;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Adapts the existing state codecs to the pre-1.21.5 native SavedData factory and save lifecycle. */
public final class LegacySavedDataType<T extends SavedData> {
    private final String id;
    private final Codec<T> codec;
    private final SavedData.Factory<T> factory;
    public LegacySavedDataType(String id, Supplier<T> constructor, Codec<T> codec, DataFixTypes dataFixType) {
        this.id = id;
        this.codec = codec;
        this.factory = new SavedData.Factory<>(constructor,
                (tag, registries) -> codec.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow(),
                dataFixType);
    }
    public String id() { return id; }
    public SavedData.Factory<T> factory() { return factory; }
    public CompoundTag save(T value, HolderLookup.Provider registries) {
        return (CompoundTag) codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), value).getOrThrow();
    }
}

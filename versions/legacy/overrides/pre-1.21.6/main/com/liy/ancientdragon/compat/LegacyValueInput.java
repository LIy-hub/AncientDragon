package com.liy.ancientdragon.compat;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** The existing encounter's ValueInput contract over older native NBT, including legacy defaults. */
public final class LegacyValueInput {
    private final CompoundTag tag;
    public LegacyValueInput(CompoundTag tag) { this.tag = tag; }
    public <T> Optional<T> read(String key, Codec<T> codec) {
        Tag value = tag.get(key);
        return value == null ? Optional.empty() : codec.parse(NbtOps.INSTANCE, value).result();
    }
    private <T> T read(String key, Codec<T> codec, T fallback) {
        return read(key, codec).orElse(fallback);
    }
    public LegacyValueInput childOrEmpty(String key) {
        return new LegacyValueInput(tag.get(key) instanceof CompoundTag child ? child : new CompoundTag());
    }
    public String getStringOr(String key, String fallback) { return read(key, Codec.STRING, fallback); }
    public int getIntOr(String key, int fallback) { return read(key, Codec.INT, fallback); }
    public long getLongOr(String key, long fallback) { return read(key, Codec.LONG, fallback); }
    public double getDoubleOr(String key, double fallback) { return read(key, Codec.DOUBLE, fallback); }
    public List<LegacyValueInput> childrenListOrEmpty(String key) {
        List<LegacyValueInput> children = new ArrayList<>();
        if (tag.get(key) instanceof ListTag list) for (Tag entry : list) {
            if (entry instanceof CompoundTag child) children.add(new LegacyValueInput(child));
        }
        return children;
    }
}

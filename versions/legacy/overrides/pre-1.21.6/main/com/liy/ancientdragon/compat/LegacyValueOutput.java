package com.liy.ancientdragon.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Writes every upstream encounter field to the same named compound/list layout used by ValueOutput. */
public final class LegacyValueOutput {
    private final CompoundTag tag;
    public LegacyValueOutput(CompoundTag tag) { this.tag = tag; }
    public void putString(String key, String value) { tag.putString(key, value); }
    public void putInt(String key, int value) { tag.putInt(key, value); }
    public void putLong(String key, long value) { tag.putLong(key, value); }
    public void putDouble(String key, double value) { tag.putDouble(key, value); }
    public ValueOutputList childrenList(String key) {
        ListTag children = new ListTag();
        tag.put(key, children);
        return new ValueOutputList(children);
    }
    public static final class ValueOutputList {
        private final ListTag children;
        private ValueOutputList(ListTag children) { this.children = children; }
        public LegacyValueOutput addChild() {
            CompoundTag child = new CompoundTag();
            children.add(child);
            return new LegacyValueOutput(child);
        }
    }
}

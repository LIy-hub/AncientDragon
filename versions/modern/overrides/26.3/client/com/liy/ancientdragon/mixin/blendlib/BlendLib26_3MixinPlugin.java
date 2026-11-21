package com.liy.ancientdragon.mixin.blendlib;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class BlendLib26_3MixinPlugin implements IMixinConfigPlugin {
    static final String SKINNED_RUNTIME = "com.liy.blendlib.fabric.client.animation.runtime.SkinnedAnimationRuntime";
    private static final String AFFECTED_VERSION = "1.0.0-beta.4+26.3";

    private static final Set<String> LIFECYCLE_AND_CACHE_METHODS = Set.of(
            "onPlayInit", "onWorldDisconnect", "onEntityUnload", "entityKey", "activeEntityKey",
            "onBlockEntityUnload", "onActiveGeneration", "extractInternal", "measurementSnapshot",
            "preparedAssetCount", "trackedClockCount");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return SKINNED_RUNTIME.equals(targetClassName) && FabricLoader.getInstance().getModContainer("blendlib")
                .filter(mod -> AFFECTED_VERSION.equals(mod.getMetadata().getVersion().getFriendlyString()))
                .isPresent();
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Render extraction and world teardown share the runtime caches. Both must hold the runtime monitor.
        for (var method : targetClass.methods) {
            if (LIFECYCLE_AND_CACHE_METHODS.contains(method.name)) {
                method.access |= Opcodes.ACC_SYNCHRONIZED;
            }
        }
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}

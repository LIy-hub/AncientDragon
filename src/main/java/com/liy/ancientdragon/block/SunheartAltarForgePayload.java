package com.liy.ancientdragon.block;

import com.liy.ancientdragon.AncientDragonMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

/** Client request to forge using the hand that opened a specific altar. */
public record SunheartAltarForgePayload(BlockPos altarPos, InteractionHand hand) implements CustomPacketPayload {
    public static final Type<SunheartAltarForgePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "sunheart_altar_forge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SunheartAltarForgePayload> CODEC =
            CustomPacketPayload.codec(SunheartAltarForgePayload::write, SunheartAltarForgePayload::read);

    private static SunheartAltarForgePayload read(RegistryFriendlyByteBuf buffer) {
        return new SunheartAltarForgePayload(
                buffer.readBlockPos(), buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(altarPos);
        buffer.writeBoolean(hand == InteractionHand.OFF_HAND);
    }

    @Override
    public Type<SunheartAltarForgePayload> type() {
        return TYPE;
    }
}

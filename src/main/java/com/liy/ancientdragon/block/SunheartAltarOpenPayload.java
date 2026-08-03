package com.liy.ancientdragon.block;

import com.liy.ancientdragon.AncientDragonMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

/** Server-authoritative request for the client to display a specific placed altar. */
public record SunheartAltarOpenPayload(BlockPos altarPos, InteractionHand hand) implements CustomPacketPayload {
    public static final Type<SunheartAltarOpenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "sunheart_altar_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SunheartAltarOpenPayload> CODEC =
            CustomPacketPayload.codec(SunheartAltarOpenPayload::write, SunheartAltarOpenPayload::read);

    private static SunheartAltarOpenPayload read(RegistryFriendlyByteBuf buffer) {
        return new SunheartAltarOpenPayload(
                buffer.readBlockPos(), buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(altarPos);
        buffer.writeBoolean(hand == InteractionHand.OFF_HAND);
    }

    @Override
    public Type<SunheartAltarOpenPayload> type() {
        return TYPE;
    }
}

package com.liy.ancientdragon.atmosphere;

import com.liy.ancientdragon.AncientDragonMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A one-shot client-side presentational cue. It deliberately carries no gameplay data. */
public record DragonAtmosphereCuePayload(int dragonEntityId, DragonAtmosphereCue cue, double x, double y, double z)
        implements CustomPacketPayload {
    public static final Type<DragonAtmosphereCuePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "atmosphere_cue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DragonAtmosphereCuePayload> CODEC =
            CustomPacketPayload.codec(DragonAtmosphereCuePayload::write, DragonAtmosphereCuePayload::read);

    private static DragonAtmosphereCuePayload read(RegistryFriendlyByteBuf buffer) {
        return new DragonAtmosphereCuePayload(
                buffer.readVarInt(),
                DragonAtmosphereCue.fromNetworkId(buffer.readUnsignedByte()),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(dragonEntityId);
        buffer.writeByte(cue.ordinal());
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
    }

    @Override
    public Type<DragonAtmosphereCuePayload> type() {
        return TYPE;
    }
}

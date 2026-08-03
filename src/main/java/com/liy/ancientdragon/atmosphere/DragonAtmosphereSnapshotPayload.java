package com.liy.ancientdragon.atmosphere;

import com.liy.ancientdragon.AncientDragonMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Periodic, idempotent encounter snapshot for clients within the local atmosphere range. */
public record DragonAtmosphereSnapshotPayload(
        int dragonEntityId,
        double x,
        double y,
        double z,
        DragonAtmospherePhase phase,
        DragonAtmosphereState state) implements CustomPacketPayload {
    public static final Type<DragonAtmosphereSnapshotPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "atmosphere_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DragonAtmosphereSnapshotPayload> CODEC =
            CustomPacketPayload.codec(DragonAtmosphereSnapshotPayload::write, DragonAtmosphereSnapshotPayload::read);

    private static DragonAtmosphereSnapshotPayload read(RegistryFriendlyByteBuf buffer) {
        return new DragonAtmosphereSnapshotPayload(
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                DragonAtmospherePhase.fromNetworkId(buffer.readUnsignedByte()),
                DragonAtmosphereState.fromNetworkId(buffer.readUnsignedByte()));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(dragonEntityId);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeByte(phase.ordinal());
        buffer.writeByte(state.ordinal());
    }

    @Override
    public Type<DragonAtmosphereSnapshotPayload> type() {
        return TYPE;
    }
}

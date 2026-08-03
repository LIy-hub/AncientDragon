package com.liy.ancientdragon.flight;

import com.liy.ancientdragon.AncientDragonMod;
import com.liy.ancientdragon.item.AncientDragonItems;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

/** Server-authoritative lifecycle for Sky Wing creative flight. */
public final class AncientDragonFlightManager {
    private static final AttachmentType<Boolean> SKY_WING_FLIGHT_GRANTED = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath(AncientDragonMod.MOD_ID, "sky_wing_flight_granted"), Codec.BOOL);

    private AncientDragonFlightManager() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server ->
                server.getPlayerList().getPlayers().forEach(AncientDragonFlightManager::synchronize));
    }

    private static void synchronize(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        boolean shouldGrant = SkyWingFlightRules.grantsCreativeFlight(
                false, false, AncientDragonItems.grantsSkyWingFlight(player.getItemBySlot(EquipmentSlot.CHEST)));
        boolean granted = player.getAttachedOrElse(SKY_WING_FLIGHT_GRANTED, false);
        if (shouldGrant && !granted) {
            player.setAttached(SKY_WING_FLIGHT_GRANTED, true);
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        } else if (!shouldGrant && granted) {
            player.setAttached(SKY_WING_FLIGHT_GRANTED, false);
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }
}

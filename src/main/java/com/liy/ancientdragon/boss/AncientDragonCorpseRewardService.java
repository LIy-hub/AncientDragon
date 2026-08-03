package com.liy.ancientdragon.boss;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.UUID;
import com.liy.ancientdragon.item.AncientDragonItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Delivers posthumous corpse shares after the physical Ancient Dragon corpse has dissipated. */
public final class AncientDragonCorpseRewardService {
    private AncientDragonCorpseRewardService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AncientDragonCorpseRewardService::deliverPendingRewards);
        ServerPlayerEvents.JOIN.register(AncientDragonCorpseRewardService::deliverPendingRewards);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> deliverPendingRewards(newPlayer));
    }

    public static void queueExperience(ServerLevel level, UUID playerId, int experience) {
        AncientDragonCorpseRewardData.get(level).queueExperience(playerId, experience);
    }

    public static void queueLoot(ServerLevel level, UUID playerId, String loot, int count) {
        AncientDragonCorpseRewardData.get(level).queueLoot(playerId, loot, count);
    }

    public static void giveLoot(ServerPlayer player, String loot, int count) {
        Item item = itemFor(loot);
        if (item == null || count <= 0) {
            return;
        }
        int remaining = count;
        while (remaining > 0) {
            int stackCount = Math.min(item.getDefaultMaxStackSize(), remaining);
            ItemStack stack = new ItemStack(item, stackCount);
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                ItemEntity overflow = player.drop(stack, false);
                if (overflow != null) {
                    overflow.setNoPickUpDelay();
                }
            }
            remaining -= stackCount;
        }
    }

    public static void giveExperience(ServerPlayer player, int experience) {
        if (experience <= 0) {
            return;
        }
        player.giveExperiencePoints(experience);
        player.sendSystemMessage(Component.translatable("message.ancient_dragon.corpse_experience", experience), true);
    }

    private static void deliverPendingRewards(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            deliverPendingRewards(player);
        }
    }

    private static void deliverPendingRewards(ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }
        AncientDragonCorpseRewardData.get(player.level()).take(player.getUUID()).ifPresent(rewards -> {
            for (AncientDragonCorpseRewardData.LootEntry loot : rewards.loot()) {
                giveLoot(player, loot.loot(), loot.count());
            }
            if (!rewards.loot().isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.ancient_dragon.corpse_loot_received"), true);
            }
            giveExperience(player, rewards.experience());
        });
    }

    private static Item itemFor(String loot) {
        return switch (loot == null ? "" : loot) {
            case "horns" -> AncientDragonItems.ANCIENT_HORN;
            case "scales" -> AncientDragonItems.ANCIENT_SCALE;
            case "left_membrane", "right_membrane" -> AncientDragonItems.ANCIENT_WING_MEMBRANE;
            case "bones" -> AncientDragonItems.ANCIENT_BONE;
            default -> null;
        };
    }
}

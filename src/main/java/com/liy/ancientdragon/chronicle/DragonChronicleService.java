package com.liy.ancientdragon.chronicle;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import com.liy.ancientdragon.entity.AncientDragonEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/** Delivers the Ancient Dragon's optional story books without changing encounter gameplay. */
public final class DragonChronicleService {
    private static final String BOOK_AUTHOR = "Ancient Dragon";

    private DragonChronicleService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DragonChronicleService::deliverPendingBooks);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player
                    && damageSource.getEntity() instanceof AncientDragonEntity) {
                recordDragonSlaying(player);
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> deliverPendingBooks(newPlayer));
        ServerPlayerEvents.JOIN.register(DragonChronicleService::deliverPendingBooks);
    }

    /** Immediately grants a newly unlocked non-death chapter to a live player. */
    public static boolean grant(ServerPlayer player, DragonChronicleEvent event) {
        DragonChronicleData data = DragonChronicleData.get(player.level());
        if (!data.unlock(player.getUUID(), event)) {
            return false;
        }
        giveBook(player, event);
        player.sendSystemMessage(Component.translatable(event.noticeKey()), true);
        return true;
    }

    /**
     * Records a dragon-caused death now, then waits to place the book until the player is alive
     * again. This keeps the book out of the death-drop path.
     */
    public static void recordDragonSlaying(ServerPlayer player) {
        DragonChronicleData data = DragonChronicleData.get(player.level());
        if (data.unlockAndQueue(player.getUUID(), DragonChronicleEvent.SLAIN_BY_DRAGON)) {
            player.sendSystemMessage(Component.translatable(
                    "message.ancient_dragon.chronicle.slain_by_dragon_death"), true);
        }
    }

    /** Delivers the final record to every eligible participant, including players currently offline. */
    public static void grantOrQueue(
            MinecraftServer server, Collection<UUID> playerIds, DragonChronicleEvent event) {
        if (playerIds == null) {
            return;
        }
        DragonChronicleData data = DragonChronicleData.get(server.overworld());
        for (UUID playerId : playerIds) {
            if (playerId == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && player.isAlive()) {
                grant(player, event);
            } else {
                data.unlockAndQueue(playerId, event);
            }
        }
    }

    /** Reissues every story book the requesting player has already unlocked. */
    public static int reissueUnlocked(ServerPlayer player) {
        List<DragonChronicleEvent> events = DragonChronicleData.get(player.level()).unlockedEvents(player.getUUID());
        for (DragonChronicleEvent event : events) {
            giveBook(player, event);
        }
        return events.size();
    }

    private static void deliverPendingBooks(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            deliverPendingBooks(player);
        }
    }

    private static void deliverPendingBooks(ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }
        DragonChronicleData data = DragonChronicleData.get(player.level());
        for (DragonChronicleEvent event : data.takePending(player.getUUID())) {
            giveBook(player, event);
            player.sendSystemMessage(Component.translatable(event.noticeKey()), true);
        }
    }

    private static void giveBook(ServerPlayer player, DragonChronicleEvent event) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = event.pages().stream().map(Filterable::passThrough).toList();
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(event.bookTitle()), BOOK_AUTHOR, 0, pages, true));
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
    }
}

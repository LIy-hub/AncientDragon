package com.liy.ancientdragon.client.screen;

import com.liy.ancientdragon.block.SunheartAltarForgePayload;
import com.liy.ancientdragon.block.SunheartAltarRite;
import com.liy.ancientdragon.block.SunheartAltarRituals;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

/**
 * A compact vanilla-style ritual panel. It intentionally previews the item in the hand used to open the altar
 * item and inventory materials rather than storing items client-side; the server owns every forge
 * decision and continues to validate the altar position, range, input and cost.
 */
public final class SunheartAltarScreen extends Screen {
    private static final int PANEL_WIDTH = 204;
    private static final int PANEL_HEIGHT = 166;
    private static final int SLOT_SIZE = 18;
    private static final int OUTER_BORDER = 0xFFB69A6A;
    private static final int INNER_BORDER = 0xFF5B472D;
    private static final int PANEL_FILL = 0xFF211A13;
    private static final int INSET_FILL = 0xFF110E0B;
    private static final int GOLD_TEXT = 0xFFE6C77F;
    private static final int MUTED_TEXT = 0xFFA7977D;
    private static final int READY_TEXT = 0xFF89C878;
    private static final int MISSING_TEXT = 0xFFE07171;

    private final BlockPos altarPos;
    private final InteractionHand hand;
    private Button forgeButton;

    public SunheartAltarScreen(BlockPos altarPos, InteractionHand hand) {
        super(Component.translatable("gui.ancient_dragon.sunheart.title"));
        this.altarPos = altarPos.immutable();
        this.hand = hand;
    }

    @Override
    protected void init() {
        int left = left();
        int top = top();
        forgeButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.ancient_dragon.sunheart.forge"),
                        button -> ClientPlayNetworking.send(new SunheartAltarForgePayload(altarPos, hand)))
                .bounds(left + 118, top + 88, 76, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"), button -> onClose())
                .bounds(left + 118, top + 116, 76, 20)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        if (forgeButton != null) {
            forgeButton.active = canForgeClientPreview();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        extractTransparentBackground(graphics);

        int left = left();
        int top = top();
        drawPanel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);
        graphics.text(font, title, left + 8, top + 8, GOLD_TEXT, false);
        graphics.text(
                font,
                Component.translatable("gui.ancient_dragon.sunheart.subtitle"),
                left + 8,
                top + 20,
                MUTED_TEXT,
                false);

        ItemStack input = currentInput();
        Optional<SunheartAltarRite> rite = SunheartAltarRituals.riteFor(input);
        ItemStack material = materialPreview(rite);
        ItemStack output = outputPreview(rite);

        graphics.text(font, Component.translatable("gui.ancient_dragon.sunheart.input"), left + 16, top + 42, MUTED_TEXT, false);
        drawSlot(graphics, left + 18, top + 56);
        drawItem(graphics, input, left + 19, top + 57, mouseX, mouseY);

        graphics.text(font, Component.translatable("gui.ancient_dragon.sunheart.material"), left + 70, top + 42, MUTED_TEXT, false);
        drawSlot(graphics, left + 75, top + 56);
        drawItem(graphics, material, left + 76, top + 57, mouseX, mouseY);

        graphics.centeredText(font, "→", left + 112, top + 59, GOLD_TEXT);
        graphics.text(font, Component.translatable("gui.ancient_dragon.sunheart.result"), left + 134, top + 42, MUTED_TEXT, false);
        drawSlot(graphics, left + 148, top + 56);
        drawItem(graphics, output, left + 149, top + 57, mouseX, mouseY);

        int available = rite.map(value -> availableMaterials(value)).orElse(0);
        Component status = status(rite, available);
        int statusColor = canForgeClientPreview() ? READY_TEXT : MISSING_TEXT;
        graphics.text(font, status, left + 12, top + 84, statusColor, false);

        graphics.text(
                font,
                Component.translatable("gui.ancient_dragon.sunheart.main_hand_hint"),
                left + 12,
                top + 143,
                MUTED_TEXT,
                false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private ItemStack currentInput() {
        return minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    private ItemStack materialPreview(Optional<SunheartAltarRite> rite) {
        if (rite.isEmpty() || rite.get().ingredientCost() == 0) {
            return ItemStack.EMPTY;
        }
        Item material = SunheartAltarRituals.ingredientFor(rite.get().ingredient());
        return new ItemStack(material, rite.get().ingredientCost());
    }

    private ItemStack outputPreview(Optional<SunheartAltarRite> rite) {
        return rite.map(value -> SunheartAltarRituals.outputFor(value).getDefaultInstance()).orElse(ItemStack.EMPTY);
    }

    private int availableMaterials(SunheartAltarRite rite) {
        if (minecraft.player == null || rite.ingredientCost() == 0) {
            return 0;
        }
        return SunheartAltarRituals.countIngredient(
                minecraft.player, SunheartAltarRituals.ingredientFor(rite.ingredient()));
    }

    private boolean canForgeClientPreview() {
        if (minecraft.player == null) {
            return false;
        }
        Optional<SunheartAltarRite> rite = SunheartAltarRituals.riteFor(currentInput());
        return rite.isPresent()
                && (minecraft.player.getAbilities().instabuild
                        || rite.get().ingredientCost() == 0
                        || availableMaterials(rite.get()) >= rite.get().ingredientCost());
    }

    private Component status(Optional<SunheartAltarRite> rite, int available) {
        if (rite.isEmpty()) {
            return Component.translatable("gui.ancient_dragon.sunheart.no_rite");
        }
        if (rite.get().ingredientCost() == 0) {
            return Component.translatable("gui.ancient_dragon.sunheart.ready");
        }
        if (minecraft.player != null && minecraft.player.getAbilities().instabuild) {
            return Component.translatable("gui.ancient_dragon.sunheart.ready_creative");
        }
        if (available < rite.get().ingredientCost()) {
            return Component.translatable(
                    "gui.ancient_dragon.sunheart.missing_material", available, rite.get().ingredientCost());
        }
        return Component.translatable("gui.ancient_dragon.sunheart.ready");
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, OUTER_BORDER);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, INNER_BORDER);
        graphics.fill(x + 4, y + 4, x + width - 4, y + height - 4, PANEL_FILL);
        graphics.fill(x + 8, y + 34, x + width - 8, y + 78, INSET_FILL);
        graphics.fill(x + 8, y + 80, x + width - 8, y + 82, INNER_BORDER);
    }

    private void drawSlot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF080706);
        graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFF8C7B5A);
        graphics.fill(x + 2, y + 2, x + SLOT_SIZE - 2, y + SLOT_SIZE - 2, 0xFF3B3021);
    }

    private void drawItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        if (stack.isEmpty()) {
            return;
        }
        graphics.item(stack, x, y);
        graphics.itemDecorations(font, stack, x, y);
        if (mouseX >= x - 1 && mouseX < x + SLOT_SIZE - 1 && mouseY >= y - 1 && mouseY < y + SLOT_SIZE - 1) {
            graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
    }
}

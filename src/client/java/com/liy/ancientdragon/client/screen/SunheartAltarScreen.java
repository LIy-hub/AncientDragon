package com.liy.ancientdragon.client.screen;

import com.liy.ancientdragon.block.SunheartAltarMenu;
import com.liy.ancientdragon.block.SunheartAltarRituals;
import com.liy.ancientdragon.item.AncientDragonItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Vanilla-smithing presentation for the server-owned Sunheart Altar menu. */
public final class SunheartAltarScreen extends ItemCombinerScreen<SunheartAltarMenu> {
    private static final Identifier SMITHING_TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/smithing.png");
    private static final Identifier ERROR_SPRITE =
            Identifier.withDefaultNamespace("container/smithing/error");

    private static final int HEART_X = 8;
    private static final int HEART_Y = 48;
    private static final int ERROR_X = 65;
    private static final int ERROR_Y = 46;
    private static final int ERROR_WIDTH = 28;
    private static final int ERROR_HEIGHT = 21;
    private static final int TOOLTIP_WIDTH = 115;

    private static final Vector3f ARMOR_STAND_TRANSLATION = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final Quaternionf ARMOR_STAND_ANGLE =
            new Quaternionf().rotationXYZ((float) Math.toRadians(25.0D), 0.0F, (float) Math.PI);

    private final ArmorStandRenderState armorStandPreview = new ArmorStandRenderState();

    public SunheartAltarScreen(SunheartAltarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SMITHING_TEXTURE);
        titleLabelX = 44;
        titleLabelY = 15;

        armorStandPreview.entityType = EntityType.ARMOR_STAND;
        armorStandPreview.showBasePlate = false;
        armorStandPreview.showArms = true;
        armorStandPreview.xRot = 25.0F;
        armorStandPreview.bodyRot = 210.0F;
    }

    @Override
    protected void subInit() {
        updateArmorStandPreview(menu.getSlot(SunheartAltarMenu.RESULT_SLOT).getItem());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        super.extractRenderState(graphics, mouseX, mouseY, deltaTicks);

        if (isHovering(HEART_X, HEART_Y, 16, 16, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(
                    font,
                    Component.translatable("gui.ancient_dragon.sunheart.embedded_heart_tooltip"),
                    mouseX,
                    mouseY);
            return;
        }

        if (menu.hasRecipeError()
                && isHovering(ERROR_X, ERROR_Y, ERROR_WIDTH, ERROR_HEIGHT, mouseX, mouseY)) {
            Component tooltip = errorTooltip();
            graphics.setTooltipForNextFrame(font, font.split(tooltip, TOOLTIP_WIDTH), mouseX, mouseY);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        super.extractBackground(graphics, mouseX, mouseY, deltaTicks);

        int heartLeft = leftPos + HEART_X;
        int heartTop = topPos + HEART_Y;
        graphics.fakeItem(AncientDragonItems.ANCIENT_DRAGON_HEART.getDefaultInstance(), heartLeft, heartTop);
        graphics.fill(heartLeft, heartTop, heartLeft + 16, heartTop + 16, 0x55000000);

        graphics.entity(
                armorStandPreview,
                25.0F,
                ARMOR_STAND_TRANSLATION,
                ARMOR_STAND_ANGLE,
                null,
                leftPos + 121,
                topPos + 20,
                leftPos + 161,
                topPos + 80);
    }

    @Override
    protected void extractErrorIcon(GuiGraphicsExtractor graphics, int left, int top) {
        if (menu.hasRecipeError()) {
            graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    ERROR_SPRITE,
                    left + ERROR_X,
                    top + ERROR_Y,
                    ERROR_WIDTH,
                    ERROR_HEIGHT);
        }
    }

    @Override
    public void slotChanged(AbstractContainerMenu container, int slotIndex, ItemStack stack) {
        if (slotIndex == SunheartAltarMenu.RESULT_SLOT) {
            updateArmorStandPreview(stack);
        }
    }

    private Component errorTooltip() {
        return switch (menu.errorReason()) {
            case MISSING_TARGET ->
                Component.translatable("gui.ancient_dragon.sunheart.error.missing_target");
            case WRONG_MATERIAL ->
                Component.translatable("gui.ancient_dragon.sunheart.error.wrong_material");
            case INSUFFICIENT_MATERIAL -> Component.translatable(
                    "gui.ancient_dragon.sunheart.error.insufficient_material",
                    menu.getSlot(SunheartAltarMenu.MATERIAL_SLOT).getItem().getCount(),
                    requiredMaterialCount());
            case UNNEEDED_MATERIAL ->
                Component.translatable("gui.ancient_dragon.sunheart.error.unneeded_material");
            case NONE -> Component.empty();
        };
    }

    private int requiredMaterialCount() {
        ItemStack target = menu.getSlot(SunheartAltarMenu.TARGET_SLOT).getItem();
        return SunheartAltarRituals.riteFor(target)
                .map(rite -> rite.ingredientCost())
                .orElse(0);
    }

    private void updateArmorStandPreview(ItemStack stack) {
        armorStandPreview.leftHandItemStack = ItemStack.EMPTY;
        armorStandPreview.leftHandItemState.clear();
        armorStandPreview.headEquipment = ItemStack.EMPTY;
        armorStandPreview.headItem.clear();
        armorStandPreview.chestEquipment = ItemStack.EMPTY;
        armorStandPreview.legsEquipment = ItemStack.EMPTY;
        armorStandPreview.feetEquipment = ItemStack.EMPTY;

        if (stack.isEmpty()) {
            return;
        }

        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        EquipmentSlot slot = equippable == null ? null : equippable.slot();
        var itemModelResolver = minecraft.getItemModelResolver();

        if (slot == EquipmentSlot.HEAD) {
            if (HumanoidArmorLayer.shouldRender(stack, EquipmentSlot.HEAD)) {
                armorStandPreview.headEquipment = stack.copy();
            } else {
                itemModelResolver.updateForTopItem(
                        armorStandPreview.headItem,
                        stack,
                        ItemDisplayContext.HEAD,
                        null,
                        null,
                        0);
            }
        } else if (slot == EquipmentSlot.CHEST) {
            armorStandPreview.chestEquipment = stack.copy();
        } else if (slot == EquipmentSlot.LEGS) {
            armorStandPreview.legsEquipment = stack.copy();
        } else if (slot == EquipmentSlot.FEET) {
            armorStandPreview.feetEquipment = stack.copy();
        } else {
            armorStandPreview.leftHandItemStack = stack.copy();
            itemModelResolver.updateForTopItem(
                    armorStandPreview.leftHandItemState,
                    stack,
                    ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                    null,
                    null,
                    0);
        }
    }
}

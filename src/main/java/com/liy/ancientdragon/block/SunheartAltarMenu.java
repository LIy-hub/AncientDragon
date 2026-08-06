package com.liy.ancientdragon.block;

import com.liy.ancientdragon.inventory.AncientDragonMenus;
import java.util.Optional;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative container lifecycle for the Sunheart Altar. */
public final class SunheartAltarMenu extends ItemCombinerMenu {
    public static final int TARGET_SLOT = 0;
    public static final int MATERIAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;

    private static final ItemCombinerMenuSlotDefinition SLOT_DEFINITION =
            ItemCombinerMenuSlotDefinition.create()
                    .withSlot(TARGET_SLOT, 26, 48, SunheartAltarRituals::isRitualTarget)
                    .withSlot(MATERIAL_SLOT, 44, 48, SunheartAltarRituals::isRitualMaterial)
                    .withResultSlot(RESULT_SLOT, 98, 48)
                    .build();

    private final DataSlot recipeError = DataSlot.standalone();
    private boolean resultQuickMovedThisClick;

    /** Client-side constructor used by the registered menu type. */
    public SunheartAltarMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    /** Server-side constructor bound to the altar that opened the menu. */
    public SunheartAltarMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(AncientDragonMenus.SUNHEART_ALTAR, containerId, inventory, access, SLOT_DEFINITION);
        addDataSlot(recipeError).set(RecipeError.NONE.ordinal());
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(AncientDragonBlocks.SUNHEART_ALTAR);
    }

    @Override
    public void createResult() {
        ItemStack target = inputSlots.getItem(TARGET_SLOT);
        ItemStack material = inputSlots.getItem(MATERIAL_SLOT);
        Optional<SunheartAltarRite> riteOptional = SunheartAltarRituals.riteFor(target);
        RecipeError error = evaluate(
                riteOptional, SunheartAltarRituals.materialKindFor(material), material.getCount());
        recipeError.set(error.ordinal());

        ItemStack result = riteOptional
                .filter(rite -> error == RecipeError.NONE)
                .map(rite -> transmuteResult(target, SunheartAltarRituals.outputFor(rite)))
                .orElse(ItemStack.EMPTY);
        resultSlots.setItem(0, result);
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasResult) {
        if (!hasResult || !stillValid(player)) {
            return false;
        }
        ItemStack target = inputSlots.getItem(TARGET_SLOT);
        ItemStack material = inputSlots.getItem(MATERIAL_SLOT);
        Optional<SunheartAltarRite> riteOptional = SunheartAltarRituals.riteFor(target);
        return riteOptional.isPresent()
                && evaluate(
                                riteOptional,
                                SunheartAltarRituals.materialKindFor(material),
                                material.getCount())
                        == RecipeError.NONE;
    }

    @Override
    protected void onTake(Player player, ItemStack result) {
        ItemStack target = inputSlots.getItem(TARGET_SLOT);
        ItemStack material = inputSlots.getItem(MATERIAL_SLOT);
        Optional<SunheartAltarRite> riteOptional = SunheartAltarRituals.riteFor(target);
        if (riteOptional.isEmpty()
                || evaluate(
                                riteOptional,
                                SunheartAltarRituals.materialKindFor(material),
                                material.getCount())
                        != RecipeError.NONE
                || !stillValid(player)) {
            createResult();
            return;
        }

        SunheartAltarRite rite = riteOptional.get();
        result.onCraftedBy(player, result.getCount());
        boolean instabuild = player.getAbilities().instabuild;
        consumeInputs(target, material, rite.ingredientCost(), instabuild);
        inputSlots.setItem(TARGET_SLOT, target);
        if (!instabuild && rite.ingredientCost() > 0) {
            inputSlots.setItem(MATERIAL_SLOT, material);
        }

        access.execute((level, altarPos) -> {
            level.playSound(
                    null, altarPos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.2F);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        ParticleTypes.END_ROD,
                        altarPos.getX() + 0.5D,
                        altarPos.getY() + 1.0D,
                        altarPos.getZ() + 0.5D,
                        24,
                        0.3D,
                        0.4D,
                        0.3D,
                        0.04D);
            }
        });
    }

    @Override
    protected boolean canMoveIntoInputSlots(ItemStack stack) {
        return SunheartAltarRituals.isRitualTarget(stack)
                || SunheartAltarRituals.isRitualMaterial(stack);
    }

    @Override
    public void clicked(
            int slotId, int button, ContainerInput input, Player player) {
        boolean quickMove = input == ContainerInput.QUICK_MOVE;
        if (quickMove) {
            resultQuickMovedThisClick = false;
        }
        try {
            super.clicked(slotId, button, input, player);
        } finally {
            if (quickMove) {
                resultQuickMovedThisClick = false;
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex != RESULT_SLOT) {
            return super.quickMoveStack(player, slotIndex);
        }
        if (resultQuickMovedThisClick) {
            return ItemStack.EMPTY;
        }

        ItemStack moved = super.quickMoveStack(player, slotIndex);
        if (!moved.isEmpty()) {
            // QUICK_MOVE loops while the result item stays the same. Cap this altar at one rite
            // per click so a stack of Ancient Horns cannot be converted in a single action.
            resultQuickMovedThisClick = true;
        }
        return moved;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    public RecipeError errorReason() {
        int value = recipeError.get();
        RecipeError[] values = RecipeError.values();
        return value >= 0 && value < values.length ? values[value] : RecipeError.NONE;
    }

    public boolean hasRecipeError() {
        return errorReason() != RecipeError.NONE;
    }

    static RecipeError evaluate(
            Optional<SunheartAltarRite> riteOptional,
            Optional<SunheartAltarRite.Ingredient> materialKind,
            int materialCount) {
        if (riteOptional.isEmpty()) {
            return materialCount == 0 ? RecipeError.NONE : RecipeError.MISSING_TARGET;
        }

        SunheartAltarRite rite = riteOptional.get();
        if (rite.ingredientCost() == 0) {
            return materialCount == 0 ? RecipeError.NONE : RecipeError.UNNEEDED_MATERIAL;
        }
        if (materialCount == 0) {
            return RecipeError.INSUFFICIENT_MATERIAL;
        }
        if (materialKind.isEmpty() || materialKind.get() != rite.ingredient()) {
            return RecipeError.WRONG_MATERIAL;
        }
        if (materialCount < rite.ingredientCost()) {
            return RecipeError.INSUFFICIENT_MATERIAL;
        }
        return RecipeError.NONE;
    }

    static void consumeInputs(
            ItemStack target, ItemStack material, int materialCost, boolean instabuild) {
        target.shrink(1);
        if (!instabuild && materialCost > 0) {
            material.shrink(materialCost);
        }
    }

    static ItemStack transmuteResult(ItemStack target, Item output) {
        return target.transmuteCopy(output, 1);
    }

    public enum RecipeError {
        NONE,
        MISSING_TARGET,
        WRONG_MATERIAL,
        INSUFFICIENT_MATERIAL,
        UNNEEDED_MATERIAL
    }
}

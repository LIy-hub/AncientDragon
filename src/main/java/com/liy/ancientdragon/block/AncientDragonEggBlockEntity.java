package com.liy.ancientdragon.block;

import com.liy.ancientdragon.entity.AncientDragonEntities;
import com.liy.ancientdragon.entity.AncientDragonHatchling;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class AncientDragonEggBlockEntity extends BlockEntity {
    public static final int HATCH_TICKS = 2 * 60 * 20;
    private static final int CRACK_TICKS = HATCH_TICKS * 2 / 3;
    private UUID keeper;
    private int progress;

    public AncientDragonEggBlockEntity(BlockPos pos, BlockState state) {
        super(AncientDragonBlocks.DRAGON_EGG_ENTITY, pos, state);
    }

    public void setKeeper(UUID keeper) {
        this.keeper = keeper;
        setChanged();
    }

    public boolean canRecover(UUID player) {
        return keeper == null || keeper.equals(player);
    }

    void tick(ServerLevel level, BlockState state) {
        if (state.getValue(AncientDragonEggBlock.NESTED) || !AncientDragonEggBlock.hasSupport(level, worldPosition)) {
            return;
        }
        if (progress < HATCH_TICKS) {
            progress++;
            if (progress % 20 == 0) {
                setChanged();
            }
        }
        if (progress >= CRACK_TICKS && !state.getValue(AncientDragonEggBlock.CRACKED)) {
            level.setBlock(worldPosition, state.setValue(AncientDragonEggBlock.CRACKED, true), Block.UPDATE_CLIENTS);
            level.playSound(null, worldPosition, SoundEvents.TURTLE_EGG_CRACK, SoundSource.BLOCKS, 0.8F, 0.8F);
        }
        if (progress < HATCH_TICKS || level.getGameTime() % 20 != 0) {
            return;
        }

        AncientDragonHatchling hatchling = new AncientDragonHatchling(AncientDragonEntities.HATCHLING, level);
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY();
        double z = worldPosition.getZ() + 0.5;
        hatchling.setPos(x, y, z);
        hatchling.setKeeper(keeper);
        hatchling.setPersistenceRequired();
        // The egg is non-colliding. Leave it in place for a retry if the spawn space is occupied.
        if (!level.noCollision(hatchling) || !level.addFreshEntity(hatchling)) {
            return;
        }
        level.removeBlock(worldPosition, false);
        level.playSound(null, worldPosition, SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS, 0.9F, 0.8F);
        level.sendParticles(ParticleTypes.POOF, x, y + 0.32, z, 10, 0.18, 0.17, 0.18, 0.02);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        keeper = input.read("Keeper", UUIDUtil.CODEC).orElse(null);
        progress = Math.clamp(input.getIntOr("Incubation", 0), 0, HATCH_TICKS);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (keeper != null) {
            output.store("Keeper", UUIDUtil.CODEC, keeper);
        }
        output.putInt("Incubation", progress);
    }
}

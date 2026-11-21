package com.liy.ancientdragon.block;

import com.liy.ancientdragon.boss.AncientDragonEncounterData;
import com.liy.ancientdragon.item.AncientDragonItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AncientDragonEggBlock extends Block implements EntityBlock {
    public static final BooleanProperty NESTED = BooleanProperty.create("nested");
    public static final BooleanProperty CRACKED = BooleanProperty.create("cracked");
    private static final VoxelShape OUTLINE = Block.box(4, 0, 4, 12, 11.25, 12);

    public AncientDragonEggBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NESTED, false).setValue(CRACKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NESTED, CRACKED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return hasSupport(context.getLevel(), context.getClickedPos()) ? defaultBlockState() : null;
    }

    static boolean hasSupport(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof AncientDragonEggBlockEntity egg) {
            egg.setKeeper(player.getUUID());
            player.sendSystemMessage(Component.translatable("message.ancient_dragon.egg.placed"));
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AncientDragonEggBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel serverLevel) || type != AncientDragonBlocks.DRAGON_EGG_ENTITY) {
            return null;
        }
        return (ignored, pos, current, entity) -> ((AncientDragonEggBlockEntity) entity).tick(serverLevel, current);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interactWithEgg(state, level, pos, player);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        return interactWithEgg(state, level, pos, player);
    }

    private static InteractionResult interactWithEgg(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof AncientDragonEggBlockEntity egg) {
            if (state.getValue(NESTED)) {
                takeFromNest(serverLevel, pos, player);
            } else if (player.isShiftKeyDown()) {
                if (!egg.canRecover(player.getUUID())) {
                    tell(player, "reserved");
                } else if (offerEgg(player)) {
                    level.removeBlock(pos, false);
                }
            } else if (!hasSupport(level, pos)) {
                tell(player, "needs_support");
            } else {
                tell(player, state.getValue(CRACKED) ? "cracking" : "warming");
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void takeFromNest(ServerLevel level, BlockPos pos, Player player) {
        AncientDragonEncounterData data = AncientDragonEncounterData.get(level);
        if (data.eggClaimed()) {
            level.removeBlock(pos, false);
        } else if (!data.canClaimEgg(player.getUUID())) {
            tell(player, data.isDefeated() ? "victory_party" : "guarded");
        } else if (offerEgg(player)) {
            data.markEggClaimed();
            level.removeBlock(pos, false);
            tell(player, "claimed");
        }
    }

    private static boolean offerEgg(Player player) {
        if (player.getInventory().getFreeSlot() >= 0
                && player.getInventory().add(new ItemStack(AncientDragonItems.DRAGON_EGG))) {
            return true;
        }
        tell(player, "inventory_full");
        return false;
    }

    private static void tell(Player player, String message) {
        player.sendSystemMessage(Component.translatable("message.ancient_dragon.egg." + message));
    }
}

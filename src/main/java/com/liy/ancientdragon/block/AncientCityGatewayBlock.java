package com.liy.ancientdragon.block;

import com.liy.ancientdragon.chronicle.DragonChronicleEvent;
import com.liy.ancientdragon.chronicle.DragonChronicleService;
import com.liy.ancientdragon.worldgen.SacredMountainTravelService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The one-way sculk veil formed inside the unused Ancient City frame. */
public final class AncientCityGatewayBlock extends Block implements Portal {
    public static final MapCodec<AncientCityGatewayBlock> CODEC = simpleCodec(AncientCityGatewayBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final VoxelShape X_SHAPE = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    private static final VoxelShape Z_SHAPE = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);
    private static final int TRANSITION_TICKS = 40;

    public AncientCityGatewayBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    public MapCodec<AncientCityGatewayBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
    }

    @Override
    protected void entityInside(
            BlockState state,
            Level level,
            BlockPos pos,
            Entity entity,
            InsideBlockEffectApplier effects,
            boolean intersects) {
        if (entity instanceof Player && entity.canUsePortal(false)) {
            entity.setAsInsidePortal(this, pos);
        }
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return TRANSITION_TICKS;
    }

    @Override
    public TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos entryPosition) {
        if (!(entity instanceof ServerPlayer player)) {
            return null;
        }
        var preparation = SacredMountainTravelService.prepareDestination(level);
        if (!preparation.succeeded()) {
            player.sendSystemMessage(Component.translatable(
                    "message.ancient_dragon.gateway_destination_failed",
                    Component.translatable(preparation.failure().orElseThrow().translationKey())));
            return null;
        }
        return preparation.destination().map(destination -> {
            DragonChronicleService.grant(player, DragonChronicleEvent.GATE_OPENED);
            return destination.transition(entity);
        }).orElse(null);
    }

    @Override
    public Portal.Transition getLocalTransition() {
        return Portal.Transition.CONFUSION;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound(pos, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.45F, 0.65F, false);
        }
        Direction.Axis axis = state.getValue(AXIS);
        for (int index = 0; index < 2; index++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            if (axis == Direction.Axis.X) {
                z = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.2D;
            } else {
                x = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.2D;
            }
            level.addParticle(ParticleTypes.SCULK_CHARGE_POP, x, y, z, 0.0D, 0.015D, 0.0D);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }
}

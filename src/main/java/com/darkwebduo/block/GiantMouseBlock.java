package com.darkwebduo.block;

import com.darkwebduo.block.entity.GiantMonitorBlockEntity;
import com.darkwebduo.registry.ModSounds;
import com.darkwebduo.util.PcSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Giant gaming mouse. Right-click = left click on the nearest powered giant monitor. */
public class GiantMouseBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty PRESSED = BooleanProperty.create("pressed");

    private static final VoxelShape SHAPE_NS = Block.box(3.0D, 0.0D, 1.0D, 13.0D, 5.0D, 15.0D);
    private static final VoxelShape SHAPE_EW = Block.box(1.0D, 0.0D, 3.0D, 15.0D, 5.0D, 13.0D);

    public GiantMouseBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PRESSED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PRESSED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction d = state.getValue(FACING);
        return (d == Direction.NORTH || d == Direction.SOUTH) ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        level.setBlock(pos, state.setValue(PRESSED, Boolean.TRUE), 3);
        level.scheduleTick(pos, this, 4);
        level.playSound(null, pos, ModSounds.MOUSE_CLICK.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        GiantMonitorBlockEntity monitor = PcSetup.findMonitor(level, pos);
        if (monitor != null && monitor.isPowered()) {
            monitor.mouseClick();
        } else if (monitor == null) {
            player.displayClientMessage(Component.translatable("message.darkweb_duo.no_monitor"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PRESSED)) {
            level.setBlock(pos, state.setValue(PRESSED, Boolean.FALSE), 3);
        }
    }
}

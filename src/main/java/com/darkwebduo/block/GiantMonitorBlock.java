package com.darkwebduo.block;

import com.darkwebduo.block.entity.GiantMonitorBlockEntity;
import com.darkwebduo.registry.ModBlockEntities;
import com.darkwebduo.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Super-large monitor: a 5 wide x 3 tall multiblock. The block you place becomes the controller (bottom centre);
 * the other 14 blocks are parts. Only the controller owns a block entity + renderer (the giant live screen).
 *
 * FACING = direction the screen faces (towards the player who placed it).
 */
public class GiantMonitorBlock extends BaseEntityBlock {
    public static final int WIDTH = 5;
    public static final int HEIGHT = 3;
    public static final int CENTER_X = 2;

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** column inside the 5-wide grid, 0..4, counted along the viewer's right-hand direction */
    public static final IntegerProperty PX = IntegerProperty.create("px", 0, WIDTH - 1);
    /** row inside the grid, 0..2, counted upwards */
    public static final IntegerProperty PY = IntegerProperty.create("py", 0, HEIGHT - 1);

    private static final VoxelShape SHAPE_N = Block.box(0.0D, 0.0D, 10.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape SHAPE_S = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 6.0D);
    private static final VoxelShape SHAPE_W = Block.box(10.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape SHAPE_E = Block.box(0.0D, 0.0D, 0.0D, 6.0D, 16.0D, 16.0D);

    /** server-thread only guards used while the whole multiblock is being taken apart */
    private static boolean dismantling = false;
    private static boolean suppressDrop = false;

    public GiantMonitorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PX, CENTER_X).setValue(PY, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PX, PY);
    }

    public static boolean isController(BlockState state) {
        return state.getValue(PX) == CENTER_X && state.getValue(PY) == 0;
    }

    /** viewer's right-hand direction for a screen facing {@code facing}. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    public static BlockPos controllerPos(BlockPos pos, BlockState state) {
        Direction r = right(state.getValue(FACING));
        return pos.relative(r, CENTER_X - state.getValue(PX)).below(state.getValue(PY));
    }

    public static BlockPos partPos(BlockPos controller, Direction facing, int px, int py) {
        return controller.relative(right(facing), px - CENTER_X).above(py);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        BlockPos origin = ctx.getClickedPos();
        Level level = ctx.getLevel();
        for (int py = 0; py < HEIGHT; py++) {
            for (int px = 0; px < WIDTH; px++) {
                BlockPos p = partPos(origin, facing, px, py);
                if (p.getY() >= level.getMaxBuildHeight() || !level.getBlockState(p).canBeReplaced(ctx)) {
                    return null;
                }
            }
        }
        return this.defaultBlockState().setValue(FACING, facing).setValue(PX, CENTER_X).setValue(PY, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;
        Direction facing = state.getValue(FACING);
        for (int py = 0; py < HEIGHT; py++) {
            for (int px = 0; px < WIDTH; px++) {
                if (px == CENTER_X && py == 0) continue;
                BlockPos p = partPos(pos, facing, px, py);
                level.setBlock(p, this.defaultBlockState().setValue(FACING, facing).setValue(PX, px).setValue(PY, py), 3);
            }
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player.isCreative()) suppressDrop = true;
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && !dismantling) {
            dismantling = true;
            try {
                BlockPos ctrl = controllerPos(pos, state);
                Direction f = state.getValue(FACING);
                for (int py = 0; py < HEIGHT; py++) {
                    for (int px = 0; px < WIDTH; px++) {
                        BlockPos p = partPos(ctrl, f, px, py);
                        if (p.equals(pos)) continue;
                        BlockState s = level.getBlockState(p);
                        if (s.getBlock() == this && s.getValue(FACING) == f && s.getValue(PX) == px && s.getValue(PY) == py) {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
                        }
                    }
                }
                if (!suppressDrop) {
                    Block.popResource(level, ctrl, new ItemStack(ModItems.GIANT_MONITOR.get()));
                }
            } finally {
                dismantling = false;
                suppressDrop = false;
            }
        }
        super.onRemove(state, level, pos, newState, moving);
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
        switch (state.getValue(FACING)) {
            case SOUTH:
                return SHAPE_S;
            case WEST:
                return SHAPE_W;
            case EAST:
                return SHAPE_E;
            default:
                return SHAPE_N;
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new GiantMonitorBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!isController(state)) return null;
        return createTickerHelper(type, ModBlockEntities.GIANT_MONITOR.get(), GiantMonitorBlockEntity::tick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos ctrl = controllerPos(pos, state);
        BlockEntity be = level.getBlockEntity(ctrl);
        if (!(be instanceof GiantMonitorBlockEntity)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            ((GiantMonitorBlockEntity) be).interact(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

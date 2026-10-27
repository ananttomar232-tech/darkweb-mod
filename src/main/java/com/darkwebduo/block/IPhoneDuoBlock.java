package com.darkwebduo.block;

import com.darkwebduo.block.entity.IPhoneDuoBlockEntity;
import com.darkwebduo.client.screen.PhoneScreens;
import com.darkwebduo.item.IPhoneDuoItem;
import com.darkwebduo.registry.ModBlockEntities;
import com.darkwebduo.registry.ModItems;
import com.darkwebduo.registry.ModSounds;
import com.darkwebduo.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

/**
 * The iPhone Duo as a display piece. The block model only holds the little levitation stand; the phone itself (hinge, glass,
 * camera plateau, live screens) is drawn by IPhoneDuoRenderer. Right-click opens the phone UI (and unfolds it),
 * sneak + right-click folds / unfolds it.
 */
public class IPhoneDuoBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 13.0D, 15.0D);

    public IPhoneDuoBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(OPEN, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(OPEN, Boolean.FALSE);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IPhoneDuoBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.IPHONE_DUO.get(), IPhoneDuoBlockEntity::tick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IPhoneDuoBlockEntity) {
            ((IPhoneDuoBlockEntity) be).setColorway(IPhoneDuoItem.colorOf(stack));
        }
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        int cw = be instanceof IPhoneDuoBlockEntity ? ((IPhoneDuoBlockEntity) be).getColorway() : 0;
        return IPhoneDuoItem.make(cw);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof IPhoneDuoBlockEntity)) return InteractionResult.PASS;
        final IPhoneDuoBlockEntity phone = (IPhoneDuoBlockEntity) be;

        if (phone.isAppearing()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.darkweb_duo.phone_busy"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                boolean nowOpen = toggle(level, pos, state, phone);
                player.displayClientMessage(Component.translatable(nowOpen ? "message.darkweb_duo.phone_unfolded" : "message.darkweb_duo.phone_folded"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            final BlockPos p = pos.immutable();
            final int cw = phone.getColorway();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PhoneScreens.open(p, cw));
        } else if (!state.getValue(OPEN)) {
            toggle(level, pos, state, phone);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** flips OPEN, plays the hinge sound and returns the new state */
    private static boolean toggle(Level level, BlockPos pos, BlockState state, IPhoneDuoBlockEntity phone) {
        boolean open = !state.getValue(OPEN);
        phone.beginFoldChange();
        level.setBlock(pos, state.setValue(OPEN, open), 3);
        if (level instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel) level;
            Vec3 c = new Vec3(pos.getX() + 0.5, pos.getY() + 0.45, pos.getZ() + 0.5);
            Fx.sound(sl, c, open ? ModSounds.PHONE_UNFOLD.get() : ModSounds.PHONE_FOLD.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            Fx.send(sl, ParticleTypes.END_ROD, c.x, c.y, c.z, 6, 0.3, 0.25, 0.08, 0.02);
        }
        return open;
    }
}

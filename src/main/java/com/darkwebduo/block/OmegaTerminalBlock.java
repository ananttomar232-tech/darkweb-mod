package com.darkwebduo.block;

import com.darkwebduo.block.entity.OmegaTerminalBlockEntity;
import com.darkwebduo.item.KeycardItem;
import com.darkwebduo.registry.ModBlockEntities;
import com.darkwebduo.registry.ModBlocks;
import com.darkwebduo.registry.ModSounds;
import com.darkwebduo.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Omega Terminal: a golden altar with a card slot. Right-click with an Omega Keycard in the inventory to start the
 * summoning cutscene of the Omega Root. All sequencing lives in {@link OmegaTerminalBlockEntity}.
 */
public class OmegaTerminalBlock extends BaseEntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final VoxelShape SHAPE = Block.box(0.5D, 0.0D, 0.5D, 15.5D, 15.0D, 15.5D);

    public OmegaTerminalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
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
        return new OmegaTerminalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.OMEGA_TERMINAL.get(), OmegaTerminalBlockEntity::tick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer)) return InteractionResult.CONSUME;
        ServerLevel sl = (ServerLevel) level;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof OmegaTerminalBlockEntity)) return InteractionResult.CONSUME;
        OmegaTerminalBlockEntity term = (OmegaTerminalBlockEntity) be;
        Vec3 c = Vec3.atCenterOf(pos).add(0, 0.5, 0);

        if (term.isBusy()) {
            player.displayClientMessage(Component.translatable("message.darkweb_duo.terminal_busy"), true);
            return InteractionResult.CONSUME;
        }
        ItemStack card = KeycardItem.find(player, 2);
        if (card.isEmpty()) {
            Fx.sound(sl, c, ModSounds.DOOR_DENIED.get(), SoundSource.BLOCKS, 1.0F, 0.8F);
            Fx.send(sl, Fx.dust(0xFF2030, 1.2F), c.x, c.y + 0.6, c.z, 14, 0.35, 0.3, 0.35, 0);
            Fx.send(sl, ParticleTypes.SMOKE, c.x, c.y + 0.5, c.z, 6, 0.3, 0.2, 0.3, 0.01);
            player.displayClientMessage(Component.translatable("message.darkweb_duo.terminal_need_card"), true);
            return InteractionResult.CONSUME;
        }
        if (level.getBlockState(pos.above()).is(ModBlocks.IPHONE_DUO.get())) {
            player.displayClientMessage(Component.translatable("message.darkweb_duo.terminal_spent"), true);
            return InteractionResult.CONSUME;
        }
        if (!term.hasClearance()) {
            Fx.sound(sl, c, ModSounds.DOOR_DENIED.get(), SoundSource.BLOCKS, 1.0F, 1.2F);
            player.displayClientMessage(Component.translatable("message.darkweb_duo.terminal_blocked"), true);
            return InteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) card.shrink(1);
        term.activate((ServerPlayer) player);
        return InteractionResult.CONSUME;
    }
}

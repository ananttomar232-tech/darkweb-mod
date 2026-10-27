package com.darkwebduo.block;

import com.darkwebduo.item.KeycardItem;
import com.darkwebduo.registry.ModBlocks;
import com.darkwebduo.registry.ModSounds;
import com.darkwebduo.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A vault door that ignores hands, redstone and everything else: it only opens for a player carrying the
 * Dark Web Keycard (tier 1). It slams shut again after 5 seconds.
 */
public class KeycardDoorBlock extends DoorBlock {
    public static final int AUTO_CLOSE_TICKS = 100;

    /** Custom door "wood type": cannot be opened by hand, sounds of an iron vault. */
    public static final BlockSetType KEYCARD_TYPE = BlockSetType.register(new BlockSetType(
            "darkweb_keycard", false, SoundType.NETHERITE_BLOCK,
            SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN,
            SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
            SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON));

    public KeycardDoorBlock(Properties properties) {
        super(properties, KEYCARD_TYPE);
    }

    /** Places a closed, correctly oriented 2-high keycard door (used by the arena builders). */
    public static void place(LevelAccessor level, BlockPos lower, Direction facing) {
        BlockState base = ModBlocks.KEYCARD_DOOR.get().defaultBlockState()
                .setValue(FACING, facing)
                .setValue(OPEN, Boolean.FALSE)
                .setValue(POWERED, Boolean.FALSE)
                .setValue(HINGE, DoorHingeSide.LEFT);
        level.setBlock(lower, base.setValue(HALF, DoubleBlockHalf.LOWER), 2 | 16);
        level.setBlock(lower.above(), base.setValue(HALF, DoubleBlockHalf.UPPER), 2 | 16);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockState lowerState = level.getBlockState(lower);
        if (!(lowerState.getBlock() instanceof KeycardDoorBlock)) {
            return InteractionResult.PASS;
        }
        boolean isOpen = lowerState.getValue(OPEN);
        Vec3 c = Vec3.atCenterOf(lower).add(0, 0.5, 0);

        // Players can always step back and close it again, but only a card holder can open it.
        if (!isOpen && !KeycardItem.has(player, 1)) {
            if (!level.isClientSide && level instanceof ServerLevel) {
                ServerLevel sl = (ServerLevel) level;
                Fx.sound(sl, c, ModSounds.DOOR_DENIED.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                Fx.send(sl, Fx.dust(0xFF2030, 1.2F), c.x, c.y, c.z, 12, 0.35, 0.6, 0.35, 0);
                Fx.send(sl, ParticleTypes.SMOKE, c.x, c.y, c.z, 6, 0.3, 0.5, 0.3, 0.01);
                player.displayClientMessage(Component.translatable("message.darkweb_duo.door_denied"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide && level instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel) level;
            boolean nowOpen = !isOpen;
            if (nowOpen) {
                Fx.sound(sl, c, ModSounds.DOOR_UNLOCK.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                Fx.send(sl, Fx.dust(0x19E6FF, 1.2F), c.x, c.y, c.z, 16, 0.4, 0.7, 0.4, 0);
                Fx.send(sl, ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 10, 0.4, 0.7, 0.4, 0.3);
                player.displayClientMessage(Component.translatable("message.darkweb_duo.door_granted"), true);
                sl.scheduleTick(lower, this, AUTO_CLOSE_TICKS);
            }
            this.setOpen(player, level, lowerState, lower, nowOpen);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER && state.getValue(OPEN)) {
            this.setOpen(null, level, state, pos, false);
        }
    }

    /** Redstone cannot open this door. */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        // intentionally empty
    }
}

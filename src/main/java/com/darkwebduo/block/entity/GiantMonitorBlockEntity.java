package com.darkwebduo.block.entity;

import com.darkwebduo.Config;
import com.darkwebduo.block.GiantKeyboardBlock;
import com.darkwebduo.block.GiantMonitorBlock;
import com.darkwebduo.block.GiantMouseBlock;
import com.darkwebduo.registry.ModBlockEntities;
import com.darkwebduo.registry.ModItems;
import com.darkwebduo.registry.ModSounds;
import com.darkwebduo.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * State of the giant monitor (lives only on the controller block): power, boot time, input devices found nearby,
 * keystroke / click counters that drive the on-screen terminal, and the order-a-delivery-box feature.
 */
public class GiantMonitorBlockEntity extends BlockEntity {
    public static final int DEVICE_RANGE = 8;
    private static final int ORDER_COOLDOWN = 60;

    private boolean powered;
    private long bootStart;
    private boolean hasKeyboard;
    private boolean hasMouse;
    private int keyPresses;
    private int mouseClicks;
    private long lastKeyTime = -1000;
    private long lastClickTime = -1000;
    private long lastOrderTime = -1000;

    public GiantMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GIANT_MONITOR.get(), pos, state);
    }

    // ---- getters for the renderer
    public boolean isPowered() { return powered; }
    public long getBootStart() { return bootStart; }
    public boolean hasKeyboard() { return hasKeyboard; }
    public boolean hasMouse() { return hasMouse; }
    public int getKeyPresses() { return keyPresses; }
    public int getMouseClicks() { return mouseClicks; }
    public long getLastKeyTime() { return lastKeyTime; }
    public long getLastClickTime() { return lastClickTime; }
    public long getLastOrderTime() { return lastOrderTime; }

    public Direction facing() {
        return getBlockState().getValue(GiantMonitorBlock.FACING);
    }

    // ---- server logic
    public static void tick(Level level, BlockPos pos, BlockState state, GiantMonitorBlockEntity be) {
        if (level.isClientSide || level.getGameTime() % 20L != 0L) return;
        boolean k = false;
        boolean m = false;
        int r = DEVICE_RANGE;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -2, -r), pos.offset(r, 2, r))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof GiantKeyboardBlock) k = true;
            else if (s.getBlock() instanceof GiantMouseBlock) m = true;
            if (k && m) break;
        }
        if (k != be.hasKeyboard || m != be.hasMouse) {
            be.hasKeyboard = k;
            be.hasMouse = m;
            be.setChanged();
            be.sync();
        }
    }

    /** Called by the keyboard block (server). */
    public void keyPress() {
        if (level == null) return;
        keyPresses++;
        lastKeyTime = level.getGameTime();
        setChanged();
        sync();
    }

    /** Called by the mouse block (server). */
    public void mouseClick() {
        if (level == null) return;
        mouseClicks++;
        lastClickTime = level.getGameTime();
        setChanged();
        sync();
    }

    public void interact(Player player) {
        if (level == null || !(level instanceof ServerLevel)) return;
        ServerLevel sl = (ServerLevel) level;
        Vec3 c = Vec3.atCenterOf(worldPosition).add(0, 1.0, 0);

        if (!powered) {
            powered = true;
            bootStart = sl.getGameTime();
            Fx.sound(sl, c, ModSounds.MONITOR_BOOT.get(), SoundSource.BLOCKS, 1.2F, 1.0F);
            setChanged();
            sync();
            return;
        }
        if (player.isShiftKeyDown()) {
            powered = false;
            Fx.sound(sl, c, ModSounds.MONITOR_OFF.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            setChanged();
            sync();
            return;
        }
        if (sl.getGameTime() - bootStart < 60) {
            return; // still booting
        }
        if (!(hasKeyboard && hasMouse)) {
            player.displayClientMessage(Component.translatable("message.darkweb_duo.monitor_need_devices", DEVICE_RANGE), true);
            return;
        }
        if (sl.getGameTime() - lastOrderTime < ORDER_COOLDOWN) {
            return;
        }
        int cost = Config.orderCost();
        if (!consumeEmeralds(player, cost)) {
            player.displayClientMessage(Component.translatable("message.darkweb_duo.monitor_need_emeralds", cost), true);
            return;
        }
        lastOrderTime = sl.getGameTime();
        Direction f = facing();
        Vec3 drop = Vec3.atCenterOf(worldPosition).add(f.getStepX() * 2.2, 5.0, f.getStepZ() * 2.2);
        ItemEntity box = new ItemEntity(sl, drop.x, drop.y, drop.z, new ItemStack(ModItems.DELIVERY_BOX.get()));
        box.setDeltaMovement(0, -0.15, 0);
        box.setGlowingTag(true);
        box.setUnlimitedLifetime();
        sl.addFreshEntity(box);
        Fx.sound(sl, drop, ModSounds.ORDER_CONFIRM.get(), SoundSource.BLOCKS, 1.2F, 1.0F);
        Fx.sound(sl, drop.add(0, -3, 0), ModSounds.DELIVERY_DROP.get(), SoundSource.BLOCKS, 1.2F, 1.0F);
        Fx.line(sl, ParticleTypes.END_ROD, drop, drop.add(0, -5.0, 0), 0.35);
        Fx.ring(sl, ParticleTypes.ELECTRIC_SPARK, drop.x, drop.y, drop.z, 0.7, 16, 0.12);
        player.displayClientMessage(Component.translatable("message.darkweb_duo.monitor_ordered"), true);
        setChanged();
        sync();
    }

    private static boolean consumeEmeralds(Player player, int cost) {
        if (player.isCreative() || cost <= 0) return true;
        int have = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(Items.EMERALD)) have += s.getCount();
        }
        if (have < cost) return false;
        int left = cost;
        for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(Items.EMERALD)) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        return true;
    }

    private void sync() {
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ---- persistence + sync
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Powered", powered);
        tag.putLong("Boot", bootStart);
        tag.putBoolean("Kb", hasKeyboard);
        tag.putBoolean("Mouse", hasMouse);
        tag.putInt("Keys", keyPresses);
        tag.putInt("Clicks", mouseClicks);
        tag.putLong("LastKey", lastKeyTime);
        tag.putLong("LastClick", lastClickTime);
        tag.putLong("LastOrder", lastOrderTime);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        powered = tag.getBoolean("Powered");
        bootStart = tag.getLong("Boot");
        hasKeyboard = tag.getBoolean("Kb");
        hasMouse = tag.getBoolean("Mouse");
        keyPresses = tag.getInt("Keys");
        mouseClicks = tag.getInt("Clicks");
        lastKeyTime = tag.getLong("LastKey");
        lastClickTime = tag.getLong("LastClick");
        lastOrderTime = tag.getLong("LastOrder");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The screen spans 5x3 blocks around the controller, so the renderer must not be culled with the controller. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(3.5, 0.0, 3.5).expandTowards(0, 3, 0);
    }
}

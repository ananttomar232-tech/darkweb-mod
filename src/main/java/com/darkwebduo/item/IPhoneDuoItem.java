package com.darkwebduo.item;

import com.darkwebduo.client.render.IPhoneDuoItemRenderer;
import com.darkwebduo.client.screen.PhoneScreens;
import com.darkwebduo.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * The iPhone Duo in the inventory. Right-click (in the air or on a block) uses the phone; sneak + right-click on a block
 * places it as a display piece. NBT "Color": 0 = Star White, 1 = Night Sky.
 */
public class IPhoneDuoItem extends BlockItem {
    public static final String TAG_COLOR = "Color";

    public IPhoneDuoItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static int colorOf(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getInt(TAG_COLOR) == 1 ? 1 : 0;
    }

    public static ItemStack make(int colorway) {
        ItemStack s = new ItemStack(ModItems.IPHONE_DUO.get());
        if (colorway == 1) s.getOrCreateTag().putInt(TAG_COLOR, 1);
        return s;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player p = ctx.getPlayer();
        if (p != null && !p.isShiftKeyDown()) return InteractionResult.PASS;
        return super.useOn(ctx);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            final int cw = colorOf(stack);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PhoneScreens.open(null, cw));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.darkweb_duo.iphone_duo.color." + colorOf(stack)).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("tooltip.darkweb_duo.iphone_duo.1").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.darkweb_duo.iphone_duo.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.darkweb_duo.iphone_duo.3").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private IPhoneDuoItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new IPhoneDuoItemRenderer();
                return renderer;
            }
        });
    }
}

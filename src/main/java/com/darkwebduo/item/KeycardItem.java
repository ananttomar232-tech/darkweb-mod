package com.darkwebduo.item;

import com.darkwebduo.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** tier 1 = Dark Web Keycard (opens keycard doors), tier 2 = Omega Keycard (activates the Omega Terminal). */
public class KeycardItem extends Item {
    private final int tier;

    public KeycardItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (tier == 1) {
            tooltip.add(Component.translatable("tooltip.darkweb_duo.dark_keycard.1").withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.darkweb_duo.dark_keycard.2").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.darkweb_duo.omega_keycard.1").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.darkweb_duo.omega_keycard.2").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** The keycard item of a tier. */
    public static Item itemFor(int tier) {
        return tier == 1 ? ModItems.DARK_KEYCARD.get() : ModItems.OMEGA_KEYCARD.get();
    }

    /** Finds a keycard of the given tier anywhere in the player's inventory (hands, hotbar, main, offhand). */
    public static ItemStack find(Player player, int tier) {
        Inventory inv = player.getInventory();
        Item wanted = itemFor(tier);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() == wanted) return s;
        }
        return ItemStack.EMPTY;
    }

    public static boolean has(Player player, int tier) {
        return !find(player, tier).isEmpty();
    }
}

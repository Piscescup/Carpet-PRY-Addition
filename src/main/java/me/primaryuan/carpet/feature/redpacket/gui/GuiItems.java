package me.primaryuan.carpet.feature.redpacket.gui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.jfr.stats.CpuLoadStat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.Arrays;

import static me.primaryuan.carpet.i18n.ServerI18n.tr;


public final class GuiItems {
    public static final String GUI_PREFIX = "carpetprimaryuan.redpacket.gui";

    private GuiItems() {}

    public static ItemStack icon(Item item, Component name, Component... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ITEM_NAME, name.copy());
        stack.set(DataComponents.LORE, new ItemLore(
                Arrays.stream(lore).map(line -> (Component) line.copy()).toList()));
        return stack;
    }

    public static ItemStack head(ServerPlayer player, String key) {
        ItemStack head = GuiItems.icon(
            Items.PLAYER_HEAD,
            player.getName(),
            tr(GUI_PREFIX + key)
        );
        //#if MC < 12110
        //$$ head.set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile()));
        //#else
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
        //#endif
        head.set(DataComponents.CUSTOM_NAME, player.getName().copy());
        return head;
    }
}

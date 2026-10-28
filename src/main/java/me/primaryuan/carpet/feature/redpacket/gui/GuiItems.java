package me.primaryuan.carpet.feature.redpacket.gui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
//#if MC >= 12105
import net.minecraft.world.item.component.TooltipDisplay;
//#else
//$$ import net.minecraft.util.Unit;
//#endif

//#if MC >= 260200
//$$ import net.minecraft.world.item.DyeColor;
//#endif

import java.util.Arrays;
import java.util.LinkedHashSet;

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

    /** A decorative, unnamed pane for unused read-only menu slots. */
    public static ItemStack filler() {
        ItemStack pane;
        //#if MC >= 260200
        //$$ pane = icon(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY), Component.empty());
        //#else
        pane = icon(Items.GRAY_STAINED_GLASS_PANE, Component.empty());
        //#endif
        //#if MC >= 12105
        pane.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new LinkedHashSet<>()));
        //#else
        //$$ pane.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
        //#endif
        return pane;
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

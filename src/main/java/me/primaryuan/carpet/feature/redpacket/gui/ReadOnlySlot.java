package me.primaryuan.carpet.feature.redpacket.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;


final class ReadOnlySlot extends Slot {
    ReadOnlySlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) { return false; }

    @Override
    public boolean mayPickup(Player player) { return false; }

    @Override
    public ItemStack remove(int amount) { return ItemStack.EMPTY; }

    @Override
    public void set(ItemStack stack) {}

    @Override
    public void setByPlayer(ItemStack stack) {}

    @Override
    public void setByPlayer(ItemStack stack, ItemStack previous) {}
}

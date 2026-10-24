package me.primaryuan.carpet.feature.redpacket.gui;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;


final class GuiInventory extends SimpleContainer {
    GuiInventory(int size) {
        super(checkSize(size));
    }


    List<ItemStack> snapshot() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack stack = getItem(i);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
        }
        return List.copyOf(items);
    }


    List<ItemStack> drain() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack stack = removeItemNoUpdate(i);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        setChanged();
        return List.copyOf(items);
    }

    private static int checkSize(int size) {
        if (size < 0 || size > 54) {
            throw new IllegalArgumentException("Input inventory size must be between 0 and 54");
        }
        return size;
    }
}

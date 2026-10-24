package me.primaryuan.carpet.feature.redpacket.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
//#if MC < 260102
import net.minecraft.world.inventory.ClickType;
//#else
//$$ import net.minecraft.world.inventory.ContainerInput;
//#endif
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;


public final class ChestGui extends GuiPage {
    private final int rows;
    private final int[] inputs;
    private final Predicate<ItemStack>[] filters;
    private final Consumer<GuiClick>[] buttons;
    private final SimpleContainer icons;
    private boolean opened;

    @SuppressWarnings("unchecked")
    public ChestGui(GuiSession session, int rows, Component title) {
        super(session, title);
        menuType(rows);
        this.rows = rows;
        this.inputs = new int[rows * 9];
        this.filters = (Predicate<ItemStack>[]) new Predicate<?>[inputs.length];
        this.buttons = (Consumer<GuiClick>[]) new Consumer<?>[inputs.length];
        this.icons = new SimpleContainer(inputs.length);
        Arrays.fill(inputs, -1);
    }

    public ChestGui icon(int index, ItemStack icon) {
        checkDisplaySlot(index);
        icons.setItem(index, icon.copy());
        buttons[index] = null;
        refresh();
        return this;
    }


    public ChestGui button(int index, ItemStack icon, Consumer<GuiClick> onClick) {
        checkDisplaySlot(index);
        icons.setItem(index, icon.copy());
        buttons[index] = Objects.requireNonNull(onClick, "onClick");
        refresh();
        return this;
    }

    public ChestGui input(int index, int inventoryIndex) {
        return input(index, inventoryIndex, stack -> true);
    }


    public ChestGui input(int index, int inventoryIndex, Predicate<ItemStack> accepts) {
        checkIndex(index);
        if (opened) throw new IllegalStateException("Input slots must be configured before opening the GUI");
        Objects.checkIndex(inventoryIndex, session.inventory().getContainerSize());
        Objects.requireNonNull(accepts, "accepts");
        if (inputs[index] >= 0 || Arrays.stream(inputs).anyMatch(value -> value == inventoryIndex)) {
            throw new IllegalArgumentException("Input slot is already mapped");
        }
        inputs[index] = inventoryIndex;
        filters[index] = accepts;
        icons.setItem(index, ItemStack.EMPTY);
        buttons[index] = null;
        return this;
    }

    @Override
    public boolean open() {
        opened = true;
        return super.open();
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new Menu(id, inventory);
    }

    private void checkDisplaySlot(int index) {
        checkIndex(index);
        if (inputs[index] >= 0) throw new IllegalArgumentException("Cannot replace an input slot with a GUI icon");
    }

    private void checkIndex(int index) {
        Objects.checkIndex(index, inputs.length);
    }

    private static MenuType<?> menuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            case 6 -> MenuType.GENERIC_9x6;
            default -> throw new IllegalArgumentException("Chest rows must be between 1 and 6");
        };
    }

    private final class Menu extends AbstractContainerMenu {
        private boolean removed;

        Menu(int id, Inventory inventory) {
            super(menuType(rows), id);
            for (int i = 0; i < inputs.length; i++) {
                int x = 8 + (i % 9) * 18;
                int y = 18 + (i / 9) * 18;
                if (inputs[i] < 0) {
                    addSlot(new ReadOnlySlot(icons, i, x, y));
                } else {
                    final Predicate<ItemStack> filter = filters[i];
                    addSlot(new Slot(session.inventory(), inputs[i], x, y) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return session.isCurrent(ChestGui.this) && filter.test(stack);
                        }

                        @Override
                        public boolean mayPickup(Player player) {
                            return session.isCurrent(ChestGui.this);
                        }
                    });
                }
            }
            int playerY = rows * 18 + 31;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new Slot(inventory, 9 + row * 9 + col,
                        8 + col * 18, playerY + row * 18));
                }
            }
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col, 8 + col * 18, playerY + 58));
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return player == session.player() && session.isCurrent(ChestGui.this);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
            Slot slot = slots.get(index);
            if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
            ItemStack stack = slot.getItem();
            ItemStack before = stack.copy();
            boolean moved = false;
            if (index < inputs.length) {
                moved = moveItemStackTo(stack, inputs.length, slots.size(), true);
            } else {
                // 合并已有栈的原版路径未必检查 mayPlace；只向已通过过滤的真实槽转移。
                for (int i = 0; i < inputs.length && !stack.isEmpty(); i++) {
                    if (inputs[i] >= 0 && slots.get(i).mayPlace(stack)) {
                        moved |= moveItemStackTo(stack, i, i + 1, false);
                    }
                }
            }
            if (!moved) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            slot.onTake(player, stack);
            return before;
        }

        //#if MC < 260102
        @Override
        public void clicked(int index, int button, ClickType action, Player player) {
            //#else
            //$$ @Override
            //$$ public void clicked(int index, int button, ContainerInput action, Player player) {
            //#endif
            if (!stillValid(player)) return;
            if (index >= 0 && index < inputs.length && inputs[index] < 0) {
                //#if MC < 260102
                if (action == ClickType.PICKUP && (button == 0 || button == 1)) {
                    //#else
                    //$$ if (action == ContainerInput.PICKUP && (button == 0 || button == 1)) {
                    //#endif
                    Consumer<GuiClick> callback = buttons[index];
                    if (callback != null) callback.accept(button == 0 ? GuiClick.LEFT : GuiClick.RIGHT);
                }
                if (player.containerMenu == this) sendAllDataToRemote();
                return;
            }
            if (index >= slots.size() || (index < 0 && index != -999 && index != -1)) return;
            super.clicked(index, button, action, player);
        }

        @Override
        public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
            return !(slot instanceof ReadOnlySlot) && super.canTakeItemForPickAll(stack, slot);
        }

        @Override
        public void removed(Player player) {
            if (removed) return;
            removed = true;
            try {
                super.removed(player);
            } finally {
                ChestGui.this.removed(this);
            }
        }
    }
}

package me.primaryuan.carpet.feature.redpacket.gui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
//#if MC < 260102
import net.minecraft.world.inventory.ClickType;
//#else
//$$ import net.minecraft.world.inventory.ContainerInput;
//#endif
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;


public final class TextInputGui extends GuiPage {
    private final ItemStack confirmIcon;
    private final Predicate<String> validator;
    private final Consumer<String> onSubmit;
    private String text;

    public TextInputGui(GuiSession session, Component title, String initialText,
                        ItemStack confirmIcon, Predicate<String> validator,
                        Consumer<String> onSubmit) {
        super(session, title);
        this.text = Objects.requireNonNull(initialText, "initialText");
        if (text.length() > 50) throw new IllegalArgumentException("Anvil text must not exceed 50 characters");
        this.confirmIcon = confirmIcon.copy();
        this.validator = Objects.requireNonNull(validator, "validator");
        this.onSubmit = Objects.requireNonNull(onSubmit, "onSubmit");
    }

    public String getInput() {
        return text;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new Menu(id, inventory);
    }

    private final class Menu extends AnvilMenu {
        private boolean submitted;
        private boolean removed;

        Menu(int id, Inventory inventory) {
            super(id, inventory, ContainerLevelAccess.NULL);
            for (int i = 0; i < 3; i++) {
                Slot original = slots.get(i);
                Slot readOnly = new ReadOnlySlot(i == 2 ? resultSlots : inputSlots,
                        i == 2 ? 0 : i, original.x, original.y);
                readOnly.index = i;
                slots.set(i, readOnly);
            }
            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME, Component.literal(text));
            inputSlots.setItem(0, paper);
            createResult();
        }

        @Override
        public boolean stillValid(Player player) {
            return player == session.player() && session.isCurrent(TextInputGui.this);
        }

        @Override
        public void createResult() {
            // 不调用 super：不修理、不计算经验费用、不生成改名物品。
            resultSlots.setItem(0, removed ? ItemStack.EMPTY : confirmIcon.copy());
        }

        @Override
        public boolean setItemName(String value) {
            if (value == null || value.length() > 50 || !session.isCurrent(TextInputGui.this)) return false;
            StringBuilder clean = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c >= 32 && c != 127 && c != '§') clean.append(c);
            }
            String next = clean.toString();
            if (text.equals(next)) return false;
            text = next;
            // 原版客户端会用输入槽名称更新文本框，保持它与当前输入一致，
            // 避免纠正背包预测时把用户文字重置成初始值。
            inputSlots.getItem(0).set(DataComponents.CUSTOM_NAME, Component.literal(text));
            inputSlots.setChanged();
            createResult();
            broadcastChanges();
            // 客户端本地可能算出改名费用；即便服务端费用一直为 0 也显式纠正。
            if (session.player().connection != null) {
                session.player().connection.send(new ClientboundContainerSetDataPacket(containerId, 0, 0));
            }
            return true;
        }

        @Override
        protected boolean mayPickup(Player player, boolean hasItems) {
            return false;
        }

        @Override
        protected void onTake(Player player, ItemStack stack) {}

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        //#if MC < 260102
        @Override
        public void clicked(int index, int button, ClickType action, Player player) {
            if (action == ClickType.PICKUP && index == 2 && button == 0
        //#else
        //$$ @Override
        //$$ public void clicked(int index, int button, ContainerInput action, Player player) {
        //$$     if (action == ContainerInput.PICKUP && index == 2 && button == 0
        //#endif
                    && stillValid(player) && !submitted && validator.test(text)) {
                submitted = true;
                try {
                    onSubmit.accept(text);
                } catch (RuntimeException | Error error) {
                    submitted = false;
                    throw error;
                }
            }
            if (player.containerMenu == this) sendAllDataToRemote();
        }

        @Override
        public void removed(Player player) {
            if (removed) return;
            removed = true;
            inputSlots.clearContent();
            resultSlots.clearContent();
            try {
                super.removed(player);
            } finally {
                TextInputGui.this.removed(this);
            }
        }
    }
}

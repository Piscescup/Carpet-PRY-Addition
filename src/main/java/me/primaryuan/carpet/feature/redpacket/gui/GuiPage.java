package me.primaryuan.carpet.feature.redpacket.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Objects;


public abstract class GuiPage {
    protected final GuiSession session;
    private final Component title;
    private AbstractContainerMenu menu;

    protected GuiPage(GuiSession session, Component title) {
        this.session = Objects.requireNonNull(session, "session");
        this.title = Objects.requireNonNull(title, "title").copy();
    }

    public boolean open() {
        return session.open(this, () -> session.player().openMenu(new SimpleMenuProvider(
            (id, inventory, player) -> {
                menu = createMenu(id, inventory);
                return menu;
            }, title)).isPresent() && isOpen());
    }

    public boolean isOpen() {
        return menu != null && session.player().containerMenu == menu;
    }

    public void close() {
        if (isOpen()) {
            session.player().closeContainer();
        }
    }

    protected abstract AbstractContainerMenu createMenu(int id, Inventory inventory);


    final void removed(AbstractContainerMenu closedMenu) {
        if (menu == closedMenu) {
            menu = null;
            session.pageClosed(this);
        }
    }

    protected final void refresh() {
        if (isOpen()) menu.broadcastChanges();
    }
}

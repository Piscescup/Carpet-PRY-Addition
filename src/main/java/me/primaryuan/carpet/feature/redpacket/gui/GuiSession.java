package me.primaryuan.carpet.feature.redpacket.gui;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;


public final class GuiSession implements AutoCloseable {
    private static final Map<ServerPlayer, GuiSession> ACTIVE = new IdentityHashMap<>();
    private static boolean registered;

    private final ServerPlayer player;
    private final GuiInventory inventory;
    private GuiPage current;
    private boolean finished;

    public GuiSession(ServerPlayer player, int inputSlots) {
        this.player = Objects.requireNonNull(player, "player");
        this.inventory = new GuiInventory(inputSlots);
    }

    public ServerPlayer player() {
        return player;
    }

    public boolean isFinished() {
        return finished;
    }

    /**
     * Close a container session before switching to a screen without an inventory menu.
     */
    static void cancelActive(ServerPlayer player) {
        GuiSession active = ACTIVE.get(player);
        if (active != null) active.cancel();
    }

    public List<ItemStack> contents() {
        return inventory.snapshot();
    }

    GuiInventory inventory() {
        return inventory;
    }

    boolean isCurrent(GuiPage page) {
        return !finished && current == page;
    }

    boolean open(GuiPage page, BooleanSupplier openPage) {
        ensureActive();
        if (page == current && page.isOpen()) {
            return true;
        }
        registerEvents();
        GuiSession previousSession = ACTIVE.get(player);
        if (previousSession != null && previousSession != this) {
            previousSession.cancel();
        }
        GuiPage previousPage = current;
        current = page;
        ACTIVE.put(player, this);
        try {
            if (openPage.getAsBoolean()) {
                return true;
            }
        } catch (RuntimeException | Error error) {
            closePrevious(previousPage);
            cancel();
            throw error;
        }
        closePrevious(previousPage);
        cancel();
        return false;
    }

    private void closePrevious(GuiPage page) {
        if (page != null && page != current && page.isOpen()) {
            page.close();
        }
    }

    void pageClosed(GuiPage page) {
        if (isCurrent(page)) {
            cancel();
        }
    }

    public List<ItemStack> takeItems() {
        ensureActive();
        finished = true;
        ACTIVE.remove(player, this);
        try {
            closePage();
        } catch (RuntimeException | Error error) {
            returnItems(inventory.drain());
            throw error;
        }
        return inventory.drain();
    }

    public void clearItems() {
        ensureActive();
        returnItems(inventory.drain());
    }

    public void cancel() {
        if (finished) {
            return;
        }
        finished = true;
        ACTIVE.remove(player, this);
        try {
            closePage();
        } finally {
            returnItems(inventory.drain());
        }
    }

    @Override
    public void close() {
        cancel();
    }

    private void closePage() {
        GuiPage page = current;
        current = null;
        if (page != null && page.isOpen()) {
            page.close();
        }
    }

    private void returnItems(List<ItemStack> items) {
        for (ItemStack stack : items) {
            //#if MC >= 260300
            //$$ player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.PREDICTED);
            //#else
            player.getInventory().placeItemBackInInventory(stack);
            //#endif
        }
    }

    private void ensureActive() {
        if (finished) {
            throw new IllegalStateException("GUI session has already finished");
        }
    }

    private static void registerEvents() {
        if (registered) {
            return;
        }
        registered = true;
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            GuiSession session = ACTIVE.get(handler.player);
            if (session != null) {
                session.cancel();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (GuiSession session : new ArrayList<>(ACTIVE.values())) {
                if (session.player.level().getServer() == server) {
                    session.cancel();
                }
            }
        });
        // 兜底处理其他模组替换菜单或绕过正常关闭流程的情况。
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (GuiSession session : new ArrayList<>(ACTIVE.values())) {
                if (session.player.level().getServer() == server
                    && (!session.player.isAlive() || session.player.hasDisconnected()
                    || session.current == null || !session.current.isOpen())) {
                    session.cancel();
                }
            }
        });
    }
}

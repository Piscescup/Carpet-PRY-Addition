package me.primaryuan.carpet.feature.redpacket.gui;

import me.primaryuan.carpet.feature.redpacket.RedPacketManager;
import me.primaryuan.carpet.feature.redpacket.RedPacketManager.PendingReward;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Only display copies live in this menu; the manager retains the real rewards.
 */
public final class RedPacketInboxGui {
    private static final String PREFIX = "carpetprimaryuan.redpacket.inbox_gui.";
    private static final int PAGE_SIZE = RewardLayout.PAGE_SIZE;
    private final GuiSession session;
    private final ChestGui gui;
    private final RedPacketManager manager;
    private List<PendingReward> rendered = List.of();
    private UUID selectedReward;
    private int page;
    private int refreshTicks;
    private boolean watching;
    private boolean receiving;

    public RedPacketInboxGui(ServerPlayer player) throws IOException {
        manager = RedPacketManager.get(player.level().getServer());
        session = new GuiSession(player, 0);
        gui = new ChestGui(session, 6, tr("title"));
        render(true);
    }

    public boolean open() {
        if (!gui.open()) return false;
        if (!watching) {
            watching = true;
            ServerTickScheduler.register(server -> {
                if (session.isFinished()) return false;
                if (session.player().level().getServer() == server && ++refreshTicks >= 20) {
                    refreshTicks = 0;
                    render(false);
                }
                return true;
            });
        }
        return true;
    }

    private void render(boolean force) {
        if (session.isFinished()) return;
        List<PendingReward> pending = manager.pendingRewards(session.player().getUUID());
        if (!force && sameRewards(pending, rendered)) return;
        rendered = pending;
        if (pending.stream().noneMatch(reward -> reward.id().equals(selectedReward))) selectedReward = null;
        List<Entry> entries = new ArrayList<>();
        for (PendingReward reward : pending) {
            for (ItemStack stack : reward.items()) entries.add(new Entry(reward, stack));
        }
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.clamp(page, 0, pages - 1);
        for (int slot = 0; slot < 54; slot++) gui.icon(slot, GuiItems.filler());
        gui.icon(4, GuiItems.icon(Items.NETHER_STAR, tr("title"), tr("summary", pending.size()),
            tr("page", page + 1, pages), tr("help")));
        int offset = page * PAGE_SIZE;
        int[] slots = RewardLayout.slots(Math.min(PAGE_SIZE, entries.size() - offset));
        for (int index = 0; index < slots.length; index++) {
            Entry entry = entries.get(offset + index);
            ItemStack icon = entry.stack.copy();
            boolean selected = entry.reward.id().equals(selectedReward);
            List<Component> lore = new ArrayList<>(icon.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
            if (selected) {
                icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                lore.add(tr("selected").copy().withStyle(ChatFormatting.GOLD));
            }
            lore.add(tr(entry.reward.refund() ? "refund" : "reward"));
            lore.add(tr("amount", icon.getCount()));
            lore.add(tr("collect"));
            icon.set(DataComponents.LORE, new ItemLore(lore));
            gui.button(slots[index], icon, click -> {
                selectedReward = entry.reward.id();
                render(true);
            });
        }
        if (entries.isEmpty()) gui.icon(22, GuiItems.icon(Items.PAPER, tr("empty"), tr("help")));
        if (page > 0) gui.button(45, GuiItems.icon(Items.ARROW, tr("previous")), click -> {
            page--;
            render(true);
        });
        if (selectedReward == null) {
            gui.icon(47, GuiItems.icon(Items.EMERALD,
                tr("receive_now").copy().withStyle(ChatFormatting.GRAY), tr("select_first")));
        } else {
            gui.button(47, GuiItems.icon(Items.EMERALD,
                tr("receive_now").copy().withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                tr("receive_now_help")), click -> receiveNow());
        }
        gui.button(49, GuiItems.icon(Items.PAPER, tr("refresh"), tr("summary", pending.size()),
            tr("page", page + 1, pages), tr("help"), tr("space")), click -> render(true));
        gui.button(51, GuiItems.icon(Items.CLOCK,
            tr("receive_later").copy().withStyle(ChatFormatting.YELLOW), tr("receive_later_help")),
            click -> session.cancel());
        if (page + 1 < pages) gui.button(53, GuiItems.icon(Items.ARROW, tr("next")), click -> {
            page++;
            render(true);
        });
    }

    private void receiveNow() {
        if (receiving || selectedReward == null || session.isFinished()) return;
        receiving = true;
        try {
            RedPacketManager.collect(session.player(), selectedReward);
            render(true);
        } finally {
            receiving = false;
        }
    }

    private static boolean sameRewards(List<PendingReward> left, List<PendingReward> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            PendingReward a = left.get(i);
            PendingReward b = right.get(i);
            if (!a.id().equals(b.id()) || a.refund() != b.refund()) return false;
            List<ItemStack> aItems = a.items();
            List<ItemStack> bItems = b.items();
            if (aItems.size() != bItems.size()) return false;
            for (int j = 0; j < aItems.size(); j++) {
                if (aItems.get(j).getCount() != bItems.get(j).getCount()
                    || !ItemStack.isSameItemSameComponents(aItems.get(j), bItems.get(j))) return false;
            }
        }
        return true;
    }

    private record Entry(PendingReward reward, ItemStack stack) {
    }

    private static Component tr(String key, Object... args) {
        return ServerI18n.tr(PREFIX + key, args);
    }
}

package me.primaryuan.carpet.feature.redpacket.gui;

import me.primaryuan.carpet.feature.redpacket.RedPacket;
import me.primaryuan.carpet.feature.redpacket.gui.RedPacketResultsData.Recipient;
import me.primaryuan.carpet.feature.redpacket.gui.RedPacketResultsData.Reward;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


final class LegacyRedPacketResultsGui {
    private static final int ROWS_PER_PAGE = 5;
    private static final int REWARDS_PER_ROW = 7;
    private static final String PREFIX = "carpetprimaryuan.redpacket.results_gui.";

    private final RedPacket packet;
    private final GuiSession session;
    private final ChestGui gui;
    private int page;
    private int renderedClaimed = -1;
    private boolean renderedExpired;
    private int refreshTicks;
    private boolean watching;

    LegacyRedPacketResultsGui(ServerPlayer player, RedPacket packet) {
        this.packet = Objects.requireNonNull(packet, "packet");
        this.session = new GuiSession(player, 0);
        this.gui = new ChestGui(session, 6, tr("title", packet.senderName()));
        render(true);
    }

    boolean open() {
        if (!gui.open()) return false;
        if (!watching) {
            watching = true;
            ServerTickScheduler.register(server -> {
                if (session.isFinished()) return false;
                if (session.player().level().getServer() == server && ++refreshTicks >= 10) {
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
        int claimed = RedPacketResultsData.claimed(packet);
        boolean expired = packet.expired();
        if (!force && claimed == renderedClaimed && expired == renderedExpired) return;
        renderedClaimed = claimed;
        renderedExpired = expired;

        List<ResultRow> rows = new ArrayList<>();
        for (Recipient recipient : RedPacketResultsData.recipients(packet)) {
            if (recipient.rewards().isEmpty()) rows.add(new ResultRow(recipient, 0));
            else for (int offset = 0; offset < recipient.rewards().size(); offset += REWARDS_PER_ROW) {
                rows.add(new ResultRow(recipient, offset));
            }
        }
        int pages = Math.max(1, (rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.clamp(page, 0, pages - 1);
        for (int slot = 0; slot < 54; slot++) gui.icon(slot, GuiItems.filler());
        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int index = page * ROWS_PER_PAGE + row;
            if (index >= rows.size()) break;
            renderRow(row, rows.get(index));
        }
        if (rows.isEmpty()) gui.icon(22, GuiItems.icon(Items.PAPER, tr("empty")));

        if (page > 0) gui.button(45, GuiItems.icon(Items.ARROW, claimTr("previous")), click -> {
            page--;
            render(true);
        });
        gui.button(47, GuiItems.icon(Items.BARRIER, claimTr("close")), click -> session.cancel());
        gui.icon(49, GuiItems.icon(Items.PAPER,
            tr("progress", packet.senderName(), claimed, packet.redPacketAmount()),
            Component.literal(packet.congratulations()), claimTr("page", page + 1, pages), tr("read_only")));
        gui.button(51, GuiItems.icon(Items.CLOCK, claimTr("refresh")), click -> render(true));
        if (page + 1 < pages) gui.button(53, GuiItems.icon(Items.ARROW, claimTr("next")), click -> {
            page++;
            render(true);
        });
    }

    private void renderRow(int row, ResultRow entry) {
        Recipient recipient = entry.recipient();
        ItemStack head = GuiItems.icon(Items.PLAYER_HEAD,
            Component.literal(recipient.name()).withStyle(ChatFormatting.GOLD),
            claimTr("share", recipient.share() + 1),
            tr(entry.offset() > 0 ? "continued" : "read_only"));
        ServerPlayer online = session.player().level().getServer().getPlayerList().getPlayer(recipient.id());
        if (online != null) {
            //#if MC < 12110
            //$$ head.set(DataComponents.PROFILE, new ResolvableProfile(online.getGameProfile()));
            //#else
            head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(online.getGameProfile()));
            //#endif
        }
        gui.icon(row * 9, head);
        if (recipient.rewards().isEmpty()) {
            gui.icon(row * 9 + 2, GuiItems.icon(Items.PAPER, tr("unknown", recipient.name()), tr("unknown_items")));
            return;
        }
        for (int column = 0; column < REWARDS_PER_ROW; column++) {
            int index = entry.offset() + column;
            if (index >= recipient.rewards().size()) break;
            Reward reward = recipient.rewards().get(index);
            ItemStack icon = reward.item().copyWithCount(Math.min(reward.count(), reward.item().getMaxStackSize()));
            icon.remove(DataComponents.CUSTOM_NAME);
            icon.set(DataComponents.ITEM_NAME,
                reward.item().getHoverName().copy().append(" × " + reward.count()).withStyle(ChatFormatting.GOLD));
            icon.set(DataComponents.LORE, new ItemLore(List.of(
                claimTr("taken_by", recipient.name()), claimTr("share", recipient.share() + 1), tr("read_only"))));
            gui.icon(row * 9 + 2 + column, icon);
        }
    }

    private record ResultRow(Recipient recipient, int offset) {}

    private static Component tr(String key, Object... args) {
        return ServerI18n.tr(PREFIX + key, args);
    }

    private static Component claimTr(String key, Object... args) {
        return ServerI18n.tr("carpetprimaryuan.redpacket.claim_gui." + key, args);
    }
}

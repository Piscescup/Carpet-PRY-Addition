package me.primaryuan.carpet.feature.redpacket.gui;

import me.primaryuan.carpet.feature.redpacket.RedPacket;
import me.primaryuan.carpet.feature.redpacket.RedPacket.ClaimResult;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

//#if MC >= 260200
//$$ import net.minecraft.world.item.DyeColor;
//#endif

import java.util.Objects;
import java.util.List;
import java.util.UUID;


public final class RedPacketClaimGui {
    private static final String PREFIX = "carpetprimaryuan.redpacket.claim_gui.";
    private static final int PAGE_SIZE = 45;

    private final GuiSession session;
    private final ChestGui gui;
    private final RedPacket packet;
    private final boolean passwordVerified;
    private int renderedClaimedCount = -1;
    private boolean renderedExpired;
    private int renderedPage = -1;
    private int page;
    private int refreshTicks;
    private boolean watching;
    private boolean claiming;

    public RedPacketClaimGui(ServerPlayer player, RedPacket packet) {
        this(player, packet, false);
    }

    public RedPacketClaimGui(ServerPlayer player, RedPacket packet, boolean passwordVerified) {
        this.passwordVerified = passwordVerified;
        this.packet = Objects.requireNonNull(packet, "packet");
        this.session = new GuiSession(player, 0);
        this.gui = new ChestGui(session, 6, tr("title"));
        render(true);
    }

    public boolean open() {
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

    public void close() {
        session.cancel();
    }

    private void render(boolean force) {
        if (session.isFinished()) return;
        int total = packet.redPacketAmount();
        int claimedCount = 0;
        for (int i = 0; i < total; i++) {
            if (packet.claimedBy(i) != null) claimedCount++;
        }
        boolean expired = packet.expired();
        int pages = (total + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.clamp(page, 0, pages - 1);
        if (!force && page == renderedPage && claimedCount == renderedClaimedCount
            && expired == renderedExpired) return;
        renderedClaimedCount = claimedCount;
        renderedExpired = expired;
        renderedPage = page;
        for (int slot = PAGE_SIZE; slot < 54; slot++) gui.icon(slot, GuiItems.filler());
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int shareIndex = page * PAGE_SIZE + slot;
            if (shareIndex >= total) {
                gui.icon(slot, GuiItems.filler());
                continue;
            }
            UUID claimantId = packet.claimedBy(shareIndex);
            boolean available = claimantId == null;

            Component status = !available ? tr("taken_by", packet.claimantName(shareIndex))
                : tr(expired ? "expired" : "click");

            ItemStack icon = GuiItems.icon(
                available ? redShulkerBox() : Items.PLAYER_HEAD,
                tr("share", shareIndex + 1)
                    .copy()
                    .withStyle(
                        available && !expired ?
                            ChatFormatting.RED :
                            ChatFormatting.GRAY
                    ),
                Component.literal(packet.congratulations()),
                status
            );
            if (!available) {
                ServerPlayer claimant = session.player()
                    .level()
                    .getServer()
                    .getPlayerList()
                    .getPlayer(claimantId);

                if (claimant != null) {
                    //#if MC < 12110
                    //$$ icon.set(DataComponents.PROFILE,
                    //$$     new ResolvableProfile(claimant.getGameProfile()));
                    //#else
                    icon.set(DataComponents.PROFILE,
                        ResolvableProfile.createResolved(claimant.getGameProfile()));
                    //#endif
                }
            }

            if (available && !expired) {
                gui.button(slot, icon, click -> claim(shareIndex));
            } else {
                gui.icon(slot, icon);
            }
        }
        if (page > 0) gui.button(45, icon(Items.ARROW, "previous"), click -> {
            page--;
            render(true);
        });
        gui.button(47, icon(Items.BARRIER, "close"), click -> close());
        gui.icon(49, GuiItems.icon(Items.PAPER, tr("summary", packet.senderName()),
            Component.literal(packet.congratulations()), tr("remaining", total - claimedCount, total),
            tr("page", page + 1, pages), tr("one_per_player")));
        gui.button(51, icon(Items.CLOCK, "refresh"), click -> render(true));
        gui.button(52, icon(Items.BOOK, "results"), click ->
            new RedPacketResultsGui(session.player(), packet).open());
        if (page + 1 < pages) gui.button(53, icon(Items.ARROW, "next"), click -> {
            page++;
            render(true);
        });
    }

    private void claim(int index) {
        if (claiming || session.isFinished()) return;
        // 不信任屏幕上的旧状态；另一位玩家可能刚刚领走同一份。
        ClaimResult result;
        List<ItemStack> preview = packet.shareItems();
        if (packet.expired()) result = ClaimResult.EXPIRED;
        else if (index < 0 || index >= packet.redPacketAmount() || packet.claimedBy(index) != null) {
            result = ClaimResult.TAKEN;
        } else {
            claiming = true;
            try {
                result = Objects.requireNonNull(packet.claim(session.player(), index, passwordVerified), "claim result");
            } finally {
                claiming = false;
            }
        }
        String key = switch (result) {
            case CLAIMED -> "success";
            case ALREADY_CLAIMED -> "already_claimed";
            case TAKEN -> "taken";
            case EXPIRED -> "expired";
            case DENIED -> "denied";
            case STORAGE_ERROR -> "storage_error";
        };
        session.player().sendSystemMessage(tr(key).copy().withStyle(
            result == ClaimResult.CLAIMED ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (result == ClaimResult.CLAIMED) {
            // 奖励已经保存；动画仅展示图标，关闭或断线不会影响待领取物品。
            new RedPacketSpinGui(session.player(), packet, index, preview).open();
        } else {
            render(true);
        }
    }

    private static Item redShulkerBox() {
        //#if MC >= 260200
        //$$ return Items.DYED_SHULKER_BOX.pick(DyeColor.RED);
        //#else
        return Items.RED_SHULKER_BOX;
        //#endif
    }

    private static ItemStack icon(Item item, String key) {
        return GuiItems.icon(item, tr(key));
    }

    private static Component tr(String key, Object... args) {
        return ServerI18n.tr(PREFIX + key, args);
    }

}

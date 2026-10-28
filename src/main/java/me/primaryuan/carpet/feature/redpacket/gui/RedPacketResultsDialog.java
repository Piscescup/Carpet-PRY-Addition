package me.primaryuan.carpet.feature.redpacket.gui;

//#if MC >= 12108

import me.primaryuan.carpet.feature.redpacket.RedPacket;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
//#if MC >= 260102
//$$ import net.minecraft.world.item.ItemStackTemplate;
//#endif

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


final class RedPacketResultsDialog {
    private static final String PREFIX = "carpetprimaryuan.redpacket.results_gui.";
    private static final int ROWS_PER_PAGE = 64;
    private static final int ROW_WIDTH = 300;
    private static final int ICON_SIZE = 20;

    private RedPacketResultsDialog() {
    }

    static boolean open(ServerPlayer player, RedPacket packet) {
        return open(player, packet, 0);
    }

    static boolean open(ServerPlayer player, RedPacket packet, int requestedPage) {
        List<Row> rows = rows(packet);
        int pages = Math.max(1, (rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        List<DialogBody> body = new ArrayList<>();
        body.add(new PlainMessage(tr("progress", packet.senderName(),
            RedPacketResultsData.claimed(packet), packet.redPacketAmount()).copy()
            .withStyle(ChatFormatting.GRAY), ROW_WIDTH));
        body.add(new PlainMessage(tr("columns").copy().withStyle(ChatFormatting.GRAY), ROW_WIDTH));
        if (pages > 1) body.add(new PlainMessage(claimTr("page", page + 1, pages), ROW_WIDTH));
        if (rows.isEmpty()) body.add(new PlainMessage(tr("empty"), ROW_WIDTH));

        UUID previousRecipient = null;
        int end = Math.min(rows.size(), (page + 1) * ROWS_PER_PAGE);
        for (int index = page * ROWS_PER_PAGE; index < end; index++) {
            Row row = rows.get(index);
            RedPacketResultsData.Recipient recipient = row.recipient();
            if (!recipient.id().equals(previousRecipient)) {
                body.add(new PlainMessage(Component.literal(recipient.name())
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                    .append(claimTr("share", recipient.share() + 1).copy()
                        .withStyle(ChatFormatting.GRAY)), ROW_WIDTH));
                previousRecipient = recipient.id();
            }
            if (row.reward() == null) {
                body.add(new PlainMessage(tr("unknown_items").copy()
                    .withStyle(ChatFormatting.GRAY), ROW_WIDTH));
                continue;
            }
            RedPacketResultsData.Reward reward = row.reward();
            ItemStack icon = reward.item().copyWithCount(1);
            Component label = reward.item().getHoverName().copy().withStyle(ChatFormatting.WHITE)
                .append(Component.literal("  × " + reward.count()).withStyle(ChatFormatting.GREEN));
            Optional<PlainMessage> description = Optional.of(new PlainMessage(label,
                ROW_WIDTH - ICON_SIZE - 2));
            //#if MC >= 260102
            //$$ body.add(new ItemBody(ItemStackTemplate.fromNonEmptyStack(icon), description,
            //$$     false, true, ICON_SIZE, ICON_SIZE));
            //#else
            body.add(new ItemBody(icon, description, false, true, ICON_SIZE, ICON_SIZE));
            //#endif
        }

        List<ActionButton> actions = new ArrayList<>();
        if (page > 0) actions.add(commandButton(claimTr("previous"), pageCommand(packet, page - 1)));
        if (page + 1 < pages) actions.add(commandButton(claimTr("next"), pageCommand(packet, page + 1)));
        actions.add(commandButton(claimTr("refresh"), pageCommand(packet, page)));
        actions.add(commandButton(claimTr("inbox"), "/redpacket inbox"));
        ActionButton close = new ActionButton(new CommonButtonData(claimTr("close"), 150), Optional.empty());
        CommonDialogData common = new CommonDialogData(tr("title", packet.senderName()),
            Optional.empty(), true, false, DialogAction.CLOSE, body, List.of());
        player.openDialog(Holder.direct(new MultiActionDialog(common, actions, Optional.of(close), 2)));
        return true;
    }

    private static List<Row> rows(RedPacket packet) {
        List<Row> rows = new ArrayList<>();
        for (RedPacketResultsData.Recipient recipient : RedPacketResultsData.recipients(packet)) {
            if (recipient.rewards().isEmpty()) rows.add(new Row(recipient, null));
            else for (RedPacketResultsData.Reward reward : recipient.rewards()) rows.add(new Row(recipient, reward));
        }
        return rows;
    }

    private static String pageCommand(RedPacket packet, int page) {
        return "/redpacket results page " + (page + 1) + " " + packet.id();
    }

    private static ActionButton commandButton(Component label, String command) {
        return new ActionButton(new CommonButtonData(label, 150),
            Optional.of(new StaticAction(new ClickEvent.RunCommand(command))));
    }

    private record Row(RedPacketResultsData.Recipient recipient, RedPacketResultsData.Reward reward) {
    }

    private static Component tr(String key, Object... args) {
        return ServerI18n.tr(PREFIX + key, args);
    }

    private static Component claimTr(String key, Object... args) {
        return ServerI18n.tr("carpetprimaryuan.redpacket.claim_gui." + key, args);
    }
}
//#endif

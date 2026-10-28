package me.primaryuan.carpet.feature.redpacket.gui;

import me.primaryuan.carpet.feature.redpacket.RedPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;


public final class RedPacketResultsGui {
    private final ServerPlayer player;
    private final RedPacket packet;
    private final int page;

    public RedPacketResultsGui(ServerPlayer player, RedPacket packet) {
        this(player, packet, 0);
    }

    public RedPacketResultsGui(ServerPlayer player, RedPacket packet, int page) {
        this.player = Objects.requireNonNull(player, "player");
        this.packet = Objects.requireNonNull(packet, "packet");
        this.page = Math.max(0, page);
    }

    public boolean open() {
        //#if MC >= 12108
        GuiSession.cancelActive(player);
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        return RedPacketResultsDialog.open(player, packet, page);
        //#else
        //$$ return new LegacyRedPacketResultsGui(player, packet).open();
        //#endif
    }
}

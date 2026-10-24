package me.primaryuan.carpet.feature.redpacket;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class NormalRedPacket
    extends BaseRedPacket
    implements RedPacket
{
    public NormalRedPacket(
        ServerPlayer sender,
        List<ItemStack> shareItems, int redPacketAmount, int receiverAmount,
        long expireTimeAtTick
    ) {
        super(sender, RedPacketType.NORMAL, shareItems, redPacketAmount, receiverAmount, expireTimeAtTick);
    }
    NormalRedPacket() { super(RedPacketType.NORMAL); }

    public NormalRedPacket(ServerPlayer sender, List<ItemStack> items, int shares, String congratulations) {
        super(sender, RedPacketType.NORMAL, items, shares, congratulations);
    }
}

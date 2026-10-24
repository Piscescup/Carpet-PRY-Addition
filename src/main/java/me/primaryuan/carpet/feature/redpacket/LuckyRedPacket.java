package me.primaryuan.carpet.feature.redpacket;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.random.RandomGenerator;

public final class LuckyRedPacket
    extends BaseRedPacket
    implements RedPacket
{
    public LuckyRedPacket(
        ServerPlayer sender,
        List<ItemStack> shareItems, int redPacketAmount, int receiverAmount,
        long expireTimeAtTick
    ) {
        super(sender, RedPacketType.LUCKY, shareItems, redPacketAmount, receiverAmount, expireTimeAtTick);
    }
    LuckyRedPacket() { super(RedPacketType.LUCKY); }

    public LuckyRedPacket(ServerPlayer sender, List<ItemStack> items, int shares, String congratulations) {
        super(sender, RedPacketType.LUCKY, items, shares, congratulations);
    }
    @Override
    protected List<ItemStack> splitShare(List<ItemStack> remaining, int shares, RandomGenerator random) {
        return randomShare(remaining, shares, random);
    }
}

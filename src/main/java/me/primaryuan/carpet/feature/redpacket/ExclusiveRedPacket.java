package me.primaryuan.carpet.feature.redpacket;

import com.google.gson.annotations.Expose;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public final class ExclusiveRedPacket
    extends BaseRedPacket
    implements RedPacket
{
    public ExclusiveRedPacket(
        ServerPlayer sender,
        List<ItemStack> shareItems,
        long expireTimeAtTick
    ) {
        super(sender, RedPacketType.EXCLUSIVE, shareItems, 1, 1, expireTimeAtTick);
    }
    @Expose
    private UUID target;

    ExclusiveRedPacket() {
        super(RedPacketType.EXCLUSIVE);
    }

    public ExclusiveRedPacket(ServerPlayer sender, List<ItemStack> items, UUID target, String congratulations) {
        super(sender, RedPacketType.EXCLUSIVE, items, 1, congratulations);
        this.target = java.util.Objects.requireNonNull(target, "target");
    }

    public UUID target() { return target; }
    @Override
    protected boolean mayReceive(UUID player, boolean passwordVerified) {
        return player.equals(target);
    }
    @Override
    protected void validate() {
        super.validate();
        if (target == null || redPacketAmount != 1) throw new IllegalArgumentException("Invalid exclusive recipient");
    }
}

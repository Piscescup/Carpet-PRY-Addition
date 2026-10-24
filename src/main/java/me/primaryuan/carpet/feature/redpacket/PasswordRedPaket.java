package me.primaryuan.carpet.feature.redpacket;

import com.google.gson.annotations.Expose;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class PasswordRedPaket
    extends BaseRedPacket
    implements RedPacket
{
    @Expose
    private final String password;

    public PasswordRedPaket(
        ServerPlayer sender,
        List<ItemStack> shareItems, int redPacketAmount, int receiverAmount, String password,
        long expireTimeAtTick
    ) {
        super(sender, RedPacketType.PASSWORD, shareItems, redPacketAmount, receiverAmount, expireTimeAtTick);
        this.password = password;
    }

    public String redPacketPassword() {
        return this.password;
    }

    PasswordRedPaket() {
        super(RedPacketType.PASSWORD);
        this.password = "";
    }

    public PasswordRedPaket(
        ServerPlayer sender, List<ItemStack> items, int shares,
        String congratulations, String password
    ) {
        super(sender, RedPacketType.PASSWORD, items, shares, congratulations);
        if (!RedPacket.validText(password)) throw new IllegalArgumentException("Invalid password");
        this.password = password;
    }
    @Override
    protected List<ItemStack> splitShare(List<ItemStack> remaining, int shares, RandomGenerator random) {
        return randomShare(remaining, shares, random);
    }
    @Override
    protected boolean mayReceive(UUID player, boolean passwordVerified) {
        return passwordVerified;
    }
    @Override
    protected void validate() {
        super.validate();
        if (!RedPacket.validText(password)) throw new IllegalArgumentException("Invalid packet password");
    }
}

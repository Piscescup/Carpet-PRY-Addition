package me.primaryuan.carpet.feature.redpacket;


import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;


public interface RedPacket {
    int MAX_RED_PACKET_AMOUNTS = 100;
    int MAX_TEXT_LENGTH = 32;

    enum ClaimResult {
        CLAIMED, ALREADY_CLAIMED, TAKEN, EXPIRED, DENIED, STORAGE_ERROR
    }

    enum RedPacketType {
        /**
         * Random amount red packet.
         */
        LUCKY,

        /**
         * The normal red packet
         */
        NORMAL,

        /**
         * Exclusive red packet
         */
        EXCLUSIVE,

        /**
         * Password red packet.
         */
        PASSWORD;
    }

    RedPacketType redPacketType();

    ServerPlayer sender();

    List<ItemStack> shareItems();

    int redPacketAmount();

    int receiverAmount();

    long expireTimeAtTick();

    String id();

    UUID senderId();

    String senderName();

    String congratulations();

    UUID claimedBy(int shareIndex);

    boolean expired();

    ClaimResult claim(ServerPlayer player, int shareIndex, boolean passwordVerified);

    static boolean validText(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_TEXT_LENGTH;
    }
}

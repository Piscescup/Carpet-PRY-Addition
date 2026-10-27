package me.primaryuan.carpet.feature.redpacket;


import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;


/**
 * Represents a created red packet that players can claim.
 *
 * <p>A red packet consists of a sender, items to distribute, a number of shares,
 * and an expiration time. Implementations maintain its state and validate claims
 * and distribute items when {@link #claim(ServerPlayer, int, boolean)} is called.</p>
 *
 * <p>Each share is identified by a zero-based index. A player may claim only one
 * share. {@link #claimedBy(int)} returns {@code null} when the share is unclaimed.</p>
 *
 * @see BaseRedPacket
 * @see LuckyRedPacket
 * @see NormalRedPacket
 * @see ExclusiveRedPacket
 * @see PasswordRedPaket
 */
public interface RedPacket {
    /**
     * The maximum number of shares in a red packet.
     */
    int MAX_RED_PACKET_AMOUNTS = 100;

    /**
     * The maximum length of text fields such as greetings and passwords.
     */
    int MAX_TEXT_LENGTH = 32;

    /**
     * The result of a claim attempt.
     */
    enum ClaimResult {
        /**
         * The claim succeeded and the items were assigned to the claimant.
         */
        CLAIMED,
        /**
         * The player has already claimed a share from this red packet.
         */
        ALREADY_CLAIMED,
        /**
         * The requested share has already been claimed by another player.
         */
        TAKEN,
        /**
         * The red packet has expired or its remaining items were refunded.
         */
        EXPIRED,
        /**
         * The player does not satisfy this red packet's claim requirements.
         */
        DENIED,
        /**
         * The red packet state could not be saved safely, so the claim was cancelled.
         */
        STORAGE_ERROR
    }

    /**
     * The distribution and claiming rules of a red packet.
     */
    enum RedPacketType {
        /**
         * A lucky red packet whose item amounts are distributed randomly.
         */
        LUCKY,

        /**
         * A normal red packet whose items are distributed as evenly as possible.
         */
        NORMAL,

        /**
         * An exclusive red packet that only its target player can claim.
         */
        EXCLUSIVE,

        /**
         * A password red packet that requires a valid password before claiming.
         */
        PASSWORD;
    }

    /**
     * Returns this red packet's type.
     *
     * @return the red packet type
     */
    RedPacketType redPacketType();

    /**
     * Returns the sender's current player instance.
     *
     * @return the sender, or {@code null} if the sender is offline
     */
    ServerPlayer sender();

    /**
     * Returns the items that have not yet been distributed.
     *
     * <p>The returned stacks are copies. Mutating them does not affect this red packet.</p>
     *
     * @return a list of copies of the remaining items
     */
    List<ItemStack> shareItems();

    /**
     * Returns the total number of shares in this red packet.
     *
     * @return the total share count, from {@code 1} to {@link #MAX_RED_PACKET_AMOUNTS}
     */
    int redPacketAmount();

    /**
     * Returns the number of recipients configured when this red packet was created.
     *
     * @return the configured recipient count
     */
    int receiverAmount();

    /**
     * Returns the server game tick at which this red packet expires.
     *
     * @return the expiration game tick
     */
    long expireTimeAtTick();

    /**
     * Returns this red packet's unique identifier.
     *
     * @return the red packet identifier
     */
    String id();

    /**
     * Returns the sender's unique identifier, including while the sender is offline.
     *
     * @return the sender UUID
     */
    UUID senderId();

    /**
     * Returns the sender name stored when this red packet was created.
     *
     * @return the sender name
     */
    String senderName();

    /**
     * Returns the greeting set by the sender.
     *
     * @return the greeting
     */
    String congratulations();

    /**
     * Returns the claimant of the specified share.
     *
     * @param shareIndex the share index, from {@code 0} inclusive to
     *                   {@link #redPacketAmount()} exclusive
     * @return the claimant UUID, or {@code null} if the share is unclaimed
     */
    UUID claimedBy(int shareIndex);

    /**
     * Determines whether this red packet has expired or was refunded.
     *
     * @return {@code true} if this red packet can no longer be claimed
     */
    boolean expired();

    /**
     * Attempts to let a player claim the specified share.
     *
     * @param player           the player attempting to claim the share
     * @param shareIndex       the share index to claim
     * @param passwordVerified whether the caller has verified the password;
     *                         {@code false} may be supplied for non-password red packets
     * @return the result of this claim attempt
     */
    ClaimResult claim(ServerPlayer player, int shareIndex, boolean passwordVerified);

    /**
     * Determines whether a value can be used for a limited red packet text field.
     *
     * @param value the value to validate
     * @return {@code true} if the value is not blank and its length does not exceed
     * {@link #MAX_TEXT_LENGTH}
     */
    static boolean validText(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_TEXT_LENGTH;
    }
}

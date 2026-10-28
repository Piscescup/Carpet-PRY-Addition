package me.primaryuan.carpet.feature.redpacket.gui;

import me.primaryuan.carpet.feature.redpacket.RedPacket;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared display snapshots for native result lists and the older-client menu.
 */
final class RedPacketResultsData {
    private RedPacketResultsData() {
    }

    record Reward(ItemStack item, int count) {
    }

    record Recipient(int share, UUID id, String name, List<Reward> rewards) {
    }

    static int claimed(RedPacket packet) {
        int count = 0;
        for (int share = 0; share < packet.redPacketAmount(); share++) {
            if (packet.claimedBy(share) != null) count++;
        }
        return count;
    }

    static List<Recipient> recipients(RedPacket packet) {
        List<Recipient> recipients = new ArrayList<>();
        for (int share = 0; share < packet.redPacketAmount(); share++) {
            UUID claimant = packet.claimedBy(share);
            if (claimant == null) continue;
            List<Reward> rewards = new ArrayList<>();
            for (ItemStack item : packet.claimedItems(share)) {
                int matching = -1;
                for (int i = 0; i < rewards.size(); i++) {
                    if (ItemStack.isSameItemSameComponents(rewards.get(i).item(), item)) {
                        matching = i;
                        break;
                    }
                }
                if (matching < 0) rewards.add(new Reward(item, item.getCount()));
                else {
                    Reward previous = rewards.get(matching);
                    rewards.set(matching, new Reward(previous.item(), previous.count() + item.getCount()));
                }
            }
            recipients.add(new Recipient(share, claimant, packet.claimantName(share), List.copyOf(rewards)));
        }
        return List.copyOf(recipients);
    }
}

package me.primaryuan.carpet.feature.redpacket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedPacketHistoryTest {
    @TempDir
    Path temporary;

    @BeforeAll
    static void bootstrap() {
        TestRegistries.bootstrap();
    }

    @Test
    void claimedHistoryPreservesActualRewardAndCannotBeChangedThroughReturnedStacks() {
        BaseRedPacket packet = packet(false, 1, List.of(namedDiamond(7)));
        UUID player = UUID.randomUUID();

        List<ItemStack> reward = packet.takeShare(player, "OfflineWinner", 0);
        reward.getFirst().shrink(5);

        assertEquals("OfflineWinner", packet.claimantName(0));
        assertEquals(player, packet.claimedBy(0));
        assertEquals(7, packet.claimedItems(0).getFirst().getCount());
        assertTrue(ItemStack.isSameItemSameComponents(namedDiamond(1), packet.claimedItems(0).getFirst()));
        packet.claimedItems(0).getFirst().setCount(1);
        assertEquals(7, packet.claimedItems(0).getFirst().getCount());
        assertTrue(packet.shareItems().isEmpty());
        assertEquals(RedPacket.ClaimResult.ALREADY_CLAIMED, packet.checkClaim(player, 0, false));
    }

    @Test
    void luckyClaimsConserveEveryKindAndPersistEachAward() {
        BaseRedPacket packet = packet(true, 8,
            List.of(namedDiamond(32), new ItemStack(Items.GOLD_INGOT, 23), new ItemStack(Items.DIAMOND, 9)));
        int[] totals = new int[3];

        for (int share = 0; share < packet.redPacketAmount(); share++) {
            UUID player = UUID.randomUUID();
            assertEquals(RedPacket.ClaimResult.CLAIMED, packet.checkClaim(player, share, false));
            List<ItemStack> awarded = packet.takeShare(player, "Winner" + share, share);
            assertFalse(awarded.isEmpty());
            assertEquals(count(awarded), count(packet.claimedItems(share)));
            assertEquals("Winner" + share, packet.claimantName(share));
            for (ItemStack stack : awarded) {
                if (stack.is(Items.GOLD_INGOT)) totals[0] += stack.getCount();
                else if (stack.has(DataComponents.CUSTOM_NAME)) totals[1] += stack.getCount();
                else totals[2] += stack.getCount();
            }
            packet.validate();
        }

        assertEquals(23, totals[0]);
        assertEquals(32, totals[1]);
        assertEquals(9, totals[2]);
        assertTrue(packet.shareItems().isEmpty());
    }

    @Test
    void storageRoundTripKeepsClaimNamesItemCountsAndComponentsAfterExpiry() throws Exception {
        BaseRedPacket packet = packet(false, 2, List.of(namedDiamond(14), new ItemStack(Items.GOLD_INGOT, 8)));
        UUID winner = UUID.randomUUID();
        packet.takeShare(winner, "WinnerBeforeRestart", 0);
        packet.expiresAtMillis = 1;
        packet.refunded = true;
        packet.shareItems = List.of();
        RedPacketStore store = store(temporary.resolve("roundtrip.json"));
        store.save(packet);

        BaseRedPacket restored = store.load(BaseRedPacket.class);
        restored.restore(null);

        assertTrue(restored.expired());
        assertEquals(winner, restored.claimedBy(0));
        assertEquals("WinnerBeforeRestart", restored.claimantName(0));
        assertEquals(count(packet.claimedItems(0)), count(restored.claimedItems(0)));
        for (int index = 0; index < packet.claimedItems(0).size(); index++) {
            ItemStack expected = packet.claimedItems(0).get(index);
            ItemStack actual = restored.claimedItems(0).get(index);
            assertEquals(expected.getCount(), actual.getCount());
            assertTrue(ItemStack.isSameItemSameComponents(expected, actual));
        }
        assertEquals(RedPacket.ClaimResult.EXPIRED, restored.checkClaim(UUID.randomUUID(), 1, false));
    }

    @Test
    void legacyStorageWithoutRewardHistoryStillLoadsClaimantsAndRemainingItems() throws Exception {
        BaseRedPacket packet = packet(false, 2, List.of(namedDiamond(10)));
        UUID winner = UUID.randomUUID();
        packet.takeShare(winner, "LegacyWinner", 0);
        Path path = temporary.resolve("legacy.json");
        RedPacketStore store = store(path);
        store.save(packet);
        JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        json.remove("claimItems");
        Files.writeString(path, json.toString());

        BaseRedPacket restored = store.load(BaseRedPacket.class);
        restored.restore(null);

        assertEquals(winner, restored.claimedBy(0));
        assertEquals("LegacyWinner", restored.claimantName(0));
        assertTrue(restored.claimedItems(0).isEmpty());
        assertEquals(5, count(restored.shareItems()));
        assertEquals(RedPacket.ClaimResult.ALREADY_CLAIMED, restored.checkClaim(winner, 1, false));
        assertEquals(RedPacket.ClaimResult.CLAIMED, restored.checkClaim(UUID.randomUUID(), 1, false));
    }

    private RedPacketStore store(Path path) {
        return new RedPacketStore(path, RegistryLayer.createRegistryAccess().compositeAccess());
    }

    private static BaseRedPacket packet(boolean lucky, int shares, List<ItemStack> items) {
        BaseRedPacket packet = lucky ? new LuckyRedPacket() : new NormalRedPacket();
        packet.id = "packet-" + UUID.randomUUID();
        packet.senderId = UUID.randomUUID();
        packet.senderName = "Sender";
        packet.redPacketAmount = shares;
        packet.receiverAmount = shares;
        packet.congratulations = "Good luck";
        packet.expiresAtMillis = Long.MAX_VALUE;
        packet.shareItems = new ArrayList<>(items);
        packet.claimants = new LinkedHashMap<>();
        packet.claimantNames = new HashMap<>();
        packet.claimItems = new LinkedHashMap<>();
        packet.validate();
        return packet;
    }

    private static ItemStack namedDiamond(int count) {
        ItemStack stack = new ItemStack(Items.DIAMOND, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Ruby prize"));
        return stack;
    }

    private static int count(List<ItemStack> stacks) {
        return stacks.stream().mapToInt(ItemStack::getCount).sum();
    }
}

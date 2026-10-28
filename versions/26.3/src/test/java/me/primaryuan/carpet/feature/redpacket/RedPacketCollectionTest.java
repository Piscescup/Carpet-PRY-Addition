package me.primaryuan.carpet.feature.redpacket;

import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedPacketCollectionTest {
    @BeforeAll
    static void bootstrap() {
        TestRegistries.bootstrap();
    }

    @Test
    void mergesThenUsesEmptySlotsWithoutChangingInventoryOrReward() {
        SimpleContainer inventory = new SimpleContainer(36);
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 50));
        ItemStack reward = new ItemStack(Items.DIAMOND, 30);

        RedPacketManager.CollectionPlan plan = RedPacketManager.planCollection(inventory, List.of(reward));

        assertEquals(30, plan.moved());
        assertTrue(plan.remaining().isEmpty());
        assertEquals(64, plan.slots().get(0).getCount());
        assertEquals(16, plan.slots().get(1).getCount());
        assertEquals(50, inventory.getItem(0).getCount());
        assertTrue(inventory.getItem(1).isEmpty());
        assertEquals(30, reward.getCount());
    }

    @Test
    void fullInventoryKeepsEveryRewardInPendingRemainder() {
        SimpleContainer inventory = fullInventory(36);
        List<ItemStack> rewards = List.of(new ItemStack(Items.DIAMOND, 30), new ItemStack(Items.GOLD_INGOT, 12));

        RedPacketManager.CollectionPlan plan = RedPacketManager.planCollection(inventory, rewards);

        assertEquals(0, plan.moved());
        assertEquals(42, count(plan.remaining()));
        assertEquals(36 * 64, count(plan.slots()));
        assertEquals(30, rewards.getFirst().getCount());
        assertEquals(12, rewards.getLast().getCount());
    }

    @Test
    void partialMergeRetainsOnlyOverflowAndConservesAllItems() {
        SimpleContainer inventory = fullInventory(36);
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 60));
        ItemStack reward = new ItemStack(Items.DIAMOND, 10);

        RedPacketManager.CollectionPlan plan = RedPacketManager.planCollection(inventory, List.of(reward));

        assertEquals(4, plan.moved());
        assertEquals(64, plan.slots().getFirst().getCount());
        assertEquals(6, plan.remaining().getFirst().getCount());
        assertEquals(60, inventory.getItem(0).getCount());
        assertEquals(10, reward.getCount());
        assertEquals(countInventory(inventory) + reward.getCount(), count(plan.slots()) + count(plan.remaining()));
    }

    @Test
    void sameItemWithDifferentComponentsNeverMerges() {
        SimpleContainer inventory = fullInventory(36);
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 1));
        ItemStack named = new ItemStack(Items.DIAMOND, 12);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Special prize"));

        RedPacketManager.CollectionPlan plan = RedPacketManager.planCollection(inventory, List.of(named));

        assertEquals(0, plan.moved());
        assertEquals(1, plan.slots().getFirst().getCount());
        assertTrue(ItemStack.isSameItemSameComponents(named, plan.remaining().getFirst()));
        assertEquals(12, plan.remaining().getFirst().getCount());
    }

    @Test
    void emptyArmorAndOffhandSlotsDoNotCountAsRewardCapacity() {
        SimpleContainer inventory = fullInventory(41);
        for (int slot = 36; slot < 41; slot++) inventory.setItem(slot, ItemStack.EMPTY);

        RedPacketManager.CollectionPlan plan = RedPacketManager.planCollection(inventory,
            List.of(new ItemStack(Items.DIAMOND, 1)));

        assertEquals(36, plan.slots().size());
        assertEquals(0, plan.moved());
        assertEquals(1, count(plan.remaining()));
    }

    @Test
    void moreRewardStacksThanInventorySlotsCanBeCollectedPartially() {
        SimpleContainer inventory = new SimpleContainer(36);
        List<ItemStack> rewards = new ArrayList<>();
        for (int stack = 0; stack < 45; stack++) rewards.add(new ItemStack(Items.DIAMOND, 64));

        RedPacketManager.CollectionPlan plan = RedPacketManager.planCollection(inventory, rewards);

        assertEquals(36 * 64, plan.moved());
        assertEquals(9 * 64, count(plan.remaining()));
        assertEquals(45 * 64, count(plan.slots()) + count(plan.remaining()));
        assertTrue(inventory.isEmpty());
        assertEquals(45 * 64, count(rewards));
    }

    @Test
    void unstackableRewardsUseOneSlotEachAndKeepComponents() {
        SimpleContainer inventory = fullInventory(36);
        inventory.setItem(3, ItemStack.EMPTY);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Lucky sword"));
        ItemStack spare = new ItemStack(Items.DIAMOND_SWORD);

        RedPacketManager.CollectionPlan plan = RedPacketManager.planCollection(inventory, List.of(sword, spare));

        assertEquals(1, plan.moved());
        assertEquals(1, plan.slots().get(3).getCount());
        assertTrue(ItemStack.isSameItemSameComponents(sword, plan.slots().get(3)));
        assertEquals(1, count(plan.remaining()));
        assertTrue(ItemStack.isSameItemSameComponents(spare, plan.remaining().getFirst()));
    }

    private static SimpleContainer fullInventory(int size) {
        SimpleContainer inventory = new SimpleContainer(size);
        for (int slot = 0; slot < size; slot++) inventory.setItem(slot, new ItemStack(Items.STONE, 64));
        return inventory;
    }

    private static int count(List<ItemStack> stacks) {
        return stacks.stream().mapToInt(ItemStack::getCount).sum();
    }

    private static int countInventory(SimpleContainer inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) count += inventory.getItem(slot).getCount();
        return count;
    }
}

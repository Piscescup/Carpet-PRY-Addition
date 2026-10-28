package me.primaryuan.carpet.feature.redpacket.gui;

import me.primaryuan.carpet.feature.redpacket.RedPacket;
import me.primaryuan.carpet.feature.redpacket.RedPacketManager;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Vertical display reels over rewards that have already been reserved and persisted.
 */
public final class RedPacketSpinGui {
    private static final String PREFIX = "carpetprimaryuan.redpacket.spin_gui.";
    private static final int STEPS = 20;
    private static final int ANIMATION_REELS = 3;
    private static final int REWARD_PAGE_SIZE = RewardLayout.PAGE_SIZE;
    private static final int[] SINGLE_COLUMN = {4};
    private static final int[] TWO_COLUMNS = {3, 5};
    private static final int[] THREE_COLUMNS = {3, 4, 5};

    private final GuiSession session;
    private final ChestGui gui;
    private final RedPacket packet;
    private final List<ItemStack> rewards;
    private final List<ItemStack> preview;
    private final List<Reel> reels = new ArrayList<>();
    private final RandomGenerator random = RandomGenerator.getDefault();
    private int page;
    private boolean watching;
    private boolean completed;
    private boolean receiving;

    public RedPacketSpinGui(ServerPlayer player, RedPacket packet, int shareIndex, List<ItemStack> preview) {
        this.packet = Objects.requireNonNull(packet, "packet");
        this.rewards = packet.claimedItems(shareIndex);
        this.preview = preview.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
        this.session = new GuiSession(player, 0);
        this.gui = new ChestGui(session, 6, tr("title"));
        for (int slot = 0; slot < 54; slot++) gui.icon(slot, GuiItems.filler());
        int[] columns = columns(Math.min(ANIMATION_REELS, rewards.size()));
        for (int i = 0; i < columns.length; i++) {
            reels.add(new Reel(columns[i], rewards.get(i), STEPS + i * 2));
        }
        gui.icon(20, GuiItems.icon(Items.GOLD_NUGGET,
            tr("pointer").copy().withStyle(ChatFormatting.GOLD)));
        gui.icon(24, GuiItems.icon(Items.GOLD_NUGGET,
            tr("pointer").copy().withStyle(ChatFormatting.GOLD)));
        renderReels();
        renderStatus();
        renderNavigation();
    }

    public boolean open() {
        if (!gui.open()) return false;
        if (!watching) {
            watching = true;
            ServerTickScheduler.register(server -> {
                if (session.isFinished() || completed) return false;
                if (session.player().level().getServer() != server) return true;
                boolean changed = false;
                boolean allStopped = true;
                for (Reel reel : reels) {
                    changed |= reel.tick();
                    allStopped &= reel.stopped();
                }
                if (allStopped) {
                    completed = true;
                    renderRewards();
                    renderNavigation();
                } else if (changed) {
                    renderReels();
                }
                return !completed;
            });
        }
        return true;
    }

    private ItemStack randomPreview() {
        List<ItemStack> candidates = preview.isEmpty() ? rewards : preview;
        if (candidates.isEmpty()) return ItemStack.EMPTY;
        ItemStack template = candidates.get(random.nextInt(candidates.size()));
        return template.copyWithCount(random.nextInt(1, template.getCount() + 1));
    }

    private void renderReels() {
        for (Reel reel : reels) {
            for (int row = 0; row < 3; row++) {
                ItemStack stack = reel.frames.get(row);
                boolean winner = row == 1 && reel.stopped();
                gui.icon((row + 1) * 9 + reel.column, stack.isEmpty() ? GuiItems.filler()
                    : display(stack, row == 1, tr(winner ? "stored" : "cosmetic")));
            }
        }
    }

    private void renderStatus() {
        List<Component> lore = new ArrayList<>(List.of(tr("stored"), tr("safe_close")));
        int pages = Math.max(1, (rewards.size() + REWARD_PAGE_SIZE - 1) / REWARD_PAGE_SIZE);
        if (completed && pages > 1) lore.add(claimTr("page", page + 1, pages));
        gui.icon(4, GuiItems.icon(completed ? Items.NETHER_STAR : Items.CLOCK,
            tr(completed ? "complete" : "spinning").copy().withStyle(ChatFormatting.GOLD),
            lore.toArray(Component[]::new)));
    }

    private void renderRewards() {
        int pages = Math.max(1, (rewards.size() + REWARD_PAGE_SIZE - 1) / REWARD_PAGE_SIZE);
        page = Math.clamp(page, 0, pages - 1);
        renderStatus();
        for (int slot = 9; slot < 45; slot++) gui.icon(slot, GuiItems.filler());
        int offset = page * REWARD_PAGE_SIZE;
        int[] slots = RewardLayout.slots(Math.min(REWARD_PAGE_SIZE, rewards.size() - offset));
        for (int i = 0; i < slots.length; i++) {
            gui.icon(slots[i], display(rewards.get(offset + i), true, tr("stored")));
        }
    }

    private void renderNavigation() {
        for (int slot = 45; slot < 54; slot++) gui.icon(slot, GuiItems.filler());
        gui.button(51, GuiItems.icon(Items.CLOCK,
                tr("receive_later").copy().withStyle(ChatFormatting.YELLOW), tr("receive_later_help")),
            click -> session.cancel());
        gui.button(49, GuiItems.icon(Items.BOOK, claimTr("results")), click ->
            new RedPacketResultsGui(session.player(), packet).open());
        if (!completed) {
            gui.icon(47, GuiItems.icon(Items.EMERALD,
                tr("receive_now").copy().withStyle(ChatFormatting.GRAY), tr("receive_wait")));
            return;
        }
        gui.button(47, GuiItems.icon(Items.EMERALD,
            tr("receive_now").copy().withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
            tr("receive_now_help")), click -> receiveNow());
        int pages = Math.max(1, (rewards.size() + REWARD_PAGE_SIZE - 1) / REWARD_PAGE_SIZE);
        if (page > 0) gui.button(45, GuiItems.icon(Items.ARROW, claimTr("previous")), click -> {
            page--;
            renderRewards();
            renderNavigation();
        });
        if (page + 1 < pages) gui.button(53, GuiItems.icon(Items.ARROW, claimTr("next")), click -> {
            page++;
            renderRewards();
            renderNavigation();
        });
    }

    private void receiveNow() {
        if (!completed || receiving || session.isFinished()) return;
        receiving = true;
        try {
            // Collect only this packet's reserved reward, across all displayed pages.
            // The manager retains any overflow; failed collections leave the choice open.
            if (RedPacketManager.collectClaim(session.player(), packet.id()) > 0) session.cancel();
        } finally {
            receiving = false;
        }
    }

    private static int[] columns(int count) {
        return switch (count) {
            case 1 -> SINGLE_COLUMN;
            case 2 -> TWO_COLUMNS;
            case 3 -> THREE_COLUMNS;
            default -> new int[0];
        };
    }

    private static ItemStack display(ItemStack source, boolean highlighted, Component... lore) {
        ItemStack icon = source.copy();
        var name = source.getHoverName().copy().append(" × " + source.getCount());
        if (highlighted) name.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        icon.remove(DataComponents.CUSTOM_NAME);
        icon.set(DataComponents.ITEM_NAME, name);
        icon.set(DataComponents.LORE, new ItemLore(List.of(lore)));
        return icon;
    }

    /**
     * Each reel moves top to bottom and slows independently before the saved reward lands.
     */
    private final class Reel {
        private final int column;
        private final ItemStack reward;
        private final int totalSteps;
        private final List<ItemStack> frames = new ArrayList<>();
        private int step;
        private int ticks;

        private Reel(int column, ItemStack reward, int totalSteps) {
            this.column = column;
            this.reward = reward.copy();
            this.totalSteps = totalSteps;
            for (int row = 0; row < 3; row++) frames.add(randomPreview());
        }

        private boolean tick() {
            if (stopped() || ++ticks < 2 + step / 4) return false;
            ticks = 0;
            frames.remove(2);
            step++;
            // The next downward step moves this icon from the top to the winning middle row.
            frames.add(0, step == totalSteps - 1 ? reward.copy() : randomPreview());
            return true;
        }

        private boolean stopped() {
            return step >= totalSteps;
        }
    }

    private static Component tr(String key, Object... args) {
        return ServerI18n.tr(PREFIX + key, args);
    }

    private static Component claimTr(String key, Object... args) {
        return ServerI18n.tr("carpetprimaryuan.redpacket.claim_gui." + key, args);
    }
}

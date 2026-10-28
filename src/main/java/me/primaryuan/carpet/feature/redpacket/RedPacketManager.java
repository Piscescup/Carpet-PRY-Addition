package me.primaryuan.carpet.feature.redpacket;

import carpet.patches.EntityPlayerMPFake;
import com.google.gson.annotations.Expose;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.feature.redpacket.gui.GuiItems;
import me.primaryuan.carpet.feature.redpacket.gui.GuiSession;
import me.primaryuan.carpet.feature.redpacket.gui.RedPacketClaimGui;
import me.primaryuan.carpet.feature.redpacket.gui.RedPacketResultsGui;
import me.primaryuan.carpet.feature.redpacket.gui.TextInputGui;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static me.primaryuan.carpet.CarpetPrimaryuanServer.LOGGER;


public final class RedPacketManager {
    private static final Map<MinecraftServer, RedPacketManager> SERVERS = new IdentityHashMap<>();
    private static boolean initialized;
    private final MinecraftServer server;
    private final RedPacketStore store;
    final Map<String, BaseRedPacket> packets = new LinkedHashMap<>();
    final List<Delivery> deliveries = new ArrayList<>();
    final Set<UUID> muted = new HashSet<>();
    private int ticks;

    public static final String REDPACKET_KEY = "carpetprimaryuan.redpacket.runtime.";

    record Delivery(@Expose UUID id, @Expose UUID owner, @Expose List<ItemStack> items,
                    @Expose boolean refund, @Expose String packetId) {
        Delivery {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(owner, "owner");
            items = items.stream().map(ItemStack::copy).toList();
            if (items.isEmpty() || items.stream().anyMatch(stack -> stack.isEmpty()
                || stack.getCount() > stack.getMaxStackSize()))
                throw new IllegalArgumentException("Invalid delivery items");
        }
    }

    /**
     * A display-only snapshot. Item mutations never affect the saved reward.
     */
    public record PendingReward(UUID id, List<ItemStack> items, boolean refund) {
        public PendingReward {
            Objects.requireNonNull(id, "id");
            items = items.stream().map(ItemStack::copy).toList();
        }

        @Override
        public List<ItemStack> items() {
            return items.stream().map(ItemStack::copy).toList();
        }
    }

    record CollectionPlan(List<ItemStack> slots, List<ItemStack> remaining, int moved) {
    }

    private record SavedData(@Expose List<BaseRedPacket> packets,
                             @Expose List<Delivery> deliveries,
                             @Expose Set<UUID> muted) {
    }

    private RedPacketManager(MinecraftServer server) throws IOException {
        this.server = server;
        this.store = new RedPacketStore(
            server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve("carpet-pry-addition")
                .resolve("carpet-pry-redpackets.json"),
            server.registryAccess()
        );
        load();
    }

    private void load() throws IOException {
        SavedData data = store.load(SavedData.class);
        if (data == null) return;
        try {
            if (data.packets == null || data.deliveries == null || data.muted == null || data.muted.contains(null)) {
                throw new IllegalArgumentException("Incomplete red packet storage");
            }
            for (BaseRedPacket packet : data.packets) {
                Objects.requireNonNull(packet, "packet").restore(server);
                if (packets.putIfAbsent(packet.id(), packet) != null)
                    throw new IllegalArgumentException("Duplicate packet id");
            }
            Set<UUID> deliveryIds = new HashSet<>();
            for (Delivery delivery : data.deliveries) {
                if (!deliveryIds.add(Objects.requireNonNull(delivery, "delivery").id())) {
                    throw new IllegalArgumentException("Duplicate delivery id");
                }
            }
            deliveries.addAll(data.deliveries);
            muted.addAll(data.muted);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid saved red packet data", invalid);
        }
    }

    private void save() throws IOException {
        store.save(new SavedData(new ArrayList<>(packets.values()), deliveries, muted));
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                get(server).tick();
            } catch (IOException error) {
                LOGGER.error("[RedPacket] Cannot load saved packets", error);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                if (handler.player instanceof EntityPlayerMPFake) return;
                int pending = get(server).pendingRewards(handler.player.getUUID()).size();
                if (pending > 0) handler.player.sendSystemMessage(tr("pending_notice", pending));
            } catch (IOException error) {
                storageError(handler.player, error);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            RedPacketManager manager = SERVERS.remove(server);
            if (manager != null) {
                try {
                    manager.save();
                } catch (IOException error) {
                    LOGGER.error("[RedPacket] Cannot save packets at shutdown", error);
                }
            }
        });
        ServerTickScheduler.register(server -> {
            RedPacketManager manager = SERVERS.get(server);
            if (manager != null && ++manager.ticks >= 20) {
                manager.ticks = 0;
                manager.tick();
            }
            return true;
        });
    }

    public static RedPacketManager get(MinecraftServer server) throws IOException {
        RedPacketManager manager = SERVERS.get(server);
        if (manager == null) {
            manager = new RedPacketManager(server);
            SERVERS.put(server, manager);
        }
        return manager;
    }

    public static void publish(RedPacket packet, GuiSession session) {
        ServerPlayer player = session.player();
        if (!CarpetPrimaryuanSettings.redPacket || player instanceof EntityPlayerMPFake) {
            player.sendSystemMessage(tr("publish_denied"));
            return;
        }
        if (session.isFinished()) return;
        if (!(packet instanceof BaseRedPacket created) || !player.getUUID().equals(packet.senderId())) {
            player.sendSystemMessage(tr("invalid_items"));
            return;
        }
        try {
            created.validate();
        } catch (IllegalArgumentException invalid) {
            player.sendSystemMessage(tr("invalid_items"));
            return;
        }
        List<ItemStack> taken = null;
        try {
            RedPacketManager manager = get(player.level().getServer());
            if (created instanceof ExclusiveRedPacket exclusive) {
                ServerPlayer target = manager.server.getPlayerList().getPlayer(exclusive.target());
                if (target == null || target instanceof EntityPlayerMPFake || target.getUUID().equals(player.getUUID())) {
                    player.sendSystemMessage(tr("target_offline"));
                    return;
                }
            }
            if (manager.packets.containsKey(created.id)) return;
            List<ItemStack> contents = session.contents();
            List<ItemStack> packetItems = created.shareItems();
            if (!sameItems(contents, packetItems)) {
                player.sendSystemMessage(tr("invalid_items"));
                return;
            }
            long expiresAt;
            try {
                expiresAt = Math.addExact(System.currentTimeMillis(), created.lifetimeMillis());
            } catch (ArithmeticException invalid) {
                player.sendSystemMessage(tr("invalid_duration"));
                return;
            }
            taken = session.takeItems();
            created.expiresAtMillis = expiresAt;
            created.bind(manager.server);
            manager.packets.put(created.id, created);
            try {
                manager.save();
            } catch (IOException error) {
                manager.packets.remove(created.id);
                throw error;
            }
            taken = null;
            player.sendSystemMessage(tr("published", packet.redPacketAmount(),
                packet.durationUnit().format(packet.durationAmount())));
            manager.broadcast(created);
        } catch (IOException error) {
            storageError(player, error);
        } finally {
            if (taken != null) for (ItemStack stack : taken) give(player, stack);
        }
    }

    private static boolean sameItems(List<ItemStack> expected, List<ItemStack> actual) {
        List<ItemStack> left = actual.stream().map(ItemStack::copy).toList();
        for (ItemStack stack : expected) {
            int remaining = stack.getCount();
            for (ItemStack candidate : left) {
                if (!ItemStack.isSameItemSameComponents(stack, candidate)) continue;
                int count = Math.min(remaining, candidate.getCount());
                candidate.shrink(count);
                remaining -= count;
                if (remaining == 0) break;
            }
            if (remaining != 0) return false;
        }
        return left.stream().allMatch(ItemStack::isEmpty);
    }

    public static int open(ServerPlayer player, String id) {
        try {
            return get(player.level().getServer()).open(player, id, false);
        } catch (IOException error) {
            storageError(player, error);
            return 0;
        }
    }

    private int open(ServerPlayer player, String id, boolean verified) {
        BaseRedPacket packet = packets.get(id);
        if (packet == null) {
            player.sendSystemMessage(tr("expired"));
            return 0;
        }
        if (packet.expired()) {
            if (!CarpetPrimaryuanSettings.redPacket || player instanceof EntityPlayerMPFake) {
                player.sendSystemMessage(tr("denied"));
                return 0;
            }
            return new RedPacketResultsGui(player, packet).open() ? 1 : 0;
        }
        if (!CarpetPrimaryuanSettings.redPacket || player instanceof EntityPlayerMPFake
            || (packet instanceof ExclusiveRedPacket exclusive && !player.getUUID().equals(exclusive.target()))) {
            player.sendSystemMessage(tr("denied"));
            return 0;
        }
        if (packet instanceof PasswordRedPaket password && !verified) {
            GuiSession auth = new GuiSession(player, 0);
            return new TextInputGui(auth, tr("password_title"), "",
                GuiItems.icon(Items.PAPER, tr("password_submit")), text -> {
                if (password.redPacketPassword().equals(text)) return true;
                player.sendSystemMessage(tr("wrong_password"));
                return false;
            }, text -> {
                auth.cancel();
                open(player, id, true);
            }).open() ? 1 : 0;
        }
        return new RedPacketClaimGui(player, packet, verified).open() ? 1 : 0;
    }

    static RedPacket.ClaimResult claim(BaseRedPacket packet, ServerPlayer player, int index, boolean verified) {
        if (!CarpetPrimaryuanSettings.redPacket || player instanceof EntityPlayerMPFake)
            return RedPacket.ClaimResult.DENIED;
        try {
            RedPacketManager manager = get(player.level().getServer());
            if (manager.packets.get(packet.id) != packet) return RedPacket.ClaimResult.EXPIRED;
            RedPacket.ClaimResult result = packet.checkClaim(player.getUUID(), index, verified);
            if (result != RedPacket.ClaimResult.CLAIMED) return result;
            List<ItemStack> before = packet.shareItems;
            boolean previouslyAnnounced = packet.completionAnnounced;
            List<ItemStack> items = packet.takeShare(player.getUUID(), player.getName().getString(), index);
            boolean completed = !previouslyAnnounced && packet.claimants.size() == packet.redPacketAmount();
            if (completed) packet.completionAnnounced = true;
            Delivery delivery = new Delivery(UUID.randomUUID(), player.getUUID(), items, false, packet.id);
            manager.deliveries.add(delivery);
            try {
                manager.save();
            } catch (IOException error) {
                packet.shareItems = before;
                packet.claimants.remove(index);
                packet.claimantNames.remove(player.getUUID());
                packet.claimItems.remove(index);
                packet.completionAnnounced = previouslyAnnounced;
                manager.deliveries.remove(delivery);
                throw error;
            }
            ServerPlayer owner = packet.sender();
            if (owner != null && !manager.muted.contains(owner.getUUID())) {
                owner.sendSystemMessage(tr("receipt", player.getName().getString(), packet.congratulations()));
            }
            if (completed) manager.broadcastCompletion(packet);
            return RedPacket.ClaimResult.CLAIMED;
        } catch (IOException error) {
            storageError(player, error);
            return RedPacket.ClaimResult.STORAGE_ERROR;
        }
    }

    public static int mute(ServerPlayer player, boolean value) {
        try {
            RedPacketManager manager = get(player.level().getServer());
            boolean old = manager.muted.contains(player.getUUID());
            if (value) manager.muted.add(player.getUUID());
            else manager.muted.remove(player.getUUID());
            try {
                manager.save();
            } catch (IOException error) {
                if (old) manager.muted.add(player.getUUID());
                else manager.muted.remove(player.getUUID());
                throw error;
            }
            player.sendSystemMessage(tr(value ? "muted" : "unmuted"));
            return 1;
        } catch (IOException error) {
            storageError(player, error);
            return 0;
        }
    }

    private void tick() {
        try {
            expire();
        } catch (IOException error) {
            LOGGER.error("[RedPacket] Failed to save expiry/refund state", error);
        }
    }

    private void expire() throws IOException {
        List<BaseRedPacket> expired = packets.values().stream()
            .filter(packet -> !packet.refunded && packet.expired()).toList();
        if (expired.isEmpty()) return;
        List<Delivery> refunds = new ArrayList<>();
        Map<String, List<ItemStack>> previousItems = new LinkedHashMap<>();
        for (BaseRedPacket packet : expired) {
            previousItems.put(packet.id, packet.shareItems);
            List<ItemStack> left = packet.shareItems();
            if (!left.isEmpty()) refunds.add(new Delivery(UUID.randomUUID(), packet.senderId, left, true, packet.id));
            packet.shareItems = List.of();
            packet.refunded = true;
        }
        deliveries.addAll(refunds);
        try {
            save();
        } catch (IOException error) {
            expired.forEach(packet -> {
                packet.refunded = false;
                packet.shareItems = previousItems.get(packet.id);
            });
            deliveries.removeAll(refunds);
            throw error;
        }
        for (Delivery refund : refunds) {
            ServerPlayer owner = server.getPlayerList().getPlayer(refund.owner);
            if (owner != null && !(owner instanceof EntityPlayerMPFake)) owner.sendSystemMessage(tr("refund_pending"));
        }
    }

    public List<PendingReward> pendingRewards(UUID owner) {
        return deliveries.stream().filter(delivery -> delivery.owner.equals(owner))
            .map(delivery -> new PendingReward(delivery.id, delivery.items, delivery.refund)).toList();
    }

    /**
     * Collects only this player's claimed reward from the specified packet.
     */
    public static int collectClaim(ServerPlayer player, String packetId) {
        if (!CarpetPrimaryuanSettings.redPacket || player instanceof EntityPlayerMPFake) {
            player.sendSystemMessage(tr("denied"));
            return 0;
        }
        try {
            RedPacketManager manager = get(player.level().getServer());
            Delivery delivery = manager.deliveries.stream()
                .filter(item -> !item.refund && item.owner.equals(player.getUUID())
                    && packetId != null && packetId.equals(item.packetId))
                .findFirst().orElse(null);
            if (delivery == null) {
                player.sendSystemMessage(tr("reward_missing"));
                return 0;
            }
            return collect(player, delivery.id);
        } catch (IOException error) {
            storageError(player, error);
            return 0;
        }
    }

    public static int collect(ServerPlayer player, UUID deliveryId) {
        if (!CarpetPrimaryuanSettings.redPacket || player instanceof EntityPlayerMPFake) {
            player.sendSystemMessage(tr("denied"));
            return 0;
        }
        try {
            RedPacketManager manager = get(player.level().getServer());
            Delivery delivery = manager.deliveries.stream()
                .filter(item -> item.id.equals(deliveryId) && item.owner.equals(player.getUUID()))
                .findFirst().orElse(null);
            if (delivery == null) {
                player.sendSystemMessage(tr("reward_missing"));
                return 0;
            }
            CollectionPlan plan = planCollection(player.getInventory(), delivery.items);
            if (plan.moved == 0) {
                player.sendSystemMessage(tr("inventory_full"));
                return 0;
            }
            int index = manager.deliveries.indexOf(delivery);
            if (plan.remaining.isEmpty()) manager.deliveries.remove(index);
            else manager.deliveries.set(index, new Delivery(delivery.id, delivery.owner, plan.remaining,
                delivery.refund, delivery.packetId));
            try {
                manager.save();
            } catch (IOException error) {
                if (plan.remaining.isEmpty()) manager.deliveries.add(index, delivery);
                else manager.deliveries.set(index, delivery);
                throw error;
            }
            // All changes run on the server thread. Commit only after the durable queue update succeeds.
            for (int slot = 0; slot < plan.slots.size(); slot++) {
                player.getInventory().setItem(slot, plan.slots.get(slot));
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            player.sendSystemMessage(tr("collected"));
            return 1;
        } catch (IOException error) {
            storageError(player, error);
            return 0;
        }
    }

    /**
     * Plans a drop-free transfer into main inventory slots, leaving both inputs untouched.
     */
    static CollectionPlan planCollection(Container inventory, List<ItemStack> rewards) {
        List<ItemStack> slots = new ArrayList<>();
        for (int slot = 0; slot < Math.min(36, inventory.getContainerSize()); slot++) {
            slots.add(inventory.getItem(slot).copy());
        }
        List<ItemStack> remaining = new ArrayList<>();
        int moved = 0;
        for (ItemStack reward : rewards) {
            int left = reward.getCount();
            for (ItemStack target : slots) {
                if (left == 0) break;
                if (target.isEmpty() || !ItemStack.isSameItemSameComponents(target, reward)) continue;
                int count = Math.min(left, Math.max(0, Math.min(target.getMaxStackSize(), inventory.getMaxStackSize())
                    - target.getCount()));
                target.grow(count);
                left -= count;
            }
            for (int slot = 0; slot < slots.size() && left > 0; slot++) {
                if (!slots.get(slot).isEmpty()) continue;
                int count = Math.min(left, Math.min(reward.getMaxStackSize(), inventory.getMaxStackSize()));
                slots.set(slot, reward.copyWithCount(count));
                left -= count;
            }
            moved += reward.getCount() - left;
            if (left > 0) remaining.add(reward.copyWithCount(left));
        }
        return new CollectionPlan(List.copyOf(slots), List.copyOf(remaining), moved);
    }

    private void broadcast(RedPacket packet) {
        String command = "/redpacket claim " + packet.id();
        Component hover = tr("broadcast_hover", packet.redPacketAmount(),
            packet.durationUnit().format(packet.durationAmount()));
        //#if MC < 12105
        //$$ Style style = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
        //$$         .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        //#else
        Style style = Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command))
            .withHoverEvent(new HoverEvent.ShowText(hover));
        //#endif
        Component message = tr("broadcast", packet.senderName()).copy().append(" ")
            .append(tr("open").copy().withStyle(style.withColor(ChatFormatting.RED).withBold(true)));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player instanceof EntityPlayerMPFake) && !muted.contains(player.getUUID()))
                player.sendSystemMessage(message);
        }
    }

    private void broadcastCompletion(RedPacket packet) {
        String command = "/redpacket results id " + packet.id();
        Component hover = completionHover(packet);
        //#if MC < 12105
        //$$ Style style = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
        //$$     .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        //#else
        Style style = Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command))
            .withHoverEvent(new HoverEvent.ShowText(hover));
        //#endif
        Component message = tr("completed", packet.senderName()).copy().append(" ")
            .append(tr("view_results").copy().withStyle(style.withColor(ChatFormatting.GOLD).withBold(true)));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player instanceof EntityPlayerMPFake) && !muted.contains(player.getUUID())) {
                player.sendSystemMessage(message);
            }
        }
    }

    /**
     * A bounded preview; the linked results screen retains every recipient and reward.
     */
    private static Component completionHover(RedPacket packet) {
        MutableComponent hover = tr("completed_hover", packet.redPacketAmount()).copy();
        int shown = 0;
        boolean truncated = false;
        for (int share = 0; share < packet.redPacketAmount(); share++) {
            if (packet.claimedBy(share) == null) continue;
            if (shown++ >= 8) {
                truncated = true;
                break;
            }
            hover.append("\n").append(Component.literal(packet.claimantName(share) + ": ")
                .withStyle(ChatFormatting.GOLD));
            List<ItemStack> kinds = new ArrayList<>();
            List<Integer> amounts = new ArrayList<>();
            for (ItemStack item : packet.claimedItems(share)) {
                int matching = -1;
                for (int i = 0; i < kinds.size(); i++) {
                    if (ItemStack.isSameItemSameComponents(kinds.get(i), item)) {
                        matching = i;
                        break;
                    }
                }
                if (matching < 0) {
                    kinds.add(item.copyWithCount(1));
                    amounts.add(item.getCount());
                } else amounts.set(matching, amounts.get(matching) + item.getCount());
            }
            if (kinds.isEmpty()) hover.append(tr("completed_unknown"));
            for (int i = 0; i < Math.min(3, kinds.size()); i++) {
                if (i > 0) hover.append(", ");
                hover.append(kinds.get(i).getHoverName()).append(" × " + amounts.get(i));
            }
            if (kinds.size() > 3) {
                hover.append(" …");
                truncated = true;
            }
        }
        if (truncated) hover.append("\n").append(tr("completed_more"));
        return hover;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        //#if MC >= 260300
        //$$ player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.PREDICTED);
        //#else
        player.getInventory().placeItemBackInInventory(stack);
        //#endif
    }

    public Set<String> packetIds() {
        return packets.values().stream().filter(packet -> !packet.expired())
            .map(BaseRedPacket::id).collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> resultIds() {
        return Set.copyOf(packets.keySet());
    }

    public static RedPacket find(ServerPlayer player, String id) {
        if (!CarpetPrimaryuanSettings.redPacket || player instanceof EntityPlayerMPFake) {
            player.sendSystemMessage(tr("denied"));
            return null;
        }
        try {
            RedPacket packet = get(player.level().getServer()).packets.get(id);
            if (packet == null) player.sendSystemMessage(tr("expired"));
            return packet;
        } catch (IOException error) {
            storageError(player, error);
            return null;
        }
    }

    private static void storageError(ServerPlayer player, IOException error) {
        LOGGER.error("[RedPacket] Storage operation failed; packet state was not reset", error);
        player.sendSystemMessage(tr("storage_error"));
    }

    private static Component tr(String key, Object... args) {
        return ServerI18n.tr(REDPACKET_KEY + key, args);
    }
}

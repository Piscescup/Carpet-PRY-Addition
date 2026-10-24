package me.primaryuan.carpet.feature.redpacket;

import carpet.patches.EntityPlayerMPFake;
import com.google.gson.annotations.Expose;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.feature.redpacket.gui.GuiItems;
import me.primaryuan.carpet.feature.redpacket.gui.GuiSession;
import me.primaryuan.carpet.feature.redpacket.gui.RedPacketClaimGui;
import me.primaryuan.carpet.feature.redpacket.gui.TextInputGui;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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

import static me.primaryuan.carpet.CarpetPrimaryuanServer.LOGGER;


public final class RedPacketManager {
    static final long LIFETIME_MILLIS = 180_000;
    private static final Map<MinecraftServer, RedPacketManager> SERVERS = new IdentityHashMap<>();
    private static boolean initialized;
    private final MinecraftServer server;
    private final RedPacketStore store;
    final Map<String, BaseRedPacket> packets = new LinkedHashMap<>();
    final List<Delivery> deliveries = new ArrayList<>();
    final Set<UUID> muted = new HashSet<>();
    private final Set<UUID> delivered = new HashSet<>();
    private int ticks;

    public static final String REDPACKET_KEY = "carpetprimaryuan.redpacket.runtime.";

    record Delivery(@Expose UUID id, @Expose UUID owner, @Expose List<ItemStack> items, @Expose boolean refund) {
        Delivery {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(owner, "owner");
            items = items.stream().map(ItemStack::copy).toList();
            if (items.isEmpty() || items.stream().anyMatch(stack -> stack.isEmpty()
                || stack.getCount() > stack.getMaxStackSize())) throw new IllegalArgumentException("Invalid delivery items");
        }
    }

    private record SavedData(@Expose List<BaseRedPacket> packets,
                             @Expose List<Delivery> deliveries,
                             @Expose Set<UUID> muted) {}

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
                if (packets.putIfAbsent(packet.id(), packet) != null) throw new IllegalArgumentException("Duplicate packet id");
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
                get(server).deliver(handler.player);
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
            taken = session.takeItems();
            created.expiresAtMillis = System.currentTimeMillis() + LIFETIME_MILLIS;
            created.bind(manager.server);
            manager.packets.put(created.id, created);
            try {
                manager.save();
            } catch (IOException error) {
                manager.packets.remove(created.id);
                throw error;
            }
            taken = null;
            player.sendSystemMessage(tr("published", packet.redPacketAmount()));
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
        if (packet == null || packet.expired()) {
            player.sendSystemMessage(tr("expired"));
            return 0;
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
            List<ItemStack> items = packet.takeShare(player.getUUID(), player.getName().getString(), index);
            Delivery delivery = new Delivery(UUID.randomUUID(), player.getUUID(), items, false);
            manager.deliveries.add(delivery);
            try {
                manager.save();
            } catch (IOException error) {
                packet.shareItems = before;
                packet.claimants.remove(index);
                packet.claimantNames.remove(player.getUUID());
                manager.deliveries.remove(delivery);
                throw error;
            }
            try {
                manager.deliver(player);
            } catch (IOException error) {
                storageError(player, error);
            }
            ServerPlayer owner = packet.sender();
            if (owner != null && !manager.muted.contains(owner.getUUID())) {
                owner.sendSystemMessage(tr("receipt", player.getName().getString(), packet.congratulations()));
            }
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
            for (ServerPlayer player : server.getPlayerList().getPlayers()) deliver(player);
        } catch (IOException error) {
            LOGGER.error("[RedPacket] Failed to save expiry/refund state", error);
        }
    }

    private void expire() throws IOException {
        List<BaseRedPacket> expired = packets.values().stream().filter(BaseRedPacket::expired).toList();
        if (expired.isEmpty()) return;
        List<Delivery> refunds = new ArrayList<>();
        Set<String> previouslyRefunded = new HashSet<>();
        for (BaseRedPacket packet : expired) {
            if (packet.refunded) previouslyRefunded.add(packet.id);
            else {
                List<ItemStack> left = packet.shareItems();
                if (!left.isEmpty()) refunds.add(new Delivery(UUID.randomUUID(), packet.senderId, left, true));
            }
            packet.refunded = true;
            packets.remove(packet.id);
        }
        deliveries.addAll(refunds);
        try {
            save();
        } catch (IOException error) {
            expired.forEach(packet -> {
                packet.refunded = previouslyRefunded.contains(packet.id);
                packets.put(packet.id, packet);
            });
            deliveries.removeAll(refunds);
            throw error;
        }
    }

    private void deliver(ServerPlayer player) throws IOException {
        if (player instanceof EntityPlayerMPFake) return;
        for (Delivery delivery : deliveries.stream().filter(item -> item.owner.equals(player.getUUID())).toList()) {
            if (!delivered.contains(delivery.id)) {
                for (ItemStack stack : delivery.items) give(player, stack.copy());
                delivered.add(delivery.id);
                if (delivery.refund) player.sendSystemMessage(tr("refunded"));
            }
            int index = deliveries.indexOf(delivery);
            deliveries.remove(index);
            try {
                save();
            } catch (IOException error) {
                deliveries.add(index, delivery);
                throw error;
            }
            delivered.remove(delivery.id);
        }
    }

    private void broadcast(RedPacket packet) {
        String command = "/redpacket claim " + packet.id();
        Component hover = tr("broadcast_hover", packet.redPacketAmount());
        //#if MC < 12105
        //$$ Style style = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
        //$$         .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        //#else
        Style style = Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command))
            .withHoverEvent(new HoverEvent.ShowText(hover));
        //#endif
        Component message = tr("broadcast", packet.senderName(), packet.congratulations())
            .copy().withStyle(style.withColor(ChatFormatting.RED));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player instanceof EntityPlayerMPFake) && !muted.contains(player.getUUID()))
                player.sendSystemMessage(message);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        //#if MC >= 260300
        //$$ player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.PREDICTED);
        //#else
        player.getInventory().placeItemBackInInventory(stack);
        //#endif
    }

    public Set<String> packetIds() {
        return this.packets.keySet();
    }

    private static void storageError(ServerPlayer player, IOException error) {
        LOGGER.error("[RedPacket] Storage operation failed; packet state was not reset", error);
        player.sendSystemMessage(tr("storage_error"));
    }

    private static Component tr(String key, Object... args) {
        return ServerI18n.tr(REDPACKET_KEY + key, args);
    }
}

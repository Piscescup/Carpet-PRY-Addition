package me.primaryuan.carpet.feature.redpacket.gui;

import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.feature.redpacket.RedPacket;
import me.primaryuan.carpet.feature.redpacket.LuckyRedPacket;
import me.primaryuan.carpet.feature.redpacket.NormalRedPacket;
import me.primaryuan.carpet.feature.redpacket.PasswordRedPaket;
import me.primaryuan.carpet.feature.redpacket.ExclusiveRedPacket;
import me.primaryuan.carpet.feature.redpacket.RedPacketManager;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static me.primaryuan.carpet.feature.redpacket.gui.GuiItems.head;


public final class RedPacketCreateGui {
    private static final String PREFIX = "carpetprimaryuan.redpacket.gui.";
    private static final int INPUT_SLOTS = 45;

    private final RedPacket.RedPacketType type;
    private final GuiSession session;
    private final ChestGui mainGui;
    private final BiConsumer<RedPacket, GuiSession> onConfirm;
    private final Supplier<List<ServerPlayer>> onlinePlayers;
    private String congratulations;
    private int shares = 1;
    private String password = "";
    private UUID target;
    private String targetName = "";

    public RedPacketCreateGui(ServerPlayer player, RedPacket.RedPacketType type, String congratulations) {
        this(player, type, congratulations, RedPacketManager::publish);
    }

    public RedPacketCreateGui(ServerPlayer player, RedPacket.RedPacketType type, String congratulations,
                              BiConsumer<RedPacket, GuiSession> onConfirm) {
        this(player, type, congratulations, onConfirm,
                () -> player.level().getServer().getPlayerList().getPlayers());
    }

    RedPacketCreateGui(ServerPlayer player, RedPacket.RedPacketType type, String congratulations,
                       BiConsumer<RedPacket, GuiSession> onConfirm,
                       Supplier<List<ServerPlayer>> onlinePlayers) {
        if (!RedPacket.validText(congratulations)) {
            throw new IllegalArgumentException("Invalid congratulations");
        }
        this.type = Objects.requireNonNull(type, "type");
        this.congratulations = congratulations;
        this.onConfirm = Objects.requireNonNull(onConfirm, "onConfirm");
        this.onlinePlayers = Objects.requireNonNull(onlinePlayers, "onlinePlayers");
        this.session = new GuiSession(player, INPUT_SLOTS);
        this.mainGui = new ChestGui(session, 6, tr("title", typeName(type).getString()));
        for (int i = 0; i < INPUT_SLOTS; i++) mainGui.input(i, i);
        mainGui.button(45, icon(Items.BARRIER, "cancel"), click -> session.cancel());
        mainGui.button(53, icon(Items.HOPPER, "clear"), click -> session.clearItems());
        mainGui.icon(51, GuiItems.icon(Items.CHEST, typeName(type), tr("type_help")));
        mainGui.button(52, GuiItems.icon(Items.EMERALD, tr("confirm"),
                tr("confirm_help")), click -> confirm());
        renderSettings();
    }

    public boolean open() {
        return mainGui.open();
    }

    private void renderSettings() {
        boolean exclusive = type == RedPacket.RedPacketType.EXCLUSIVE;
        mainGui.icon(46, ItemStack.EMPTY);
        mainGui.icon(48, ItemStack.EMPTY);
        if (exclusive) {
            mainGui.icon(47, GuiItems.icon(redShulkerBox(), tr("shares", shares), tr("exclusive_count")));
        } else {
            mainGui.button(47, GuiItems.icon(redShulkerBox(), tr("shares", shares),
                    tr("count_help"), tr("count_range", RedPacket.MAX_RED_PACKET_AMOUNTS)),
                    click -> changeShares(click == GuiClick.LEFT ? 1 : -1));
        }
        mainGui.button(49, GuiItems.icon(Items.WRITABLE_BOOK, tr("message"),
                Component.literal(congratulations), tr("edit_help")), click -> editMessage());
        switch (type) {
            case LUCKY, NORMAL -> mainGui.icon(50, icon(Items.BOOK, "no_extra"));
            case PASSWORD -> mainGui.button(50, GuiItems.icon(Items.TRIPWIRE_HOOK, tr("password"),
                    tr(password.isEmpty() ? "not_set" : "set"), tr("edit_help")), click -> editPassword());
            case EXCLUSIVE -> mainGui.button(50, GuiItems.icon(Items.PLAYER_HEAD, tr("target"),
                    target == null ? tr("not_set") : Component.literal(targetName), tr("edit_help")),
                    click -> openTargets(0));
        }
    }

    private void changeShares(int delta) {
        shares = Math.clamp(shares + delta, 1, RedPacket.MAX_RED_PACKET_AMOUNTS);
        ServerPlayer player = session.player();
        if (player.connection != null) {
            player.connection.send(new ClientboundSoundPacket(
                    delta > 0 ? SoundEvents.NOTE_BLOCK_HARP : Holder.direct(SoundEvents.VILLAGER_NO),
                    SoundSource.MASTER, player.getX(), player.getY(), player.getZ(),
                    1.0F, 1.0F, player.getRandom().nextLong()));
        }
        renderSettings();
    }

    private static Item redShulkerBox() {
        //#if MC >= 260200
        //$$ return Items.DYED_SHULKER_BOX.pick(DyeColor.RED);
        //#else
        return Items.RED_SHULKER_BOX;
        //#endif
    }

    private void editMessage() {
        new TextInputGui(session, tr("message"), congratulations, icon(Items.PAPER, "save"),
                this::validateText, text -> {
                    congratulations = text;
                    renderSettings();
                    mainGui.open();
                }).open();
    }

    private void editPassword() {
        new TextInputGui(session, tr("password"), password, icon(Items.PAPER, "save"),
                this::validateText, text -> {
                    password = text;
                    renderSettings();
                    mainGui.open();
                }).open();
    }

    private boolean validateText(String text) {
        if (RedPacket.validText(text)) return true;
        error("bad_text", RedPacket.MAX_TEXT_LENGTH);
        return false;
    }

    private List<ServerPlayer> targets() {
        return onlinePlayers.get().stream()
                .filter(player -> !(player instanceof EntityPlayerMPFake))
                .filter(player -> !player.getUUID().equals(session.player().getUUID()))
                .sorted(Comparator.comparing(player -> player.getName().getString(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void openTargets(int requestedPage) {
        List<ServerPlayer> players = targets();
        int pages = Math.max(1, (players.size() + INPUT_SLOTS - 1) / INPUT_SLOTS);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        ChestGui selector = new ChestGui(session, 6, tr("targets_title", page + 1, pages));
        if (players.isEmpty()) selector.icon(22, icon(Items.BARRIER, "no_targets"));
        for (int i = page * INPUT_SLOTS; i < Math.min(players.size(), (page + 1) * INPUT_SLOTS); i++) {
            ServerPlayer chosen = players.get(i);
            UUID id = chosen.getUUID();
            String name = chosen.getName().getString();
            selector.button(i % INPUT_SLOTS, head(chosen, ".choose_target"), click -> {
                if (targets().stream().noneMatch(online -> online.getUUID().equals(id))) {
                    error("target_offline");
                    openTargets(page);
                    return;
                }
                target = id;
                targetName = name;
                renderSettings();
                mainGui.open();
            });
        }
        if (page > 0) selector.button(45, icon(Items.ARROW, "previous"), click -> openTargets(page - 1));
        selector.button(49, icon(Items.BARRIER, "back"), click -> mainGui.open());
        if (page + 1 < pages) selector.button(53, icon(Items.ARROW, "next"), click -> openTargets(page + 1));
        selector.open();
    }

    private void confirm() {
        List<ItemStack> items = session.contents();
        if (items.isEmpty()) {
            error("empty");
            return;
        }
        long amount = items.stream().mapToLong(ItemStack::getCount).sum();
        if (amount < shares) {
            error("not_enough", amount, shares);
            return;
        }
        if (!validateText(congratulations)) return;
        if (type == RedPacket.RedPacketType.PASSWORD && !validateText(password)) return;
        if (type == RedPacket.RedPacketType.EXCLUSIVE
                && (target == null || targets().stream().noneMatch(player -> player.getUUID().equals(target)))) {
            error(target == null ? "need_target" : "target_offline");
            return;
        }
        RedPacket packet;
        try {
            packet = switch (type) {
                case LUCKY -> new LuckyRedPacket(session.player(), items, shares, congratulations);
                case NORMAL -> new NormalRedPacket(session.player(), items, shares, congratulations);
                case PASSWORD -> new PasswordRedPaket(session.player(), items, shares, congratulations, password);
                case EXCLUSIVE -> new ExclusiveRedPacket(session.player(), items, target, congratulations);
            };
        } catch (IllegalArgumentException invalid) {
            session.player().sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.runtime.invalid_items")
                    .copy().withStyle(ChatFormatting.RED));
            return;
        }
        onConfirm.accept(packet, session);
    }

    private void error(String key, Object... args) {
        session.player().sendSystemMessage(tr(key, args).copy().withStyle(ChatFormatting.RED));
    }

    private static ItemStack icon(Item item, String key) {
        return GuiItems.icon(item, tr(key));
    }

    private static Component typeName(RedPacket.RedPacketType type) {
        return tr("type." + type.name().toLowerCase(Locale.ROOT));
    }

    private static Component tr(String key, Object... args) {
        return ServerI18n.tr(PREFIX + key, args);
    }
}

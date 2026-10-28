package me.primaryuan.carpet.feature.redpacket;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import me.primaryuan.carpet.util.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.random.RandomGenerator;


public abstract class BaseRedPacket implements RedPacket {
    private static final DateTimeFormatter ID_TIME_FORMAT = DateTimeFormatter
        .ofPattern("yyyy/MM/dd_HH:mm:ss", Locale.ROOT)
        .withZone(ZoneId.systemDefault());

    protected ServerPlayer sender;
    @Expose
    protected RedPacketType type;
    @Expose
    @SerializedName("count")
    protected int redPacketAmount;
    protected int receiverAmount;
    private long expireTimeAtTick;
    private MinecraftServer server;

    @Expose
    String id;
    @Expose
    @SerializedName("sender")
    UUID senderId;
    @Expose
    String senderName;
    @Expose
    @SerializedName("message")
    String congratulations;
    @Expose
    @SerializedName("expires")
    long expiresAtMillis;
    @Expose
    private long durationAmount = TimeUnit.DEFAULT_AMOUNT;
    @Expose
    private TimeUnit durationUnit = TimeUnit.DEFAULT;
    @Expose
    @SerializedName("lifetimeMillis")
    private Long legacyLifetimeMillis;
    @Expose
    @SerializedName("items")
    protected List<ItemStack> shareItems;
    @Expose
    Map<Integer, UUID> claimants;
    @Expose
    Map<UUID, String> claimantNames;
    @Expose
    Map<Integer, List<ItemStack>> claimItems = new LinkedHashMap<>();
    @Expose
    boolean refunded;
    @Expose
    boolean completionAnnounced;

    protected BaseRedPacket(RedPacketType type) {
        this.type = type;
    }

    protected BaseRedPacket(ServerPlayer sender, RedPacketType type, List<ItemStack> shareItems,
                            int redPacketAmount, int receiverAmount, long expireTimeAtTick) {
        this(type);
        this.sender = Objects.requireNonNull(sender, "sender");
        this.senderId = sender.getUUID();
        this.senderName = sender.getName().getString();
        this.id = senderName + "_" + UUID.randomUUID() + "_" + ID_TIME_FORMAT.format(Instant.now());
        this.redPacketAmount = redPacketAmount;
        this.receiverAmount = receiverAmount;
        this.expireTimeAtTick = expireTimeAtTick;
        this.congratulations = "Congratulation to Everyone!";
        this.expiresAtMillis = Long.MAX_VALUE;
        this.claimants = new LinkedHashMap<>();
        this.claimantNames = new HashMap<>();
        this.shareItems = shareItems.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
        validateCommon();
    }

    protected BaseRedPacket(ServerPlayer sender, RedPacketType type, List<ItemStack> items,
                            int shares, String congratulations) {
        this(sender, type, items, shares, shares, Long.MAX_VALUE);
        if (!RedPacket.validText(congratulations)) throw new IllegalArgumentException("Invalid congratulations");
        this.congratulations = congratulations;
    }

    void restore(MinecraftServer server) {
        // Older saves recorded the claimant but not the awarded items.
        if (claimItems == null) claimItems = new LinkedHashMap<>();
        restoreLegacyDuration();
        validate();
        // Completed legacy packets have already finished; do not announce them again after a restart.
        if (claimants.size() == redPacketAmount) completionAnnounced = true;
        receiverAmount = redPacketAmount;
        shareItems = shareItems.stream().map(ItemStack::copy).toList();
        bind(server);
    }

    protected void validate() {
        validateCommon();
    }

    private void validateCommon() {
        if (durationUnit == null) throw new IllegalArgumentException("Missing red packet duration unit");
        durationUnit.toMillis(durationAmount);
        if (id == null || id.isBlank() || senderId == null || type == null || senderName == null || senderName.isBlank()
            || !RedPacket.validText(congratulations) || redPacketAmount < 1 || redPacketAmount > MAX_RED_PACKET_AMOUNTS
            || expiresAtMillis <= 0 || shareItems == null || claimants == null || claimantNames == null || claimItems == null
            || shareItems.stream().anyMatch(stack -> stack == null || stack.isEmpty()
                || stack.getCount() > stack.getMaxStackSize())) {
            throw new IllegalArgumentException("Invalid red packet configuration");
        }
        Set<UUID> players = new HashSet<>();
        for (var entry : claimants.entrySet()) {
            if (entry.getKey() == null || entry.getKey() < 0 || entry.getKey() >= redPacketAmount
                || entry.getValue() == null || !players.add(entry.getValue())) {
                throw new IllegalArgumentException("Invalid red packet claimant");
            }
        }
        long items = shareItems.stream().mapToLong(ItemStack::getCount).sum();
        int remainingShares = redPacketAmount - players.size();
        if (!claimantNames.keySet().equals(players)
            || claimantNames.values().stream().anyMatch(name -> name == null || name.isBlank())
            || (completionAnnounced && remainingShares != 0)
            || (refunded ? items != 0 : items < remainingShares || (remainingShares == 0 && items != 0))) {
            throw new IllegalArgumentException("Invalid remaining red packet items or claims");
        }
        for (var entry : claimItems.entrySet()) {
            if (!claimants.containsKey(entry.getKey()) || entry.getValue() == null || entry.getValue().isEmpty()
                || entry.getValue().stream().anyMatch(stack -> stack == null || stack.isEmpty()
                    || stack.getCount() > stack.getMaxStackSize())) {
                throw new IllegalArgumentException("Invalid red packet claim history");
            }
        }
    }

    void bind(MinecraftServer server) {
        this.server = server;
        this.sender = null; // UUID 查询当前玩家，避免红包持有离线玩家实例。
    }

    @Override
    public RedPacketType redPacketType() {
        return type;
    }

    @Override
    public ServerPlayer sender() {
        return server == null ? sender : server.getPlayerList().getPlayer(senderId);
    }

    @Override
    public List<ItemStack> shareItems() {
        return shareItems.stream().map(ItemStack::copy).toList();
    }

    @Override
    public int redPacketAmount() {
        return redPacketAmount;
    }

    @Override
    public int receiverAmount() {
        return receiverAmount;
    }

    @Override
    public long durationAmount() {
        return durationAmount;
    }

    @Override
    public TimeUnit durationUnit() {
        return durationUnit;
    }

    /** Configures the amount and unit together before this packet is published. */
    public void setDuration(long amount, TimeUnit unit) {
        if (unit == null) throw new IllegalArgumentException("Missing red packet duration unit");
        unit.toMillis(amount);
        if (server != null) throw new IllegalStateException("Cannot change a published red packet's lifetime");
        this.durationAmount = amount;
        this.durationUnit = unit;
        this.legacyLifetimeMillis = null;
    }

    private void restoreLegacyDuration() {
        if (legacyLifetimeMillis == null) return;
        long millis = legacyLifetimeMillis;
        if (millis <= 0 || millis > TimeUnit.MAX_MILLIS) {
            throw new IllegalArgumentException("Invalid legacy red packet duration");
        }
        for (TimeUnit unit : TimeUnit.values()) {
            long unitMillis = unit.toMillis(1);
            if (millis % unitMillis != 0) continue;
            durationAmount = millis / unitMillis;
            durationUnit = unit;
            legacyLifetimeMillis = null;
            return;
        }
        throw new IllegalArgumentException("Legacy red packet duration is not a whole time unit");
    }

    @Override
    public long expireTimeAtTick() {
        if (server == null) return expireTimeAtTick;
        long tickMillis = TimeUnit.TICKS.toMillis(1);
        long remaining = Math.max(0, expiresAtMillis - System.currentTimeMillis());
        return server.getTickCount() + remaining / tickMillis + (remaining % tickMillis == 0 ? 0 : 1);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public UUID senderId() {
        return senderId;
    }

    @Override
    public String senderName() {
        return senderName;
    }

    @Override
    public String congratulations() {
        return congratulations;
    }

    @Override
    public UUID claimedBy(int shareIndex) {
        return claimants.get(shareIndex);
    }

    @Override
    public String claimantName(int shareIndex) {
        UUID player = claimedBy(shareIndex);
        return player == null ? null : claimantNames.getOrDefault(player, player.toString());
    }

    @Override
    public List<ItemStack> claimedItems(int shareIndex) {
        return claimItems.getOrDefault(shareIndex, List.of()).stream().map(ItemStack::copy).toList();
    }

    @Override
    public boolean expired() {
        return refunded || System.currentTimeMillis() >= expiresAtMillis;
    }

    @Override
    public ClaimResult claim(ServerPlayer player, int index, boolean passwordVerified) {
        return RedPacketManager.claim(this, player, index, passwordVerified);
    }

    ClaimResult checkClaim(UUID player, int index, boolean passwordVerified) {
        if (expired()) return ClaimResult.EXPIRED;
        if (!mayReceive(player, passwordVerified)) return ClaimResult.DENIED;
        if (claimants.containsValue(player)) return ClaimResult.ALREADY_CLAIMED;
        if (index < 0 || index >= redPacketAmount || claimants.containsKey(index)) return ClaimResult.TAKEN;
        return ClaimResult.CLAIMED;
    }

    protected boolean mayReceive(UUID player, boolean passwordVerified) {
        return true;
    }

    List<ItemStack> takeShare(UUID player, String name, int index) {
        List<ItemStack> remaining = new ArrayList<>(shareItems());
        List<ItemStack> result = splitShare(remaining, redPacketAmount - claimants.size(), RandomGenerator.getDefault());
        remaining.removeIf(ItemStack::isEmpty);
        shareItems = List.copyOf(remaining);
        claimants.put(index, player);
        claimantNames.put(player, name);
        claimItems.put(index, result.stream().map(ItemStack::copy).toList());
        return result;
    }

    protected List<ItemStack> splitShare(List<ItemStack> remaining, int shares, RandomGenerator random) {
        if (shares == 1) {
            List<ItemStack> result = remaining.stream().map(ItemStack::copy).toList();
            remaining.clear();
            return result;
        }
        List<ItemStack> kinds = new ArrayList<>();
        List<Integer> amounts = new ArrayList<>();
        for (ItemStack stack : remaining) {
            int kind = -1;
            for (int i = 0; i < kinds.size(); i++) {
                if (ItemStack.isSameItemSameComponents(kinds.get(i), stack)) { kind = i; break; }
            }
            if (kind < 0) {
                kinds.add(stack.copyWithCount(1));
                amounts.add(stack.getCount());
            } else amounts.set(kind, amounts.get(kind) + stack.getCount());
        }
        int total = amounts.stream().mapToInt(Integer::intValue).sum();
        int amount = total / shares + (random.nextInt(shares) < total % shares ? 1 : 0);
        List<ItemStack> result = new ArrayList<>();
        List<Integer> extras = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) {
            int base = amounts.get(i) / shares;
            take(remaining, kinds.get(i), base, result);
            amount -= base;
            if (amounts.get(i) % shares != 0) extras.add(i);
        }
        while (amount-- > 0) {
            int kind = extras.remove(random.nextInt(extras.size()));
            take(remaining, kinds.get(kind), 1, result);
        }
        return result;
    }

    /** 拼手气按剩余物品随机抽取，保留足够物品让后续每份至少一件。 */
    protected static List<ItemStack> randomShare(List<ItemStack> remaining, int shares, RandomGenerator random) {
        int total = remaining.stream().mapToInt(ItemStack::getCount).sum();
        int amount = shares == 1 ? total
            : random.nextInt(1, Math.clamp(2L * total / shares, 1, total - shares + 1) + 1);
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            int chosen = random.nextInt(total--);
            for (ItemStack stack : remaining) {
                if (chosen < stack.getCount()) {
                    add(result, stack, 1);
                    stack.shrink(1);
                    break;
                }
                chosen -= stack.getCount();
            }
        }
        return result;
    }

    private static void take(List<ItemStack> remaining, ItemStack kind, int amount, List<ItemStack> result) {
        for (ItemStack stack : remaining) {
            if (amount == 0) break;
            if (!ItemStack.isSameItemSameComponents(stack, kind)) continue;
            int moved = Math.min(amount, stack.getCount());
            add(result, stack, moved);
            stack.shrink(moved);
            amount -= moved;
        }
    }

    private static void add(List<ItemStack> share, ItemStack template, int amount) {
        for (ItemStack stack : share) {
            if (!ItemStack.isSameItemSameComponents(stack, template)) continue;
            int moved = Math.min(amount, stack.getMaxStackSize() - stack.getCount());
            stack.grow(moved);
            amount -= moved;
        }
        while (amount > 0) {
            int size = Math.min(amount, template.getMaxStackSize());
            share.add(template.copyWithCount(size));
            amount -= size;
        }
    }

}

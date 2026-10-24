package me.primaryuan.carpet.feature.redpacket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import static me.primaryuan.carpet.CarpetPrimaryuanServer.LOGGER;

final class RedPacketStore {
    private final Path path;
    private final Gson gson;

    RedPacketStore(Path path, HolderLookup.Provider registries) {
        this.path = path;
        DynamicOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .excludeFieldsWithoutExposeAnnotation()
            .disableJdkUnsafe()
            .registerTypeAdapter(ItemStack.class, new TypeAdapter<ItemStack>() {
                @Override
                public void write(JsonWriter writer, ItemStack stack) throws IOException {
                    if (stack == null || stack.isEmpty()) throw new JsonParseException("Empty packet item");
                    gson.toJson(ItemStack.CODEC.encodeStart(ops, stack).getOrThrow(), writer);
                }

                @Override
                public ItemStack read(JsonReader reader) {
                    return ItemStack.CODEC.parse(ops, JsonParser.parseReader(reader)).getOrThrow();
                }
            })
            .registerTypeAdapter(BaseRedPacket.class, new TypeAdapter<BaseRedPacket>() {
                @Override
                public void write(JsonWriter writer, BaseRedPacket packet) throws IOException {
                    gson.toJson(packet, packet.getClass(), writer);
                }

                @Override
                public BaseRedPacket read(JsonReader reader) {
                    JsonElement json = JsonParser.parseReader(reader);
                    Class<? extends BaseRedPacket> type = switch (RedPacket.RedPacketType.valueOf(
                        json.getAsJsonObject().get("type").getAsString())) {
                        case LUCKY -> LuckyRedPacket.class;
                        case NORMAL -> NormalRedPacket.class;
                        case PASSWORD -> PasswordRedPaket.class;
                        case EXCLUSIVE -> ExclusiveRedPacket.class;
                    };
                    return gson.fromJson(json, type);
                }
            }.nullSafe())
            .create();
    }

    <T> T load(Class<T> type) throws IOException {
        if (!Files.exists(path)) return null;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            T result = gson.fromJson(reader, type);
            if (result == null) throw new IOException("Empty red packet storage: " + path);
            return result;
        } catch (RuntimeException error) {
            throw new IOException("Cannot deserialize red packet storage: " + path, error);
        }
    }

    void save(Object data) throws IOException {
        try {
            String json = gson.toJson(data);
            Files.createDirectories(path.getParent());
            Path temporary = Files.createTempFile(path.getParent(), path.getFileName() + ".", ".tmp");
            try {
                Files.writeString(temporary, json, StandardCharsets.UTF_8);
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                try { Files.deleteIfExists(temporary); }
                catch (IOException cleanup) { LOGGER.warn("[RedPacket] Cannot remove temporary save {}", temporary, cleanup); }
            }
        } catch (RuntimeException error) {
            throw new IOException("Cannot serialize red packet storage: " + path, error);
        }
    }
}

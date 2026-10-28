package me.primaryuan.carpet.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.feature.redpacket.RedPacket;
import me.primaryuan.carpet.util.TimeUnit;
import me.primaryuan.carpet.feature.redpacket.RedPacketManager;
import me.primaryuan.carpet.feature.redpacket.gui.RedPacketCreateGui;
import me.primaryuan.carpet.feature.redpacket.gui.RedPacketInboxGui;
import me.primaryuan.carpet.feature.redpacket.gui.RedPacketResultsGui;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import static me.primaryuan.carpet.CarpetPrimaryuanServer.LOGGER;


/**
 * <p>
 *     The redpacket command like QQ.
 * </p>
 *
 * <p>
 *     The command tree:
 * </p>
 *
 * <pre>{@code
 * /redpacket
 *      create <type> [expireTime] [min|s|h|ticks] [congratulations] -> create a packet
 *      claim <id> -> open the packet's claim GUI
 *      inbox -> collect stored rewards and refunds when ready
 *      results id <id> -> see what everyone claimed, including expired packets
 *      mute | unmute -> change personal broadcast subscription
 *
 * }</pre>
 *
 *
 * @author <a href="https://github.com/Piscescup">PiscesCup</a>
 * @since 1.3.1
 */
public final class RedPacketCommands {
    private static final String DEFAULT_GREETING = "Congratulation to Everyone.";
    private static final LiteralArgumentBuilder<CommandSourceStack> CREATE_COMMANDS = createCommands();

    public static final LiteralArgumentBuilder<CommandSourceStack> CLAIM_COMMAND = Commands.literal("claim")
        .then(Commands.argument("id", StringArgumentType.greedyString())
            .suggests((ctx, builder) -> {
                Set<String> packetIds = Set.of();
                try {
                    packetIds = RedPacketManager.get(ctx.getSource().getServer())
                        .packetIds();
                } catch (IOException ignore) {
                }
                packetIds.forEach(builder::suggest);

                return builder.buildFuture();
            })
            .executes(ctx -> RedPacketManager.open(
                    ctx.getSource().getPlayerOrException(),
                    StringArgumentType.getString(ctx, "id")
                )
            )
        );

    public static final LiteralArgumentBuilder<CommandSourceStack> RESULT_COMMAND = Commands.literal("results")
        .then(Commands.literal("page")
            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                .then(Commands.argument("id", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        RedPacket packet = RedPacketManager.find(player,
                            StringArgumentType.getString(ctx, "id"));
                        return packet != null && new RedPacketResultsGui(player, packet,
                            IntegerArgumentType.getInteger(ctx, "page") - 1).open() ? 1 : 0;
                    })
                )
            )
        )
        .then(Commands.literal("id")
            .then(Commands.argument("id", StringArgumentType.greedyString())
                .suggests((ctx, builder) -> {
                    try {
                        RedPacketManager.get(ctx.getSource().getServer()).resultIds().stream()
                            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                            .forEach(builder::suggest);
                    } catch (IOException ignore) {
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    RedPacket packet = RedPacketManager.find(player, StringArgumentType.getString(ctx, "id"));
                    return packet != null && new RedPacketResultsGui(player, packet).open() ? 1 : 0;
                })
            )
        );

    private static LiteralArgumentBuilder<CommandSourceStack> createCommands() {
        Command<CommandSourceStack> defaults = ctx -> create(ctx, TimeUnit.DEFAULT);
        var expiry = Commands.argument("expireTime", LongArgumentType.longArg())
            .executes(defaults)
            .then(congratulations(defaults));
        for (TimeUnit unit : TimeUnit.values()) {
            Command<CommandSourceStack> action = ctx -> create(ctx, unit);
            expiry.then(Commands.literal(unit.token())
                .executes(action)
                .then(congratulations(action)));
        }
        return Commands.literal("create")
            .then(Commands.argument("type", StringArgumentType.word())
                .suggests((c, b) -> {
                    Arrays.stream(RedPacket.RedPacketType.values())
                        .map(Enum::name)
                        .map(name -> name.toLowerCase(Locale.ROOT))
                        .filter(name -> name.startsWith(b.getRemainingLowerCase()))
                        .forEach(b::suggest);

                    return b.buildFuture();
                })
                .executes(defaults)
                .then(expiry)
                .then(congratulations(defaults))
            );
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> congratulations(
        Command<CommandSourceStack> action
    ) {
        return Commands.argument("congratulations", StringArgumentType.greedyString()).executes(action);
    }

    private static int create(CommandContext<CommandSourceStack> ctx, TimeUnit unit) throws CommandSyntaxException {
        long amount = argumentOrDefault(ctx, "expireTime", Long.class, TimeUnit.DEFAULT_AMOUNT);
        String greeting = argumentOrDefault(ctx, "congratulations", String.class, DEFAULT_GREETING);
        try {
            unit.toMillis(amount);
        } catch (IllegalArgumentException invalid) {
            throw invalidExpiry();
        }
        return createGuiOf(ctx.getSource().getPlayerOrException(),
            parseType(StringArgumentType.getString(ctx, "type")), greeting, amount, unit);
    }

    private static <T> T argumentOrDefault(CommandContext<CommandSourceStack> ctx,
                                          String name, Class<T> type, T fallback) {
        try {
            return ctx.getArgument(name, type);
        } catch (IllegalArgumentException missing) {
            return fallback;
        }
    }

    private static CommandSyntaxException invalidExpiry() {
        return new SimpleCommandExceptionType(ServerI18n.tr(
            "carpetprimaryuan.redpacket.gui.bad_expire_time")).create();
    }

    private RedPacketCommands() {
        // Don't allow to create an instance of a util class
    }

    public static void register() {
        RedPacketManager.init();

        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) ->
                dispatcher.register( Commands.literal("redpacket")
                    .requires(ignored -> CarpetPrimaryuanSettings.redPacket)
                    .then(CREATE_COMMANDS)
                    .then(CLAIM_COMMAND)
                    .then(Commands.literal("inbox")
                        .executes(ctx -> openInbox(ctx.getSource().getPlayerOrException())))
                    .then(RESULT_COMMAND)
                    .then(Commands.literal("mute")
                        .executes(ctx ->
                            RedPacketManager.mute(ctx.getSource().getPlayerOrException(), true)
                        )
                    )
                    .then(Commands.literal("unmute")
                        .executes(ctx ->
                            RedPacketManager.mute(ctx.getSource().getPlayerOrException(), false)
                        )
                    )
                )
        );
    }

    public static int openInbox(ServerPlayer player) {
        try {
            return new RedPacketInboxGui(player).open() ? Command.SINGLE_SUCCESS : 0;
        } catch (IOException error) {
            LOGGER.error("[RedPacket] Cannot open reward inbox", error);
            player.sendSystemMessage(ServerI18n.tr(RedPacketManager.REDPACKET_KEY + "storage_error"));
            return 0;
        }
    }

    private static int createGuiOf(
        ServerPlayer player,
        RedPacket.RedPacketType type,
        String congratulations,
        long durationAmount,
        TimeUnit durationUnit
    ) throws CommandSyntaxException {
        if (!RedPacket.validText(congratulations)) {
            throw new SimpleCommandExceptionType(ServerI18n.tr(
                    "carpetprimaryuan.redpacket.gui.bad_text", RedPacket.MAX_TEXT_LENGTH)).create();
        }
        return new RedPacketCreateGui(player, type, congratulations, durationAmount, durationUnit).open()
            ? Command.SINGLE_SUCCESS : 0;
    }

    private static RedPacket.RedPacketType parseType(String value) throws CommandSyntaxException {
        try {
            return RedPacket.RedPacketType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new SimpleCommandExceptionType(ServerI18n.tr(
                    "carpetprimaryuan.redpacket.gui.bad_type", value)).create();
        }
    }
}

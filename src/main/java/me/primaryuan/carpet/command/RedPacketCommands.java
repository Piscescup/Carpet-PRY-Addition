package me.primaryuan.carpet.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.feature.redpacket.RedPacket;
import me.primaryuan.carpet.feature.redpacket.RedPacketManager;
import me.primaryuan.carpet.feature.redpacket.gui.RedPacketCreateGui;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;


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
 *      create <type> [congratulations] -> open a GUI to create a red packet
 *      claim <id> -> open the packet's claim GUI
 *      mute | unmute -> change personal broadcast subscription
 *
 * }</pre>
 *
 *
 * @author <a href="https://github.com/Piscescup">PiscesCup</a>
 * @since 1.3.1
 */
public final class RedPacketCommands {
    private static final LiteralArgumentBuilder<CommandSourceStack> CREATE_COMMANDS = Commands.literal("create")
        .then(Commands.argument("type", StringArgumentType.word())
            .suggests((c, b) -> {
                Arrays.stream(RedPacket.RedPacketType.values())
                    .map(Enum::name)
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .filter(name -> name.startsWith(b.getRemainingLowerCase()))
                    .forEach(b::suggest);

                return b.buildFuture();
            })
            .executes(ctx -> createGuiOf(
                    ctx.getSource().getPlayerOrException(),
                    parseType(StringArgumentType.getString(ctx, "type")),
                    "Congratulation to Everyone."
                )
            )
            .then( Commands.argument("congratulations", StringArgumentType.greedyString())
                .executes(ctx -> createGuiOf(
                        ctx.getSource().getPlayerOrException(),
                        parseType(StringArgumentType.getString(ctx, "type")),
                        StringArgumentType.getString(ctx, "congratulations")
                    )
                )
            )
        );

    private RedPacketCommands() {
        // Don't allow to create a util class
    }

    public static void register() {
        RedPacketManager.init();
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) ->
                dispatcher.register( Commands.literal("redpacket")
                    .requires(ignored -> CarpetPrimaryuanSettings.redPacket)
                    .then(CREATE_COMMANDS)
                    .then(Commands.literal("claim")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> {
                                Set<String> packetIds = Set.of();
                                try {
                                    packetIds = RedPacketManager.get(ctx.getSource().getServer())
                                        .packetIds();
                                } catch (IOException ignore) {}
                                packetIds.forEach(builder::suggest);

                                return builder.buildFuture();
                            })
                            .executes(ctx -> RedPacketManager.open(ctx.getSource().getPlayerOrException(),
                                StringArgumentType.getString(ctx, "id")))))
                    .then(Commands.literal("mute")
                        .executes(ctx -> RedPacketManager.mute(ctx.getSource().getPlayerOrException(), true)))
                    .then(Commands.literal("unmute")
                        .executes(ctx -> RedPacketManager.mute(ctx.getSource().getPlayerOrException(), false)))
                )
        );
    }

    private static int createGuiOf(
        ServerPlayer player,
        RedPacket.RedPacketType type,
        String congratulations
    ) throws CommandSyntaxException {
        if (!RedPacket.validText(congratulations)) {
            throw new SimpleCommandExceptionType(ServerI18n.tr(
                    "carpetprimaryuan.redpacket.gui.bad_text", RedPacket.MAX_TEXT_LENGTH)).create();
        }
        return new RedPacketCreateGui(player, type, congratulations).open() ? Command.SINGLE_SUCCESS : 0;
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

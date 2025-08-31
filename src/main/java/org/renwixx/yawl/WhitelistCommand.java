package org.renwixx.yawl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;

import java.util.List;
import java.util.StringJoiner;

public final class WhitelistCommand {

    public static BrigadierCommand create(final Yawl plugin) {
        LiteralArgumentBuilder<CommandSource> builder = LiteralArgumentBuilder.<CommandSource>literal("yawl")
                .executes(context -> {
                    context.getSource().sendMessage(plugin.getLocaleManager().getMessage("help-message"));
                    return Command.SINGLE_SUCCESS;
                });

        var addCommand = LiteralArgumentBuilder.<CommandSource>literal("add")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                        .executes(context -> {
                            CommandSource source = context.getSource();
                            LocaleManager locale = plugin.getLocaleManager();
                            if (!source.hasPermission(Permissions.ADD)) {
                                source.sendMessage(locale.getMessageFor(source, "no-permission"));
                                return Command.SINGLE_SUCCESS;
                            }

                            String playerName = context.getArgument("player", String.class).trim();
                            if (plugin.addPlayer(playerName)) {
                                sendMessageToSource(source, locale.getMessageFor(source, "player-added",
                                                Placeholder.unparsed("player", playerName)), plugin);
                            } else {
                                sendMessageToSource(source, locale.getMessageFor(source, "player-already-exists",
                                                Placeholder.unparsed("player", playerName)), plugin);
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                );

        var removeCommand = LiteralArgumentBuilder.<CommandSource>literal("remove")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                        .suggests((context, suggestionBuilder) -> {
                            List<String> players = plugin.getWhitelistedPlayers();
                            String input = suggestionBuilder.getRemaining();
                            players.stream()
                                    .filter(name -> plugin.getConfig().isCaseSensitive()
                                            ? name.startsWith(input)
                                            : name.toLowerCase().startsWith(input.toLowerCase()))
                                    .forEach(suggestionBuilder::suggest);
                            return suggestionBuilder.buildFuture();
                        })
                        .executes(context -> {
                            CommandSource source = context.getSource();
                            LocaleManager locale = plugin.getLocaleManager();
                            if (!source.hasPermission(Permissions.REMOVE)) {
                                source.sendMessage(locale.getMessageFor(source, "no-permission"));
                                return Command.SINGLE_SUCCESS;
                            }

                            String playerName = context.getArgument("player", String.class).trim();

                            if (!(source instanceof ConsoleCommandSource)
                                    && source instanceof com.velocitypowered.api.proxy.Player player
                                    && player.getUsername().equalsIgnoreCase(playerName)) {
                                sendMessageToSource(source, locale.getMessageFor(source, "cannot-remove-self"), plugin);
                                return Command.SINGLE_SUCCESS;
                            }

                            if (plugin.removePlayer(playerName)) {
                                sendMessageToSource(source, locale.getMessageFor(source, "player-removed",
                                        Placeholder.unparsed("player", playerName)), plugin);
                            } else {
                                sendMessageToSource(source, locale.getMessageFor(source, "player-not-found",
                                                Placeholder.unparsed("player", playerName)), plugin);
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                );

        var listCommand = LiteralArgumentBuilder.<CommandSource>literal("list")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    LocaleManager locale = plugin.getLocaleManager();
                    if (!source.hasPermission(Permissions.LIST)) {
                        source.sendMessage(locale.getMessageFor(source, "no-permission"));
                        return Command.SINGLE_SUCCESS;
                    }

                    List<String> players = plugin.getWhitelistedPlayers();
                    if (players.isEmpty()) {
                        sendMessageToSource(source, locale.getMessageFor(source, "list-empty"), plugin);
                    } else {
                        StringJoiner joiner = new StringJoiner(", ");
                        players.forEach(joiner::add);
                        sendMessageToSource(source, locale.getMessageFor(source, "list-header",
                                Placeholder.unparsed("count", String.valueOf(players.size())),
                                Placeholder.unparsed("players", joiner.toString())), plugin);
                    }
                    return Command.SINGLE_SUCCESS;
                });

        var reloadCommand = LiteralArgumentBuilder.<CommandSource>literal("reload")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    if (!source.hasPermission(Permissions.RELOAD)) {
                        source.sendMessage(plugin.getLocaleManager().getMessageFor(source, "no-permission"));
                        return Command.SINGLE_SUCCESS;
                    }
                    plugin.reload();
                    sendMessageToSource(source, plugin.getLocaleManager().getMessageFor(source, "reload-success"), plugin);
                    return Command.SINGLE_SUCCESS;
                });

        builder.then(addCommand)
                .then(removeCommand)
                .then(listCommand)
                .then(reloadCommand);

        return new BrigadierCommand(builder);
    }

    private static void sendMessageToSource(CommandSource source, Component message, Yawl plugin) {
        if (source instanceof ConsoleCommandSource) {
            Logger logger = plugin.getLogger();
            String plain = PlainTextComponentSerializer.plainText().serialize(message);
            logger.info(plain);
        } else {
            source.sendMessage(message);
        }
    }
}
package org.renwixx.yawl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.List;
import java.util.StringJoiner;

public final class WhitelistCommand {

    public static BrigadierCommand create(final Yawl plugin) {
        final MiniMessage mm = plugin.getMiniMessage();
        final PluginConfig.Messages messages = plugin.getConfig().getMessages();

        LiteralArgumentBuilder<CommandSource> builder = LiteralArgumentBuilder.<CommandSource>literal("yawl")
                .requires(source -> source.hasPermission(Permissions.ADD)
                        || source.hasPermission(Permissions.REMOVE)
                        || source.hasPermission(Permissions.LIST))

                .then(LiteralArgumentBuilder.<CommandSource>literal("add")
                        .requires(source -> source.hasPermission(Permissions.ADD))
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                                .executes(context -> {
                                    String playerName = context.getArgument("player", String.class);
                                    if (plugin.addPlayer(playerName)) {
                                        context.getSource().sendMessage(mm.deserialize(messages.getPlayerAdded(),
                                                Placeholder.unparsed("player", playerName)));
                                    } else {
                                        context.getSource().sendMessage(mm.deserialize(messages.getPlayerAlreadyExists(),
                                                Placeholder.unparsed("player", playerName)));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )

                .then(LiteralArgumentBuilder.<CommandSource>literal("remove")
                        .requires(source -> source.hasPermission(Permissions.REMOVE))
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                                .executes(context -> {
                                    String playerName = context.getArgument("player", String.class);
                                    if (plugin.removePlayer(playerName)) {
                                        context.getSource().sendMessage(mm.deserialize(messages.getPlayerRemoved(),
                                                Placeholder.unparsed("player", playerName)));
                                    } else {
                                        context.getSource().sendMessage(mm.deserialize(messages.getPlayerNotFound(),
                                                Placeholder.unparsed("player", playerName)));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )

                .then(LiteralArgumentBuilder.<CommandSource>literal("list")
                        .requires(source -> source.hasPermission(Permissions.LIST))
                        .executes(context -> {
                            List<String> players = plugin.getWhitelistedPlayers();
                            if (players.isEmpty()) {
                                context.getSource().sendMessage(mm.deserialize(messages.getListEmpty()));
                            } else {
                                StringJoiner joiner = new StringJoiner(", ");
                                players.forEach(joiner::add);
                                context.getSource().sendMessage(mm.deserialize(messages.getListHeader(),
                                        Placeholder.unparsed("count", String.valueOf(players.size())),
                                        Placeholder.unparsed("players", joiner.toString())));
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                );

        return new BrigadierCommand(builder);
    }
}
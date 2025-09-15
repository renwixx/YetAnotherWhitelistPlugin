package org.renwixx.yawl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.renwixx.yawl.util.DurationParser;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

public final class WhitelistCommand {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    public static BrigadierCommand create(final Yawl plugin, final VelocityToBackendBridge bridge) {
        LiteralArgumentBuilder<CommandSource> builder = LiteralArgumentBuilder.<CommandSource>literal("yawl")
                .executes(context -> {
                    context.getSource().sendMessage(plugin.getLocaleManager().getMessage("help-message"));
                    return Command.SINGLE_SUCCESS;
                });

        var addCommand = LiteralArgumentBuilder.<CommandSource>literal("add")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("duration", StringArgumentType.greedyString())
                                .suggests((ctx, sb) -> {
                                    sb.suggest("7d");
                                    sb.suggest("30d");
                                    sb.suggest("1mo");
                                    sb.suggest("1y");
                                    return sb.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSource source = context.getSource();
                                    LocaleManager locale = plugin.getLocaleManager();
                                    if (!source.hasPermission(Permissions.ADD)) {
                                        source.sendMessage(locale.getMessageFor(source, "no-permission"));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String playerName = context.getArgument("player", String.class).trim();
                                    String durationStr = context.getArgument("duration", String.class).trim();
                                    var parsed = DurationParser.parse(durationStr);
                                    if (parsed.isEmpty()) {
                                        sendMessageToSource(source, locale.getMessageFor(source, "invalid-duration",
                                                Placeholder.unparsed("duration", durationStr)), plugin);
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Duration dur = parsed.get();
                                    boolean added = plugin.addPlayer(playerName, dur);
                                    if (added) {
                                        String until = DATE_FMT.format(Instant.now().plus(dur));
                                        sendMessageToSource(source, locale.getMessageFor(source, "player-added-temp",
                                                Placeholder.unparsed("player", playerName),
                                                Placeholder.unparsed("until", until)), plugin);
                                        plugin.getServer().getPlayer(playerName).ifPresent(bridge::sendWhitelistUpdate);
                                    } else {
                                        sendMessageToSource(source, locale.getMessageFor(source, "player-already-exists",
                                                Placeholder.unparsed("player", playerName)), plugin);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                }))
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
                                plugin.getServer().getPlayer(playerName).ifPresent(bridge::sendWhitelistUpdate);
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
                                plugin.getServer().getPlayer(playerName).ifPresent(bridge::sendWhitelistUpdate);
                            } else {
                                sendMessageToSource(source, locale.getMessageFor(source, "player-not-found",
                                                Placeholder.unparsed("player", playerName)), plugin);
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                );

        var extendCommand = LiteralArgumentBuilder.<CommandSource>literal("extend")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("duration", StringArgumentType.word())
                                .suggests((ctx, sb) -> {
                                    sb.suggest("7d");
                                    sb.suggest("30d");
                                    sb.suggest("1mo");
                                    sb.suggest("1y");
                                    return sb.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSource source = context.getSource();
                                    LocaleManager locale = plugin.getLocaleManager();
                                    if (!source.hasPermission(Permissions.EXTEND)) {
                                        source.sendMessage(locale.getMessageFor(source, "no-permission"));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String playerName = context.getArgument("player", String.class).trim();
                                    String durationStr = context.getArgument("duration", String.class).trim();
                                    var parsed = DurationParser.parse(durationStr);
                                    if (parsed.isEmpty()) {
                                        sendMessageToSource(source, locale.getMessageFor(source, "invalid-duration",
                                                Placeholder.unparsed("duration", durationStr)), plugin);
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Duration dur = parsed.get();

                                    Optional<org.renwixx.yawl.storage.WhitelistEntry> existing = plugin.getEntry(playerName);

                                    if (existing.isEmpty()) {
                                        boolean added = plugin.addPlayer(playerName, dur);
                                        if (added) {
                                            String until = DATE_FMT.format(Instant.now().plus(dur));
                                            sendMessageToSource(source, locale.getMessageFor(source, "player-added-temp",
                                                    Placeholder.unparsed("player", playerName),
                                                    Placeholder.unparsed("until", until)), plugin);
                                        } else {
                                            sendMessageToSource(source, locale.getMessageFor(source, "player-already-exists",
                                                    Placeholder.unparsed("player", playerName)), plugin);
                                        }
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    var entry = existing.get();
                                    Long expires = entry.getExpiresAtMillis();

                                    if (expires == null) {
                                        long newMillis = Instant.now().plus(dur).toEpochMilli();
                                        plugin.updatePlayerExpiry(playerName, newMillis);
                                        String until = DATE_FMT.format(Instant.ofEpochMilli(newMillis));
                                        sendMessageToSource(source, locale.getMessageFor(source, "player-extended-replace",
                                                Placeholder.unparsed("player", playerName),
                                                Placeholder.unparsed("until", until)), plugin);
                                        plugin.getServer().getPlayer(playerName).ifPresent(bridge::sendWhitelistUpdate);
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (!entry.isExpired()) {
                                        long newMillis = expires + dur.toMillis();
                                        plugin.updatePlayerExpiry(playerName, newMillis);
                                        String until = DATE_FMT.format(Instant.ofEpochMilli(newMillis));
                                        sendMessageToSource(source, locale.getMessageFor(source, "player-extended-add",
                                                Placeholder.unparsed("player", playerName),
                                                Placeholder.unparsed("duration", durationStr),
                                                Placeholder.unparsed("until", until)), plugin);
                                        plugin.getServer().getPlayer(playerName).ifPresent(bridge::sendWhitelistUpdate);
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String expiredAt = DATE_FMT.format(Instant.ofEpochMilli(expires));
                                    Component prompt = locale.getMessageFor(source, "extend-expired-title",
                                            Placeholder.unparsed("player", playerName),
                                            Placeholder.unparsed("expired", expiredAt));

                                    String cmdAdd = "/yawl extend " + playerName + " " + durationStr + " add";
                                    String cmdReplace = "/yawl extend " + playerName + " " + durationStr + " replace";

                                    Component btnAdd = locale.getMessageFor(source, "extend-button-add")
                                            .clickEvent(ClickEvent.runCommand(cmdAdd))
                                            .hoverEvent(HoverEvent.showText(locale.getMessageFor(source, "extend-button-add-hover")));
                                    Component btnReplace = locale.getMessageFor(source, "extend-button-replace")
                                            .clickEvent(ClickEvent.runCommand(cmdReplace))
                                            .hoverEvent(HoverEvent.showText(locale.getMessageFor(source, "extend-button-replace-hover")));

                                    Component full = prompt.append(Component.space()).append(btnAdd).append(Component.space()).append(btnReplace);
                                    sendMessageToSource(source, full, plugin);
                                    plugin.getServer().getPlayer(playerName).ifPresent(bridge::sendWhitelistUpdate);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("mode", StringArgumentType.word())
                                        .suggests((ctx, sb) -> {
                                            sb.suggest("add");
                                            sb.suggest("replace");
                                            return sb.buildFuture();
                                        })
                                        .executes(context -> {
                                            CommandSource source = context.getSource();
                                            LocaleManager locale = plugin.getLocaleManager();
                                            if (!source.hasPermission(Permissions.EXTEND)) {
                                                source.sendMessage(locale.getMessageFor(source, "no-permission"));
                                                return Command.SINGLE_SUCCESS;
                                            }

                                            String playerName = context.getArgument("player", String.class).trim();
                                            String durationStr = context.getArgument("duration", String.class).trim();
                                            String mode = context.getArgument("mode", String.class).trim().toLowerCase();

                                            var parsed = DurationParser.parse(durationStr);
                                            if (parsed.isEmpty()) {
                                                sendMessageToSource(source, locale.getMessageFor(source, "invalid-duration",
                                                        Placeholder.unparsed("duration", durationStr)), plugin);
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            Duration dur = parsed.get();

                                            Optional<org.renwixx.yawl.storage.WhitelistEntry> existing = plugin.getEntry(playerName);

                                            if ("replace".equals(mode)) {
                                                long newMillis = Instant.now().plus(dur).toEpochMilli();
                                                if (existing.isPresent()) {
                                                    plugin.updatePlayerExpiry(playerName, newMillis);
                                                } else {
                                                    plugin.addPlayer(playerName, dur);
                                                }
                                                String until = DATE_FMT.format(Instant.ofEpochMilli(newMillis));
                                                sendMessageToSource(source, locale.getMessageFor(source, "player-extended-replace",
                                                        Placeholder.unparsed("player", playerName),
                                                        Placeholder.unparsed("until", until)), plugin);
                                                plugin.getServer().getPlayer(playerName).ifPresent(bridge::sendWhitelistUpdate);
                                                return Command.SINGLE_SUCCESS;
                                            } else if ("add".equals(mode)) {
                                                if (existing.isEmpty()) {
                                                    boolean added = plugin.addPlayer(playerName, dur);
                                                    if (added) {
                                                        String until = DATE_FMT.format(Instant.now().plus(dur));
                                                        sendMessageToSource(source, locale.getMessageFor(source, "player-added-temp",
                                                                Placeholder.unparsed("player", playerName),
                                                                Placeholder.unparsed("until", until)), plugin);
                                                    } else {
                                                        sendMessageToSource(source, locale.getMessageFor(source, "player-already-exists",
                                                                Placeholder.unparsed("player", playerName)), plugin);
                                                    }
                                                    plugin.getServer().getPlayer(playerName).ifPresent(bridge::sendWhitelistUpdate);
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                                var entry = existing.get();
                                                Long base = entry.getExpiresAtMillis();
                                                if (base == null) {
                                                    base = Instant.now().toEpochMilli();
                                                }
                                                long newMillis = base + dur.toMillis();
                                                plugin.updatePlayerExpiry(playerName, newMillis);
                                                String until = DATE_FMT.format(Instant.ofEpochMilli(newMillis));
                                                sendMessageToSource(source, locale.getMessageFor(source, "player-extended-add",
                                                        Placeholder.unparsed("player", playerName),
                                                        Placeholder.unparsed("duration", durationStr),
                                                        Placeholder.unparsed("until", until)), plugin);
                                                plugin.getServer().getPlayer(playerName).ifPresent(bridge::sendWhitelistUpdate);
                                                return Command.SINGLE_SUCCESS;
                                            } else {
                                                return Command.SINGLE_SUCCESS;
                                            }
                                        })))
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
                    plugin.getServer().getAllPlayers().forEach(bridge::sendWhitelistUpdate);
                    return Command.SINGLE_SUCCESS;
                });

        builder.then(addCommand)
                .then(extendCommand)
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
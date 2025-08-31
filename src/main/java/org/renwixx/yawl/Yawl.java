package org.renwixx.yawl;

import com.google.inject.Inject;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
        id = "yetanotherwhitelistplugin",
        name = "yawl",
        version = BuildConstants.VERSION,
        description = "Most simple whitelist plugin for Velocity server.",
        url = "https://github.com/renwixx/",
        authors = {"Renwixx"}
)
public class Yawl {

    public static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Map<String, String> whitelistedPlayers = new ConcurrentHashMap<>();
    private boolean useClientLocale = false;
    private PluginConfig config;
    private LocaleManager localeManager;

    @Inject
    public Yawl(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        reload();

        server.getEventManager().register(this, new ConnectionListener(this));

        CommandManager commandManager = server.getCommandManager();
        BrigadierCommand yawlCommand = WhitelistCommand.create(this);
        commandManager.register(commandManager.metaBuilder("yawl").build(), yawlCommand);

        logger.info("YAWL (Yet Another Whitelist Plugin) has been enabled!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        saveWhitelistInternal(); // сохранить при выключении сервера
    }

    public void reload() {
        this.config = new PluginConfig(dataDirectory, logger);

        this.useClientLocale = config.isUseClientLocale();
        if (this.localeManager == null) {
            this.localeManager = new LocaleManager(dataDirectory, this, config.getLocale(), logger);
        } else {
            this.localeManager.setLocale(config.getLocale());
        }

        loadWhitelistInternal();
        checkAndKickNonWhitelistedPlayers();
    }

    public void checkAndKickNonWhitelistedPlayers() {
        if (!config.isEnabled())
            return;

        Component kickMessage = localeManager.getMessage("kick-message");
        for (Player player : server.getAllPlayers()) {
            if (!player.hasPermission(Permissions.BYPASS) && !isWhitelisted(player.getUsername())) {
                player.disconnect(kickMessage);
                logger.info("Kicked player {} because they are not in whitelist.", player.getUsername());
            }
        }
    }

    private String canonical(String name) {
        return config.isCaseSensitive() ? name : name.toLowerCase(Locale.ROOT);
    }

    public boolean isWhitelisted(String playerName) {
        return whitelistedPlayers.containsKey(canonical(playerName));
    }

    public List<String> getWhitelistedPlayers() {
        return List.copyOf(whitelistedPlayers.values());
    }

    public boolean addPlayer(String playerName) {
        String processed = playerName.trim();
        if (processed.isEmpty()) return false;

        String canonical = canonical(processed);
        if (whitelistedPlayers.putIfAbsent(canonical, processed) == null) {
            saveWhitelistInternal();
            return true;
        }
        return false;
    }

    public boolean removePlayer(String playerName) {
        String processed = playerName.trim();
        String canonical = canonical(processed);

        String removed = whitelistedPlayers.remove(canonical);
        if (removed != null) {
            saveWhitelistInternal();
            server.getPlayer(processed).ifPresent(player -> {
                if (!player.hasPermission(Permissions.BYPASS)) {
                    player.disconnect(localeManager.getMessage("kick-message"));
                    logger.info("Kicked player {} because they were removed from the whitelist.", player.getUsername());
                }
            });
            return true;
        }
        return false;
    }

    private Path getWhitelistPath() {
        return dataDirectory.resolve("whitelist.txt");
    }

    private void loadWhitelistInternal() {
        Path file = getWhitelistPath();
        if (!Files.exists(file)) {
            try {
                Files.createDirectories(dataDirectory);
                Files.write(file, List.of("# Add one player per line", "Player1"));
            } catch (IOException e) {
                logger.error("Could not create whitelist.txt", e);
                return;
            }
        }

        try {
            whitelistedPlayers.clear();
            List<String> lines = Files.readAllLines(file);
            lines.stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(name -> whitelistedPlayers.put(canonical(name), name));

            logger.info("Loaded {} players from whitelist.txt", whitelistedPlayers.size());
        } catch (IOException e) {
            logger.error("Could not load whitelist.txt", e);
        }
    }

    private void saveWhitelistInternal() {
        Path file = getWhitelistPath();
        Path tempFile = dataDirectory.resolve("whitelist.txt.tmp");

        try {
            List<String> sortedPlayers = whitelistedPlayers.values().stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();

            Files.write(tempFile, sortedPlayers);
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.error("Could not save whitelist.txt", e);
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {}
        }
    }

    public LocaleManager getLocaleManager() { return localeManager; }
    public boolean shouldUseClientLocale() { return useClientLocale; }
    public PluginConfig getConfig() { return config; }
    public Logger getLogger() { return logger; }
}
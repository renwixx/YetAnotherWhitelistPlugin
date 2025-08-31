package org.renwixx.yawl;

import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Plugin(id = "yetanotherwhitelistplugin",
        name = "yawl",
        version = org.renwixx.yawl.BuildConstants.VERSION,
        description = "Most simple whitelist plugin for Velocity server.",
        url = "https://github.com/renwixx/",
        authors = {"Renwixx"})
public class Yawl {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final List<String> whitelistedPlayers = new CopyOnWriteArrayList<>();
    private final MiniMessage miniMessage;

    private PluginConfig config;

    @Inject
    public Yawl(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();
        loadWhitelist();

        server.getEventManager().register(this, new ConnectionListener(this));

        CommandManager commandManager = server.getCommandManager();
        BrigadierCommand yawlCommand = WhitelistCommand.create(this);
        commandManager.register(commandManager.metaBuilder("yawl").build(), yawlCommand);

        logger.info("YAWL (Yet Another Whitelist Plugin) has been enabled!");
    }

    public boolean isWhitelisted(String playerName) {
        return whitelistedPlayers.stream().anyMatch(name -> name.equalsIgnoreCase(playerName));
    }

    public List<String> getWhitelistedPlayers() {
        return Collections.unmodifiableList(whitelistedPlayers);
    }

    public boolean addPlayer(String playerName) {
        if (isWhitelisted(playerName)) {
            return false; // Игрок уже в списке
        }
        whitelistedPlayers.add(playerName);
        saveWhitelist();
        return true;
    }

    public boolean removePlayer(String playerName) {
        boolean removed = whitelistedPlayers.removeIf(name -> name.equalsIgnoreCase(playerName));
        if (removed) {
            saveWhitelist();
        }
        return removed;
    }

    private Path getWhitelistPath() {
        return dataDirectory.resolve("whitelist.txt");
    }

    public void loadWhitelist() {
        Path file = getWhitelistPath();
        if (!Files.exists(file)) {
            try {
                Files.createDirectories(dataDirectory);
                Files.write(file, List.of("# Add one player per line", "Player1", "YourNickname"));
            } catch (IOException e) {
                logger.error("Could not create whitelist.txt", e);
                return;
            }
        }

        try {
            whitelistedPlayers.clear();
            List<String> lines = Files.readAllLines(file);
            whitelistedPlayers.addAll(
                    lines.stream()
                            .map(String::trim)
                            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                            .toList()
            );
            logger.info("Loaded {} players from whitelist.txt", whitelistedPlayers.size());
        } catch (IOException e) {
            logger.error("Could not load whitelist.txt", e);
        }
    }

    public void saveWhitelist() {
        Path file = getWhitelistPath();
        try {
            Files.write(file, whitelistedPlayers);
        } catch (IOException e) {
            logger.error("Could not save whitelist.txt", e);
        }
    }

    private void loadConfig() {
        File configFile = new File(dataDirectory.toFile(), "config.toml");

        if (!configFile.exists()) {
            try {
                Files.createDirectories(dataDirectory);
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.toml")) {
                    if (in == null) {
                        logger.error("Default config.toml not found in the plugin JAR!");
                        return;
                    }
                    Files.copy(in, configFile.toPath());
                }
            } catch (IOException e) {
                logger.error("Could not create default config.toml!", e);
                return;
            }
        }

        try {
            Toml toml = new Toml().read(configFile);
            this.config = new PluginConfig(toml);
            logger.info("Configuration loaded successfully.");
        } catch (Exception e) {
            logger.error("Failed to load config.toml. Using default values.", e);
            this.config = new PluginConfig(new Toml()); // Загрузка с дефолтными значениями
        }
    }

    public ProxyServer getServer() { return server; }
    public PluginConfig getConfig() { return config; }
    public MiniMessage getMiniMessage() { return miniMessage; }
    public Logger getLogger() { return logger; }
}
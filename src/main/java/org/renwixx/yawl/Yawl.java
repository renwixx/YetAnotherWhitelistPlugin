package org.renwixx.yawl;

import com.google.inject.Inject;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Plugin(id = "yetanotherwhitelistplugin",
        name = "yawl",
        version = BuildConstants.VERSION,
        description = "Most simple whitelist plugin for Velocity server.",
        url = "https://github.com/renwixx/",
        authors = {"Renwixx"})
public class Yawl {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final MiniMessage miniMessage;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Set<String> whitelistedPlayers = ConcurrentHashMap.newKeySet();
    private PluginConfig config;
    private LocaleManager localeManager;

    @Inject
    public Yawl(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.miniMessage = MiniMessage.miniMessage();
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

    public void reload() {
        // ИСПРАВЛЕНО: Операции, требующие блокировки, отделены от последующей проверки игроков.
        lock.writeLock().lock();
        try {
            this.config = new PluginConfig(dataDirectory, logger);
            this.localeManager = new LocaleManager(dataDirectory, config.getLocale(), logger, miniMessage);
            loadWhitelistInternal();
        } finally {
            lock.writeLock().unlock();
        }

        // Проверка и кик игроков выполняются ПОСЛЕ освобождения блокировки.
        checkAndKickPlayers();
    }

    public void checkAndKickPlayers() {
        if (!config.isEnabled()) {
            return;
        }

        Component kickMessage = localeManager.getMessage("kick-message");
        for (Player player : server.getAllPlayers()) {
            // isWhitelisted использует readLock, что безопасно
            if (!player.hasPermission(Permissions.BYPASS) && !isWhitelisted(player.getUsername())) {
                player.disconnect(kickMessage);
                logger.info("Kicked player {} because they are no longer on the whitelist.", player.getUsername());
            }
        }
    }

    public boolean isWhitelisted(String playerName) {
        lock.readLock().lock();
        try {
            if (config.isCaseSensitive()) {
                return whitelistedPlayers.contains(playerName);
            }
            return whitelistedPlayers.contains(playerName.toLowerCase());
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<String> getWhitelistedPlayers() {
        lock.readLock().lock();
        try {
            return List.copyOf(whitelistedPlayers);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean addPlayer(String playerName) {
        lock.writeLock().lock();
        try {
            String processedName = playerName.trim();
            if (processedName.isEmpty()) return false;

            String nameToAdd = config.isCaseSensitive() ? processedName : processedName.toLowerCase();
            boolean added = whitelistedPlayers.add(nameToAdd);

            if (added) {
                saveWhitelistInternal();
            }
            return added;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removePlayer(String playerName) {
        // ИСПРАВЛЕНО: Логика кика игрока полностью вынесена за пределы блокировки.
        String processedName = playerName.trim();
        boolean removed;

        lock.writeLock().lock();
        try {
            String nameToRemove = config.isCaseSensitive() ? processedName : processedName.toLowerCase();
            removed = whitelistedPlayers.remove(nameToRemove);
            if (removed) {
                saveWhitelistInternal();
            }
        } finally {
            lock.writeLock().unlock();
        }

        // Кик происходит только если игрок был успешно удален и ПОСЛЕ освобождения блокировки.
        if (removed) {
            server.getPlayer(playerName).ifPresent(player -> {
                if (!player.hasPermission(Permissions.BYPASS)) {
                    player.disconnect(localeManager.getMessage("kick-message"));
                    logger.info("Kicked player {} because they were removed from the whitelist.", player.getUsername());
                }
            });
        }
        return removed;
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
            Set<String> newWhitelist = lines.stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(name -> config.isCaseSensitive() ? name : name.toLowerCase())
                    .collect(Collectors.toSet());

            whitelistedPlayers.addAll(newWhitelist);
            logger.info("Loaded {} players from whitelist.txt", whitelistedPlayers.size());
        } catch (IOException e) {
            logger.error("Could not load whitelist.txt", e);
        }
    }

    // ИЗМЕНЕНО: Метод сохранения файла переписан для атомарности.
    private void saveWhitelistInternal() {
        Path file = getWhitelistPath();
        Path tempFile = dataDirectory.resolve("whitelist.txt.tmp");

        try {
            List<String> sortedPlayers = whitelistedPlayers.stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();

            Files.write(tempFile, sortedPlayers);

            // Атомарно заменяем старый файл новым, чтобы избежать повреждения данных
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.error("Could not save whitelist.txt", e);
            // Пытаемся удалить временный файл в случае ошибки
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // Игнорируем ошибку удаления, так как исходная ошибка важнее
            }
        }
    }

    public PluginConfig getConfig() { return config; }
    public MiniMessage getMiniMessage() { return miniMessage; }
    public LocaleManager getLocaleManager() { return localeManager; }
    public Logger getLogger() { return logger; }
}
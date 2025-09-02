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
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.renwixx.yawl.storage.FileWhitelistStorage;
import org.renwixx.yawl.storage.WhitelistEntry;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
    private final Map<String, WhitelistEntry> whitelistedPlayers = new ConcurrentHashMap<>();
    private boolean useClientLocale = false;
    private PluginConfig config;
    private LocaleManager localeManager;
    private FileWhitelistStorage storage;
    private ScheduledTask expiryTask;

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
        try {
            if (expiryTask != null) {
                expiryTask.cancel();
                expiryTask = null;
            }
            if (storage != null) {
                storage.flush(whitelistedPlayers);
            }
        } catch (Exception e) {
            logger.error("Error while closing storage", e);
        }
    }

    public void reload() {
        this.config = new PluginConfig(dataDirectory, logger);

        this.useClientLocale = config.isUseClientLocale();
        if (this.localeManager == null) {
            this.localeManager = new LocaleManager(dataDirectory, this, config.getLocale(), logger);
        } else {
            this.localeManager.setLocale(config.getLocale());
        }

        // Инициализация хранилища (только файл)
        try {
            storage = new FileWhitelistStorage(dataDirectory.resolve("whitelist.txt"), dataDirectory, logger);
            storage.init();
            whitelistedPlayers.clear();
            // Загружаем и перекладываем с учетом каноникализации
            Map<String, WhitelistEntry> loaded = storage.loadAll();
            for (WhitelistEntry e : loaded.values()) {
                // перекладываем ключ в соответствие с текущей настройкой case-sensitive
                String canonNow = canonical(e.getOriginalName());
                whitelistedPlayers.put(canonNow, new WhitelistEntry(canonNow, e.getOriginalName(), e.getExpiresAtMillis()));
            }
        } catch (Exception e) {
            logger.error("Failed to initialize storage. Fallback to empty whitelist.", e);
            whitelistedPlayers.clear();
        }

        removeExpiredEntriesAndMaybeKick(false);
        checkAndKickNonWhitelistedPlayers();
        scheduleExpirySweep();
    }

    private void scheduleExpirySweep() {
        try {
            if (expiryTask != null) {
                expiryTask.cancel();
            }
        } catch (Exception ignored) {}
        expiryTask = server.getScheduler()
                .buildTask(this, () -> {
                    if (!config.isEnabled()) return;
                    removeExpiredEntriesAndMaybeKick(config.isKickActiveOnRevoke());
                })
                .repeat(Duration.ofSeconds(5))
                .schedule();
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
        WhitelistEntry entry = whitelistedPlayers.get(canonical(playerName));
        if (entry == null) return false;
        if (entry.isExpired()) {
            // Снять истекшую запись
            whitelistedPlayers.remove(entry.getCanonicalName());
            try {
                if (storage != null) {
                    storage.flush(whitelistedPlayers);
                }
            } catch (Exception e) {
                logger.warn("Failed to persist removal of expired entry '{}'", entry.getOriginalName(), e);
            }
            return false;
        }
        return true;
    }

    public List<String> getWhitelistedPlayers() {
        // Только актуальные
        removeExpiredEntriesAndMaybeKick(false);
        return whitelistedPlayers.values().stream()
                .sorted(Comparator.comparing(WhitelistEntry::getOriginalName, String.CASE_INSENSITIVE_ORDER))
                .map(WhitelistEntry::getOriginalName)
                .toList();
    }

    public boolean addPlayer(String playerName) {
        return addPlayerInternal(playerName, null);
    }

    public boolean addPlayer(String playerName, Duration duration) {
        Long expiresAt = duration == null ? null : Instant.now().plus(duration).toEpochMilli();
        return addPlayerInternal(playerName, expiresAt);
    }

    private boolean addPlayerInternal(String playerName, Long expiresAtMillis) {
        String processed = playerName.trim();
        if (processed.isEmpty()) return false;

        String canonical = canonical(processed);
        WhitelistEntry newEntry = new WhitelistEntry(canonical, processed, expiresAtMillis);
        WhitelistEntry old = whitelistedPlayers.putIfAbsent(canonical, newEntry);
        if (old == null) {
            try {
                if (storage != null) {
                    storage.flush(whitelistedPlayers);
                }
            } catch (Exception e) {
                logger.error("Failed to persist whitelist entry for {}", processed, e);
            }
            return true;
        } else {
            // Если уже есть, но хотим обновить срок — перезапишем, если пришел новый expires
            if (!Objects.equals(old.getExpiresAtMillis(), expiresAtMillis) && expiresAtMillis != null) {
                whitelistedPlayers.put(canonical, newEntry);
                try {
                    if (storage != null) {
                        storage.flush(whitelistedPlayers);
                    }
                } catch (Exception e) {
                    logger.error("Failed to update whitelist entry for {}", processed, e);
                }
            }
            return false;
        }
    }

    public boolean removePlayer(String playerName) {
        String processed = playerName.trim();
        String canonical = canonical(processed);

        WhitelistEntry removed = whitelistedPlayers.remove(canonical);
        if (removed != null) {
            try {
                if (storage != null) {
                    storage.flush(whitelistedPlayers);
                }
            } catch (Exception e) {
                logger.error("Failed to persist whitelist removal for {}", processed, e);
            }
            server.getPlayer(processed).ifPresent(player -> {
                if (config.isKickActiveOnRevoke() && !player.hasPermission(Permissions.BYPASS)) {
                    player.disconnect(localeManager.getMessage("kick-message"));
                    logger.info("Kicked player {} because they were removed from the whitelist.", player.getUsername());
                }
            });
            return true;
        }
        return false;
    }

    private void removeExpiredEntriesAndMaybeKick(boolean kickActive) {
        boolean changed = false;
        for (Iterator<Map.Entry<String, WhitelistEntry>> it = whitelistedPlayers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, WhitelistEntry> e = it.next();
            WhitelistEntry value = e.getValue();
            if (value.isExpired()) {
                it.remove();
                if (kickActive && config.isEnabled()) {
                    server.getPlayer(value.getOriginalName()).ifPresent(player -> {
                        if (!player.hasPermission(Permissions.BYPASS)) {
                            player.disconnect(localeManager.getMessage("kick-message"));
                            logger.info("Kicked player {} because their whitelist access expired.", player.getUsername());
                        }
                    });
                }
                changed = true;
            }
        }
        if (changed) {
            try {
                if (storage != null) {
                    storage.flush(whitelistedPlayers);
                }
            } catch (Exception e) {
                logger.warn("Failed to flush storage after expired entries cleanup", e);
            }
        }
    }

    public LocaleManager getLocaleManager() { return localeManager; }
    public boolean shouldUseClientLocale() { return useClientLocale; }
    public PluginConfig getConfig() { return config; }
    public Logger getLogger() { return logger; }
}
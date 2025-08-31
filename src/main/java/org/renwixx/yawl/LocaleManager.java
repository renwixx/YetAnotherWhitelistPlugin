package org.renwixx.yawl;

import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class LocaleManager {
    private final Yawl plugin;
    private final Path localesDirectory;
    private String locale;
    private final Logger logger;
    private Toml messages;
    private static final List<String> SUPPORTED_LOCALES = List.of(
            "en", "ru", "uk", "de", "fr", "es", "ar", "zh-cn", "ja"
    );

    public LocaleManager(Path dataDirectory, Yawl plugin, String locale, Logger logger) {
        this.localesDirectory = dataDirectory.resolve("locales");
        this.plugin = plugin;
        this.locale = locale;
        this.logger = logger;
        reload();
    }

    public void reload() {
        // Save default locales if missing
        SUPPORTED_LOCALES.forEach(this::saveDefaultLocale);

        Path localeFile = localesDirectory.resolve(locale + ".toml");
        if (!Files.exists(localeFile)) {
            logger.warn("Locale file '{}' not found. Falling back to 'en.toml'.", locale + ".toml");
            localeFile = localesDirectory.resolve("en.toml");
        }

        try {
            this.messages = new Toml().read(localeFile.toFile());
            logger.info("Successfully loaded messages from '{}'.", localeFile.getFileName());
        } catch (Exception e) {
            logger.error("Failed to load locale file '{}'. Using empty messages.", localeFile.getFileName(), e);
            this.messages = new Toml();
        }
    }

    private void saveDefaultLocale(String localeCode) {
        try {
            if (!Files.exists(localesDirectory)) {
                Files.createDirectories(localesDirectory);
            }
            Path localeFile = localesDirectory.resolve(localeCode + ".toml");
            if (!Files.exists(localeFile)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("locales/" + localeCode + ".toml")) {
                    if (in == null) {
                        logger.error("Default locale file for '{}' not found in JAR.", localeCode);
                        return;
                    }
                    Files.copy(in, localeFile);
                }
            }
        } catch (IOException e) {
            logger.error("Could not save default locale file for '{}'.", localeCode, e);
        }
    }

    public Component getMessageFor(CommandSource source, String key, TagResolver... placeholders) {
        if (!plugin.shouldUseClientLocale()) {
            return getMessage(key, placeholders);
        }

        String lang = null;

        if (source instanceof Player player) {
            if (player.getPlayerSettings().getLocale() != null) {
                lang = player.getPlayerSettings().getLocale().getLanguage();
            }
        }

        if (lang != null && Files.exists(localesDirectory.resolve(lang + ".toml"))) {
            try {
                Toml localMessages = new Toml().read(localesDirectory.resolve(lang + ".toml").toFile());
                String msg = localMessages.getString(key, messages.getString(key, key));
                return Yawl.MINI_MESSAGE.deserialize(msg, placeholders);
            } catch (Exception e) {
                logger.warn("Failed to load messages for '{}', using default locale.", lang, e);
            }
        }

        return getMessage(key, placeholders);
    }

    public String getMessageString(String key) {
        return messages.getString(key, "<red>Missing message for key: " + key + "</red>");
    }

    public Component getMessage(String key, TagResolver... placeholders) {
        String message = getMessageString(key);
        return Yawl.MINI_MESSAGE.deserialize(message, placeholders);
    }

    public void setLocale(String locale) {
        this.locale = locale;
        reload();
    }
}

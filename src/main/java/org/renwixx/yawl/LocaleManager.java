package org.renwixx.yawl;

import com.moandjiezana.toml.Toml;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages loading and providing localized messages from .toml files.
 */
public class LocaleManager {

    private final Path localesDirectory;
    private final String locale;
    private final Logger logger;
    private final MiniMessage miniMessage;
    private Toml messages;

    public LocaleManager(Path dataDirectory, String locale, Logger logger, MiniMessage miniMessage) {
        this.localesDirectory = dataDirectory.resolve("locales");
        this.locale = locale;
        this.logger = logger;
        this.miniMessage = miniMessage;
        loadMessages();
    }

    private void loadMessages() {
        // Save default locales if they don't exist
        saveDefaultLocale("en");
        saveDefaultLocale("ru");

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
            this.messages = new Toml(); // Avoid NullPointerException
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

    /**
     * Gets a message string from the loaded locale file.
     *
     * @param key The key of the message (e.g., "kick-message").
     * @return The message string.
     */
    public String getMessageString(String key) {
        return messages.getString(key, "<red>Missing message for key: " + key + "</red>");
    }

    /**
     * Deserializes a message from the locale file into a Component.
     *
     * @param key          The key of the message.
     * @param placeholders Placeholders to be applied using MiniMessage.
     * @return The formatted Component.
     */
    public Component getMessage(String key, TagResolver... placeholders) {
        String message = getMessageString(key);
        return miniMessage.deserialize(message, placeholders);
    }
}
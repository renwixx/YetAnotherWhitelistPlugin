package org.renwixx.yawl;

import com.moandjiezana.toml.Toml;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class PluginConfig {
    private final boolean enabled;
    private final String locale;
    private final boolean caseSensitive;
    private final boolean useClientLocale;

    public PluginConfig(Path dataDirectory, Logger logger) {
        Path configFile = saveDefaultConfig(dataDirectory, logger);
        Toml toml;
        if (configFile != null) {
            toml = new Toml().read(configFile.toFile());
        } else {
            toml = new Toml();
        }

        this.enabled = toml.getBoolean("settings.enabled", true);
        this.locale = toml.getString("settings.locale", "en");
        this.caseSensitive = toml.getBoolean("settings.case-sensitive", false);
        this.useClientLocale = toml.getBoolean("settings.use-client-locale", false);
    }

    private Path saveDefaultConfig(Path dataDirectory, Logger logger) {
        Path configFile = dataDirectory.resolve("config.toml");
        if (!Files.exists(configFile)) {
            try {
                Files.createDirectories(dataDirectory);
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.toml")) {
                    if (in == null) {
                        logger.error("Default config.toml not found in the plugin JAR!");
                        return null;
                    }
                    Files.copy(in, configFile);
                }
            } catch (IOException e) {
                logger.error("Could not create default config.toml!", e);
                return null;
            }
        }
        return configFile;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getLocale() {
        return locale;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public boolean isUseClientLocale() {
        return useClientLocale;
    }
}
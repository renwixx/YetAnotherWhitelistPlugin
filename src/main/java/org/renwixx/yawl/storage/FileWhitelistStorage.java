package org.renwixx.yawl.storage;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

public class FileWhitelistStorage {
    private final Path filePath;
    private final Path dataDirectory;
    private final Logger logger;

    public FileWhitelistStorage(Path filePath, Path dataDirectory, Logger logger) {
        this.filePath = filePath;
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    public void init() throws IOException {
        if (!Files.exists(filePath)) {
            Files.createDirectories(dataDirectory);
            List<String> defaults = List.of(
                    "# Add one player per line; optionally use 'name|expiresAtMillis' for timed access",
                    "Player1"
            );
            Files.write(filePath, defaults);
        }
    }

    public Map<String, WhitelistEntry> loadAll() throws IOException {
        List<String> lines = Files.readAllLines(filePath);
        Map<String, WhitelistEntry> map = new HashMap<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String name;
            Long expires = null;
            if (line.contains("|")) {
                String[] parts = line.split("\\|", 2);
                name = parts[0].trim();
                try {
                    expires = parts[1].trim().isEmpty() ? null : Long.parseLong(parts[1].trim());
                } catch (NumberFormatException e) {
                    logger.warn("Invalid expiresAt in whitelist line '{}', ignoring expiry.", line);
                }
            } else {
                name = line;
            }
            if (!name.isEmpty()) {
                map.put(name, new WhitelistEntry(name, name, expires));
            }
        }
        logger.info("Loaded {} players from whitelist.txt", map.size());
        return map;
    }

    public void flush(Map<String, WhitelistEntry> entries) throws IOException {
        Path tempFile = filePath.resolveSibling(filePath.getFileName().toString() + ".tmp");
        try {
            List<String> lines = entries.values().stream()
                    .sorted(Comparator.comparing(WhitelistEntry::getOriginalName, String.CASE_INSENSITIVE_ORDER))
                    .map(e -> e.getOriginalName() + (e.getExpiresAtMillis() != null ? "|" + e.getExpiresAtMillis() : ""))
                    .collect(Collectors.toList());
            Files.write(tempFile, lines);
            Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {}
            throw e;
        }
    }
}

package org.renwixx.yawl.storage;

import java.time.Instant;
import java.util.Objects;

public final class WhitelistEntry {
    private final String canonicalName;
    private final String originalName;
    private final Long expiresAtMillis; // null = бессрочно

    public WhitelistEntry(String canonicalName, String originalName, Long expiresAtMillis) {
        this.canonicalName = Objects.requireNonNull(canonicalName, "canonicalName");
        this.originalName = Objects.requireNonNull(originalName, "originalName");
        this.expiresAtMillis = expiresAtMillis;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public String getOriginalName() {
        return originalName;
    }

    public Long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean isExpired() {
        return expiresAtMillis != null && expiresAtMillis <= Instant.now().toEpochMilli();
    }
}

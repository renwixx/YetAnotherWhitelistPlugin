package org.renwixx.yawl.util;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern SIMPLE = Pattern.compile("^\\s*(\\d+)\\s*([a-zA-Z]+)\\s*$");

    private DurationParser() {}

    public static Optional<Duration> parse(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        String s = input.trim();
        if (s.startsWith("P") || s.startsWith("p")) {
            try {
                return Optional.of(Duration.parse(s.toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
        Matcher m = SIMPLE.matcher(s);
        if (!m.matches()) return Optional.empty();
        long amount = Long.parseLong(m.group(1));
        String unit = m.group(2).toLowerCase(Locale.ROOT);
        return switch (unit) {
            case "s", "sec", "secs", "second", "seconds" -> Optional.of(Duration.ofSeconds(amount));
            case "m", "min", "mins", "minute", "minutes" -> Optional.of(Duration.ofMinutes(amount));
            case "h", "hr", "hrs", "hour", "hours" -> Optional.of(Duration.ofHours(amount));
            case "d", "day", "days" -> Optional.of(Duration.ofDays(amount));
            case "w", "wk", "wks", "week", "weeks" -> Optional.of(Duration.ofDays(amount * 7));
            case "mo", "mon", "month", "months" -> Optional.of(Duration.ofDays(amount * 30)); // прибл.
            case "y", "yr", "yrs", "year", "years" -> Optional.of(Duration.ofDays(amount * 365)); // прибл.
            default -> Optional.empty();
        };
    }
}

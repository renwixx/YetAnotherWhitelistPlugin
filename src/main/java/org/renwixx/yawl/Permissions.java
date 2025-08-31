package org.renwixx.yawl;

public final class Permissions {
    private static final String BASE = "yawl.";
    public static final String BYPASS = BASE + "bypass";

    private static final String COMMAND_BASE = BASE + "command.";
    public static final String ADD = COMMAND_BASE + "add";
    public static final String REMOVE = COMMAND_BASE + "remove";
    public static final String LIST = COMMAND_BASE + "list";
    public static final String RELOAD = COMMAND_BASE + "reload";

    private Permissions() {
        // Prevent instantiation
    }
}
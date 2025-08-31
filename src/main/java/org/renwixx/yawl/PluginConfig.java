package org.renwixx.yawl;

import com.moandjiezana.toml.Toml;

public class PluginConfig {

    private final boolean enabled;
    private final Messages messages;

    public PluginConfig(Toml toml) {
        this.enabled = toml.getBoolean("settings.enabled", true);
        this.messages = new Messages(toml.getTable("messages"));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Messages getMessages() {
        return messages;
    }

    public static class Messages {
        private final String kickMessage;
        private final String playerAdded;
        private final String playerAlreadyExists;
        private final String playerRemoved;
        private final String playerNotFound;
        private final String listHeader;
        private final String listEmpty;
        private final String noPermission;

        public Messages(Toml toml) {
            this.kickMessage = toml.getString("kick-message", "<red>Вы не в белом списке этого сервера!");
            this.playerAdded = toml.getString("player-added", "<gray>Игрок <green>{player}</green> был добавлен в белый список.");
            this.playerAlreadyExists = toml.getString("player-already-exists", "<gray>Игрок <yellow>{player}</yellow> уже находится в белом списке.");
            this.playerRemoved = toml.getString("player-removed", "<gray>Игрок <red>{player}</red> был удален из белого списка.");
            this.playerNotFound = toml.getString("player-not-found", "<gray>Игрока <yellow>{player}</yellow> нет в белом списке.");
            this.listHeader = toml.getString("list-header", "<gold>Игроки в белом списке ({count}):</gold><white> {players}");
            this.listEmpty = toml.getString("list-empty", "<yellow>Белый список пуст.");
            this.noPermission = toml.getString("no-permission", "<red>У вас нет прав для использования этой команды.");
        }

        public String getKickMessage() { return kickMessage; }
        public String getPlayerAdded() { return playerAdded; }
        public String getPlayerAlreadyExists() { return playerAlreadyExists; }
        public String getPlayerRemoved() { return playerRemoved; }
        public String getPlayerNotFound() { return playerNotFound; }
        public String getListHeader() { return listHeader; }
        public String getListEmpty() { return listEmpty; }
        public String getNoPermission() { return noPermission; }
    }
}
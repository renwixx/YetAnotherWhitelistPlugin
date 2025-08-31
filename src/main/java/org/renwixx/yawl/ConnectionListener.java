package org.renwixx.yawl;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;

public class ConnectionListener {

    private final Yawl plugin;

    public ConnectionListener(Yawl plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        if (!plugin.getConfig().isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        String playerName = player.getUsername();

        if (!plugin.isWhitelisted(playerName)) {
            event.setResult(LoginEvent.ComponentResult.denied(
                    plugin.getMiniMessage().deserialize(plugin.getConfig().getMessages().getKickMessage())
            ));
        }
    }
}
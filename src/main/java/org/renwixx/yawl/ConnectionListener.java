package org.renwixx.yawl;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;

public record ConnectionListener(Yawl plugin) {

    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        if (!plugin.getConfig().isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission(Permissions.BYPASS)) {
            return;
        }

        String playerName = player.getUsername();

        if (!plugin.isWhitelisted(playerName)) {
            event.setResult(LoginEvent.ComponentResult.denied(
                    plugin.getLocaleManager().getMessage("kick-message")
            ));
        }
    }
}
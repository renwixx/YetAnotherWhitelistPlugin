package org.renwixx.yawl;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.renwixx.yawl.storage.WhitelistEntry;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class VelocityToBackendBridge {
    private final Yawl plugin;
    private final LocaleManager localeManager;
    private static final MinecraftChannelIdentifier DATA_CHANNEL = MinecraftChannelIdentifier.create("yawl", "data");

    public VelocityToBackendBridge(Yawl plugin, LocaleManager localeManager) {
        this.plugin = plugin;
        this.localeManager = localeManager;
        plugin.getServer().getChannelRegistrar().register(DATA_CHANNEL);
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> {
            if (player.getCurrentServer().map(ServerConnection::getServer).filter(s -> s.equals(event.getServer())).isPresent()) {
                sendWhitelistUpdate(player);
            }
        });
    }

    public void sendWhitelistUpdate(Player player) {
        player.getCurrentServer().ifPresent(serverConnection -> {
            byte[] payload = createDataPayload(player);
            serverConnection.getServer().sendPluginMessage(DATA_CHANNEL, payload);
        });
    }

    private byte[] createDataPayload(Player player) {
        String durationString;
        Optional<WhitelistEntry> entryOpt = plugin.getEntry(player.getUsername());

        if (entryOpt.isPresent()) {
            WhitelistEntry entry = entryOpt.get();
            if (entry.getExpiresAtMillis() == null) {
                durationString = localeManager.getMessageStringFor(player, "placeholder-permanent");
            } else if (entry.isExpired()) {
                durationString = localeManager.getMessageStringFor(player, "placeholder-expired");
            } else {
                long remainingMillis = entry.getExpiresAtMillis() - System.currentTimeMillis();
                durationString = formatDuration(Duration.ofMillis(remainingMillis), player);
            }
        } else {
            durationString = localeManager.getMessageStringFor(player, "placeholder-na");
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(stream)) {
            out.writeUTF(player.getUniqueId().toString());
            out.writeUTF(durationString);
        } catch (IOException e) {
            plugin.getLogger().error("Failed to create plugin message payload for {}", player.getUsername(), e);
        }
        return stream.toByteArray();
    }

    private String formatDuration(Duration duration, Player player) {
        if (duration.isNegative() || duration.isZero()) {
            return localeManager.getMessageStringFor(player, "placeholder-expired");
        }
        long days = duration.toDays();
        if (days > 0) {
            return String.format("%d%s", days, localeManager.getMessageStringFor(player, "placeholder-days"));
        }
        long hours = duration.toHoursPart();
        if (hours > 0) {
            return String.format("%d%s", hours, localeManager.getMessageStringFor(player, "placeholder-hrs"));
        }
        long minutes = duration.toMinutesPart();
        long displayMinutes = (minutes == 0 && duration.toSecondsPart() > 0) ? 1 : minutes;
        return String.format("%d%s", Math.max(1, displayMinutes), localeManager.getMessageStringFor(player, "placeholder-mins"));
    }
}
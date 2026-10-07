package land.webgui.server;

import land.webgui.WebviewNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends the signed F6 menu URL as soon as the auth bridge confirms a player's login: until
 * then the client only got the bare page (WebviewAuthGate). Checked once a second.
 */
public final class WebviewAuthWatcher {
    private static final Map<UUID, String> WAITING = new ConcurrentHashMap<>();
    private static int ticks;

    public static void waitFor(ServerPlayer player, String menuUrl) {
        WAITING.put(player.getUUID(), menuUrl);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (WAITING.isEmpty() || ++ticks % 20 != 0) return;
        var server = event.getServer();
        WAITING.forEach((uuid, url) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) { WAITING.remove(uuid); return; }
            if (WebviewAuthGate.allowed(player)) {
                WAITING.remove(uuid);
                WebviewNetworking.sendMainMenuUrl(player, url);
            }
        });
    }

    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        WAITING.remove(event.getEntity().getUUID());
    }
}

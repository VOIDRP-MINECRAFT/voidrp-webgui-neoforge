package land.webgui.server;

import land.webgui.WebGUIMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * No WebGUI for a player who has not finished logging in.
 *
 * The server is offline-mode: anyone can join under any nickname and sits "pending" until
 * voidrp-auth-bridge confirms the login through the launcher. A webgui token signed in that
 * window is a key to that account's game-ui (upgrader spins, market orders, Void Coins) —
 * so with the auth bridge installed nothing is opened and no token is signed until the bridge
 * says the player is authenticated.
 *
 * The bridge's in-memory state is asked (it is empty after a restart), not its entity tags:
 * tags are saved with the player and could survive a crash as "authenticated". Without the
 * bridge on the server the gate is open, as before.
 */
public final class WebviewAuthGate {
    private static final String AUTH_MOD_ID = "voidrp_auth_bridge";
    private static final String BOOTSTRAP = "ru.voidrp.authbridge.bootstrap.ModBootstrap";

    private static volatile boolean resolved;
    private static Method getBootstrap;
    private static Method stateStore;
    private static Method isAuthenticated;

    private WebviewAuthGate() {}

    public static boolean authBridgePresent() {
        return ModList.get().isLoaded(AUTH_MOD_ID);
    }

    public static boolean allowed(ServerPlayer player) {
        if (!authBridgePresent()) return true;
        if (!resolve()) {
            // bridge present but its API changed: fail closed on the pending tag at least
            return player.getTags().contains("voidrp_authenticated") && !player.getTags().contains("voidrp_auth_pending");
        }
        try {
            Object bootstrap = getBootstrap.invoke(null);
            Object store = stateStore.invoke(bootstrap);
            return (Boolean) isAuthenticated.invoke(store, player.getUUID());
        } catch (ReflectiveOperationException | RuntimeException e) {
            WebGUIMod.LOGGER.warn("[WebGUI] auth gate: cannot ask voidrp_auth_bridge ({}) — closed for {}", e.toString(), player.getName().getString());
            return false;
        }
    }

    private static synchronized boolean resolve() {
        if (resolved) return isAuthenticated != null;
        resolved = true;
        try {
            Class<?> bootstrapClass = Class.forName(BOOTSTRAP);
            getBootstrap = bootstrapClass.getMethod("get");
            stateStore = bootstrapClass.getMethod("stateStore");
            isAuthenticated = stateStore.getReturnType().getMethod("isAuthenticated", UUID.class);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            WebGUIMod.LOGGER.warn("[WebGUI] auth gate: voidrp_auth_bridge API not found ({}), using its tags", e.toString());
            isAuthenticated = null;
            return false;
        }
    }
}

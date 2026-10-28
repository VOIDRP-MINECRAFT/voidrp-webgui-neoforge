package land.webgui;

import land.webgui.compat.ClientCompat;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class WebGUIClientHandlers {
    private WebGUIClientHandlers() {}

    // The server's open request (the HUD on join, a page) can arrive before the in-game browser
    // has started: the launcher joins the server as soon as the game window is up and MCEF needs
    // a few seconds more. The request used to be dropped with a chat line, so the HUD never showed
    // until a rejoin. Keep the latest one and apply it when the browser is ready — on the same
    // connection only, so nothing carries over to another server.
    private static WebviewPayloads.OpenWebS2CPayload pendingOpen;
    private static Object pendingConnection;

    public static void onMouseMove(long window, double x, double y) {
        Minecraft client = Minecraft.getInstance();
        if (!WebHudOverlay.shouldDeliverHudBrowserInput(client)) return;
        var win = client.getWindow();
        if (ClientCompat.windowHandle(win) != window) return;
        MCEFBrowser browser = WebSession.browser();
        if (browser == null) return;
        browser.sendMouseMove((int) x, (int) y);
    }

    public static void handleOpenPayload(WebviewPayloads.OpenWebS2CPayload payload) {
        WebGUIMod.LOGGER.info("[WebGUI] Client received open payload: proto={} mode={} url={}",
                payload.protocolVersion(), payload.displayMode(), payload.url());
        if (payload.protocolVersion() != WebviewNetworking.PROTOCOL_VERSION) {
            WebGUIMod.LOGGER.warn("[WebGUI] Protocol version mismatch: got {} expected {}",
                    payload.protocolVersion(), WebviewNetworking.PROTOCOL_VERSION);
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (!MCEF.isInitialized()) {
            WebGUIMod.LOGGER.info("[WebGUI] MCEF not ready yet — opening once it is: {}", payload.url());
            pendingOpen = payload;
            pendingConnection = client.getConnection();
            return;
        }
        applyOpen(client, payload);
    }

    /** Every client tick: applies a deferred open once MCEF is up and no screen is in the way. */
    public static void tickPendingOpen(Minecraft client) {
        if (pendingOpen == null) return;
        if (client.getConnection() == null || client.getConnection() != pendingConnection) {
            pendingOpen = null;
            pendingConnection = null;
            return;
        }
        // Wait while the player has a screen open (inventory, chat): opening would close it.
        if (!MCEF.isInitialized() || client.player == null || ClientCompat.screen(client) != null) return;
        WebviewPayloads.OpenWebS2CPayload payload = pendingOpen;
        pendingOpen = null;
        pendingConnection = null;
        WebGUIMod.LOGGER.info("[WebGUI] MCEF ready — applying the deferred open: {}", payload.url());
        applyOpen(client, payload);
    }

    private static void applyOpen(Minecraft client, WebviewPayloads.OpenWebS2CPayload payload) {
        String u = payload.url() == null || payload.url().isBlank() ? StartUrls.primary() : payload.url();
        if (payload.displayMode() == WebviewNetworking.MODE_GUI) {
            WebGUIMod.LOGGER.info("[WebGUI] Opening WebViewScreen: {}", u);
            ClientCompat.setScreen(client, new WebViewScreen(u));
        } else if (payload.displayMode() == WebviewNetworking.MODE_HUD) {
            WebGUIMod.LOGGER.info("[WebGUI] Applying server HUD: {}", u);
            WebHudOverlay.applyServerOpen(client, u);
        } else {
            WebGUIMod.LOGGER.warn("[WebGUI] Unknown displayMode: {}", payload.displayMode());
        }
    }
}

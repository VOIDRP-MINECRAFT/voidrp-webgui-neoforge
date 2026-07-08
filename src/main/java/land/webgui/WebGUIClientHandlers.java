package land.webgui;

import land.webgui.compat.Compat;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class WebGUIClientHandlers {
    private WebGUIClientHandlers() {}

    public static void onMouseMove(long window, double x, double y) {
        Minecraft client = Minecraft.getInstance();
        if (!WebHudOverlay.shouldDeliverHudBrowserInput(client)) return;
        var win = client.getWindow();
        if (Compat.windowHandle(win) != window) return;
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
            WebGUIMod.LOGGER.warn("[WebGUI] MCEF not initialized — cannot open URL: {}", payload.url());
            if (client.player != null) {
                client.player.sendSystemMessage(Component.translatable("message.webgui.mcef_not_ready"));
            }
            return;
        }
        String u = payload.url() == null || payload.url().isBlank() ? StartUrls.primary() : payload.url();
        if (payload.displayMode() == WebviewNetworking.MODE_GUI) {
            WebGUIMod.LOGGER.info("[WebGUI] Opening WebViewScreen: {}", u);
            Compat.setScreen(client, new WebViewScreen(u));
        } else if (payload.displayMode() == WebviewNetworking.MODE_HUD) {
            WebGUIMod.LOGGER.info("[WebGUI] Applying server HUD: {}", u);
            WebHudOverlay.applyServerOpen(client, u);
        } else {
            WebGUIMod.LOGGER.warn("[WebGUI] Unknown displayMode: {}", payload.displayMode());
        }
    }
}

package land.webgui;

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
        if (win.getWindow() != window) return;
        MCEFBrowser browser = WebSession.browser();
        if (browser == null) return;
        browser.sendMouseMove((int) x, (int) y);
    }

    public static void handleOpenPayload(WebviewPayloads.OpenWebS2CPayload payload) {
        if (payload.protocolVersion() != WebviewNetworking.PROTOCOL_VERSION) return;
        Minecraft client = Minecraft.getInstance();
        if (!MCEF.isInitialized()) {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.translatable("message.webgui.mcef_not_ready"));
            }
            return;
        }
        String u = payload.url() == null || payload.url().isBlank() ? StartUrls.primary() : payload.url();
        if (payload.displayMode() == WebviewNetworking.MODE_GUI) {
            client.setScreen(new WebViewScreen(u));
        } else if (payload.displayMode() == WebviewNetworking.MODE_HUD) {
            WebHudOverlay.applyServerOpen(client, u);
        }
    }
}

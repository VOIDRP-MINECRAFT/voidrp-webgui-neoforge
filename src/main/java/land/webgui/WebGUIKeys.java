package land.webgui;

import land.webgui.compat.ClientCompat;

import com.cinemamod.mcef.MCEF;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class WebGUIKeys {
    private WebGUIKeys() {}

    public static void tick(Minecraft client) {
        WebGUIClientHandlers.tickPendingOpen(client);
        var keyMainMenu = WebGUIClientSetup.keyMainMenu();
        var keyHudInteractive = WebGUIClientSetup.keyHudInteractive();
        var keyHudReload = WebGUIClientSetup.keyHudReload();
        if (keyMainMenu == null || keyHudInteractive == null) return;

        while (keyMainMenu.consumeClick()) {
            tryOpenMainMenu(client);
        }
        while (keyHudInteractive.consumeClick()) {
            if (!WebHudOverlay.isHudVisible() || ClientCompat.screen(client) != null) continue;
            WebHudOverlay.toggleInteractive(client);
        }
        // Reload the active webview, bypassing cache (dev loop / stuck page). Works for the HUD
        // or an open WebViewScreen.
        if (keyHudReload != null) {
            while (keyHudReload.consumeClick()) WebSession.reloadActive();
        }

        // Emit-to-page HUD hotkeys — each just fires a `webgui:<event>` the page reacts to.
        // Add a new one: register a KeyMapping in WebGUIClientSetup + one line here.
        emitHudKey(WebGUIClientSetup.keyHudSlide(),  "hudSlide",  client);
        emitHudKey(WebGUIClientSetup.keyHudNotify(), "notifyAct", client);
    }

    private static void emitHudKey(KeyMapping key, String event, Minecraft client) {
        if (key == null) return;
        while (key.consumeClick()) {
            if (!WebHudOverlay.isHudVisible() || ClientCompat.screen(client) != null) continue;
            WebviewClientEmit.dispatch(event, null);
        }
    }

    private static void tryOpenMainMenu(Minecraft client) {
        if (ClientCompat.screen(client) instanceof WebViewScreen) return;
        if (!MCEF.isInitialized()) {
            WebHudOverlay.hintMcefNotReady(client);
            return;
        }
        ClientCompat.setScreen(client, new WebViewScreen(WebGUIMainMenuUrl.getUrl()));
    }
}

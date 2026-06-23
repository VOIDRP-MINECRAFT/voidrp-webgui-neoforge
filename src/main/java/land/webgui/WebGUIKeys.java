package land.webgui;

import com.cinemamod.mcef.MCEF;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class WebGUIKeys {
    private WebGUIKeys() {}

    public static void tick(Minecraft client) {
        var keyMainMenu = WebGUIClientSetup.keyMainMenu();
        var keyHudInteractive = WebGUIClientSetup.keyHudInteractive();
        if (keyMainMenu == null || keyHudInteractive == null) return;

        while (keyMainMenu.consumeClick()) {
            tryOpenMainMenu(client);
        }
        while (keyHudInteractive.consumeClick()) {
            if (!WebHudOverlay.isHudVisible() || client.screen != null) continue;
            WebHudOverlay.toggleInteractive(client);
        }
    }

    private static void tryOpenMainMenu(Minecraft client) {
        if (client.screen instanceof WebViewScreen) return;
        if (!MCEF.isInitialized()) {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.translatable("message.webgui.mcef_not_ready"));
            }
            return;
        }
        client.setScreen(new WebViewScreen(WebGUIMainMenuUrl.getUrl()));
    }
}

package land.webgui;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

public final class WebGUIClientForgeEvents {

    @SubscribeEvent
    public void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        // Tick-based fallback: if MCEF finished initializing but scheduleForInit missed it
        // (race between Connector Fabric init and FMLClientSetupEvent), set up now.
        if (McefBridge.mcefPresent && !McefBridge.isMcefInitialized() && MCEF.isInitialized()) {
            McefSetup.setupNow();
        }
        WebHudOverlay.tickCursor(client);
        WebGUIKeys.tick(client);
        WebviewClientBridge.tick(client);
    }

    @SubscribeEvent
    public void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft client = Minecraft.getInstance();
        if (!WebHudOverlay.shouldDeliverHudBrowserInput(client)) return;

        var win = client.getWindow();
        double mx = client.mouseHandler.xpos() / win.getGuiScaledWidth();
        double my = client.mouseHandler.ypos() / win.getGuiScaledHeight();
        double scaledX = client.mouseHandler.xpos() / win.getGuiScale();
        double scaledY = client.mouseHandler.ypos() / win.getGuiScale();

        if (!WebHudOverlay.containsMouse(scaledX, scaledY, client)) return;

        MCEFBrowser browser = WebSession.browser();
        if (browser != null) {
            int lx = (int) client.mouseHandler.xpos();
            int ly = (int) client.mouseHandler.ypos();
            browser.setFocus(true);
            if (event.getAction() == GLFW.GLFW_PRESS) {
                browser.sendMousePress(lx, ly, event.getButton());
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                browser.sendMouseRelease(lx, ly, event.getButton());
            }
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (!WebHudOverlay.shouldDeliverHudBrowserInput(client)) return;

        double scaledX = client.mouseHandler.xpos() / client.getWindow().getGuiScale();
        double scaledY = client.mouseHandler.ypos() / client.getWindow().getGuiScale();
        if (!WebHudOverlay.containsMouse(scaledX, scaledY, client)) return;

        MCEFBrowser browser = WebSession.browser();
        if (browser == null) return;

        int lx = (int) client.mouseHandler.xpos();
        int ly = (int) client.mouseHandler.ypos();
        browser.setFocus(true);
        browser.sendMouseWheel(lx, ly, (int) event.getScrollDeltaY(), (int) event.getScrollDeltaX());
        event.setCanceled(true);
    }
}

package land.webgui;

import com.cinemamod.mcef.MCEFBrowser;
import land.webgui.render.WebTextureRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Версионный хук отрисовки HUD-браузера поверх интерфейса (26.2). */
public final class WebHudRenderHook {

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        MCEFBrowser browser = WebHudOverlay.renderableHudBrowser(client);
        if (browser == null) return;
        int texId = browser.getRenderer().getTextureID();
        if (texId == 0) return;
        WebTextureRenderer.blitFullTexture(
                event.getGuiGraphics(), texId,
                client.getWindow().getWidth(), client.getWindow().getHeight(),
                client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
    }
}

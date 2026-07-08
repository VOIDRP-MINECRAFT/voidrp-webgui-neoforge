package land.webgui;

import land.webgui.compat.Compat;

import com.cinemamod.mcef.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class WebHudOverlay {
    private static boolean hudVisible;
    private static boolean hudPageReady;
    private static boolean hudInteractive;
    private static int lastPixelW = -1;
    private static int lastPixelH = -1;
    private static boolean cursorUnlockedForWebHud;
    private static boolean restoreHudAfterGuiClose;

    private WebHudOverlay() {}

    public static boolean isHudVisible() { return hudVisible; }
    public static boolean isHudInteractive() { return hudInteractive; }
    public static void setHudInteractive(boolean value) { hudInteractive = value; }
    public static boolean isHudPageReady() { return hudPageReady; }

    public static boolean shouldDeliverHudBrowserInput(Minecraft client) {
        if (client == null || !McefBridge.isMcefInitialized()) return false;
        if (!hudVisible || !hudInteractive || Compat.screen(client) != null) return false;
        if (WebSession.mode() != WebSession.Mode.HUD_OVERLAY) return false;
        return WebSession.browser() != null;
    }

    public static boolean shouldForwardHudArrowKeys(Minecraft client) {
        if (client == null || !McefBridge.isMcefInitialized()) return false;
        if (!hudVisible || Compat.screen(client) != null) return false;
        if (hudInteractive) return false;
        if (WebSession.mode() != WebSession.Mode.HUD_OVERLAY) return false;
        return WebSession.browser() != null;
    }

    public static boolean shouldBlockVanillaWorldInteractions(Minecraft client) {
        if (client == null || Compat.screen(client) != null) return false;
        if (!hudVisible || !hudInteractive) return false;
        return client.level != null;
    }

    public static void tickCursor(Minecraft client) {
        if (Compat.screen(client) != null) return;
        if (!hudVisible) {
            if (cursorUnlockedForWebHud) {
                client.mouseHandler.grabMouse();
                cursorUnlockedForWebHud = false;
            }
            return;
        }
        if (hudInteractive) {
            client.mouseHandler.releaseMouse();
            cursorUnlockedForWebHud = true;
            return;
        }
        if (cursorUnlockedForWebHud) {
            client.mouseHandler.grabMouse();
            cursorUnlockedForWebHud = false;
        }
    }

    public static void applyServerOpen(Minecraft client, String url) {
        String u = url == null || url.isBlank() ? StartUrls.primary() : url;
        if (Compat.screen(client) != null) {
            Compat.setScreen(client, null);
        }
        restoreHudAfterGuiClose = false;
        hudVisible = true;
        hudPageReady = false;
        hudInteractive = false;
        lastPixelW = -1;
        lastPixelH = -1;
        WebSession.openForHud(u);
        resizeBrowser(client);
    }

    public static void toggleHud(Minecraft client) {
        if (Compat.screen(client) instanceof WebViewScreen) return;
        if (!McefBridge.isMcefInitialized()) {
            notifyMcefMissing(client);
            return;
        }
        if (hudVisible) {
            hudVisible = false;
            hudPageReady = false;
            hudInteractive = false;
            WebSession.closeHudOnly();
            lastPixelW = -1;
            lastPixelH = -1;
            if (cursorUnlockedForWebHud) {
                client.mouseHandler.grabMouse();
                cursorUnlockedForWebHud = false;
            }
        } else {
            hudVisible = true;
            hudPageReady = false;
            hudInteractive = false;
            WebSession.openForHud(StartUrls.primary());
            resizeBrowser(client);
        }
    }

    public static void toggleInteractive(Minecraft client) {
        if (!McefBridge.isMcefInitialized()) return;
        if (!hudVisible || Compat.screen(client) != null) return;
        hudInteractive = !hudInteractive;
        if (hudInteractive) {
            client.mouseHandler.releaseMouse();
            cursorUnlockedForWebHud = true;
        } else if (cursorUnlockedForWebHud) {
            client.mouseHandler.grabMouse();
            cursorUnlockedForWebHud = false;
        }
    }

    public static void onGuiOpened() {
        restoreHudAfterGuiClose = hudVisible && WebSession.mode() == WebSession.Mode.HUD_OVERLAY;
        hudInteractive = false;
        lastPixelW = -1;
        lastPixelH = -1;
        Minecraft c = Minecraft.getInstance();
        if (cursorUnlockedForWebHud && c != null) {
            c.mouseHandler.grabMouse();
            cursorUnlockedForWebHud = false;
        }
    }

    public static void onGuiClosed(Minecraft client) {
        if (!restoreHudAfterGuiClose) return;
        restoreHudAfterGuiClose = false;
        if (WebSession.mode() == WebSession.Mode.HUD_OVERLAY && WebSession.browser() != null) {
            hudVisible = true;
            hudInteractive = false;
            lastPixelW = -1;
            lastPixelH = -1;
            resizeBrowser(client);
        }
    }

    static void onHudBrowserLoadStart(MCEFBrowser browser) {
        if (browser == null) return;
        if (WebSession.mode() == WebSession.Mode.HUD_OVERLAY && browser == WebSession.browser()) {
            hudPageReady = false;
        }
    }

    static void onHudBrowserLoadFinished(MCEFBrowser browser) {
        if (browser == null) return;
        if (WebSession.mode() == WebSession.Mode.HUD_OVERLAY && browser == WebSession.browser()) {
            hudPageReady = true;
        }
    }

    /**
     * Готовый к отрисовке HUD-браузер (или null). Версионные рендер-хуки
     * берут его отсюда — вся общая логика остаётся здесь.
     */
    public static MCEFBrowser renderableHudBrowser(Minecraft client) {
        if (!hudVisible) return null;
        MCEFBrowser browser = WebSession.hudBrowser();
        if (browser == null) return null;
        resizeBrowser(client);
        if (!hudPageReady) return null;
        if (!browser.isTextureReady()) return null;
        return browser;
    }

    public static boolean containsMouse(double mouseX, double mouseY, Minecraft client) {
        if (!hudVisible) return false;
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        return mouseX >= 0 && mouseY >= 0 && mouseX < sw && mouseY < sh;
    }

    static void resizeBrowser(Minecraft client) {
        MCEFBrowser browser = WebSession.hudBrowser();
        if (browser == null) return;
        int pxW = client.getWindow().getWidth();
        int pxH = client.getWindow().getHeight();
        if (pxW != lastPixelW || pxH != lastPixelH) {
            browser.resize(pxW, pxH);
            lastPixelW = pxW;
            lastPixelH = pxH;
        }
    }

    private static void notifyMcefMissing(Minecraft client) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.translatable("message.webgui.mcef_not_ready"));
        }
    }
}

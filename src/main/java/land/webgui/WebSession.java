package land.webgui;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;

public final class WebSession {
    private static MCEFBrowser browser;
    private static MCEFBrowser suspendedHudBrowser;
    private static Mode mode = Mode.NONE;

    public enum Mode { NONE, GUI_SCREEN, HUD_OVERLAY }

    private WebSession() {}

    public static MCEFBrowser browser() { return browser; }
    public static Mode mode() { return mode; }

    /**
     * Append a per-open cache-buster so CEF always fetches the latest HTML (which references
     * the newest hashed JS/CSS — those stay cached). Applied to EVERY page open (menu, HUD,
     * command-opened webgui pages, notification open_gui …), so a fresh frontend build shows
     * up without any nginx/plugin change. The single knob for "always serve the latest build".
     */
    private static String cacheBust(String url) {
        if (url == null || url.isBlank()) return url;
        return url + (url.contains("?") ? "&" : "?") + "_v=" + System.currentTimeMillis();
    }

    public static MCEFBrowser hudBrowser() {
        if (mode == Mode.HUD_OVERLAY) return browser;
        if (mode == Mode.GUI_SCREEN) return suspendedHudBrowser;
        return null;
    }

    public static void dispose() {
        closeActiveBrowser();
        closeSuspendedHudBrowser();
        mode = Mode.NONE;
        WebviewClientBridge.clearCache();
    }

    private static void closeActiveBrowser() {
        if (browser != null) { browser.close(); browser = null; }
    }

    private static void closeSuspendedHudBrowser() {
        if (suspendedHudBrowser != null) { suspendedHudBrowser.close(); suspendedHudBrowser = null; }
    }

    public static void closeGuiAndRestoreHud() {
        if (mode != Mode.GUI_SCREEN) return;
        closeActiveBrowser();
        if (suspendedHudBrowser != null) {
            browser = suspendedHudBrowser;
            suspendedHudBrowser = null;
            mode = Mode.HUD_OVERLAY;
        } else {
            mode = Mode.NONE;
        }
        WebviewClientBridge.clearCache();
    }

    public static MCEFBrowser openForGui(String url) {
        closeSuspendedHudBrowser();
        if (mode == Mode.HUD_OVERLAY && browser != null) {
            suspendedHudBrowser = browser;
            browser = null;
        } else {
            closeActiveBrowser();
        }
        browser = MCEF.createBrowser(cacheBust(url), true);
        mode = Mode.GUI_SCREEN;
        WebviewClientBridge.clearCache();
        return browser;
    }

    public static MCEFBrowser openForHud(String url) {
        closeSuspendedHudBrowser();
        if (mode == Mode.HUD_OVERLAY && browser != null) {
            browser.loadURL(cacheBust(url));
            WebviewClientBridge.clearCache();
            return browser;
        }
        closeActiveBrowser();
        browser = MCEF.createBrowser(cacheBust(url), true);
        mode = Mode.HUD_OVERLAY;
        WebviewClientBridge.clearCache();
        return browser;
    }

    public static void closeHudOnly() {
        if (mode == Mode.HUD_OVERLAY) { closeActiveBrowser(); mode = Mode.NONE; }
        closeSuspendedHudBrowser();
    }

    /** Reload the active webview, bypassing the cache (dev convenience / stuck-page escape). */
    public static void reloadActive() {
        MCEFBrowser b = browser;
        if (b == null) return;
        try {
            b.reloadIgnoreCache();
        } catch (Throwable t) {
            // Fallback for MCEF forks without reloadIgnoreCache: re-load with a fresh buster.
            String u = b.getURL();
            if (u != null && !u.isBlank()) b.loadURL(cacheBust(u.replaceAll("[?&]_v=\\d+", "")));
        }
        WebviewClientBridge.clearCache();
    }
}

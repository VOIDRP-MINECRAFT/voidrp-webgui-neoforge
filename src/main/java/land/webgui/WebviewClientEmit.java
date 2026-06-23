package land.webgui;

import com.cinemamod.mcef.MCEFBrowser;
import com.google.gson.Gson;

public final class WebviewClientEmit {
    private static final Gson GSON = new Gson();
    private WebviewClientEmit() {}

    public static void dispatch(String eventName, String jsonPayload) {
        MCEFBrowser main = WebSession.browser();
        MCEFBrowser hud  = WebSession.hudBrowser();
        if (main == null && hud == null) return;

        String encodedName = GSON.toJson(eventName);
        String data = (jsonPayload == null || jsonPayload.isBlank()) ? "null" : jsonPayload;

        String js = "(function(){"
                + "var n=" + encodedName + ";"
                + "var d=" + data + ";"
                + "window.dispatchEvent(new CustomEvent('webgui:'+n,{detail:d}));"
                + "if(window.webgui&&window.webgui._hs&&window.webgui._hs[n])"
                + "{window.webgui._hs[n].forEach(function(e){try{e.w({detail:d});}catch(x){}});}"
                + "})();";

        if (main != null) executeJs(main, js);
        if (hud != null && hud != main) executeJs(hud, js);
    }

    private static void executeJs(MCEFBrowser browser, String js) {
        try {
            String url = browser.getURL();
            browser.executeJavaScript(js, url != null ? url : "", 0);
        } catch (Throwable t) {
            WebGUIMod.LOGGER.debug("webgui emit dispatch: {}", t.toString());
        }
    }
}

package land.webgui;

import com.cinemamod.mcef.MCEF;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.slf4j.Logger;

// Loaded only from WebGUIClientSetup.onClientSetup, after confirming MCEF is present.
// Kept separate from McefBridge so that org.cef.* references never appear in the
// Mixin injection call chain, where they would trigger NoClassDefFoundError before
// jcef is added to the classpath by MCEF.
final class McefSetup {
    private McefSetup() {}

    private static volatile boolean setupDone = false;

    static void scheduleInit() {
        // Register listener for future initialization
        MCEF.scheduleForInit(success -> {
            if (!success) {
                WebGUIMod.LOGGER.error("MCEF (Chromium) failed to initialize — web GUI will not work.");
                return;
            }
            setupNow();
        });
        // keksuccino MCEF initializes during Connector Fabric init, which runs before
        // FMLClientSetupEvent fires. If that already happened, scheduleForInit adds to
        // a cleared list and the callback never fires — check isInitialized() as a fallback.
        if (MCEF.isInitialized()) {
            setupNow();
        }
    }

    // Called from tick event as a second fallback for the narrow TOCTOU window
    // between the isInitialized() check above and the scheduleForInit() call.
    static void setupNow() {
        if (setupDone) return;
        setupDone = true;
        doSetup();
    }

    private static void doSetup() {
        Logger log = WebGUIMod.LOGGER;
        MCEF.getClient().addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level,
                                            String message, String source, int line) {
                String text = source != null && !source.isEmpty()
                        ? (line > 0 ? source + ":" + line + " " : source + " ") + (message != null ? message : "")
                        : (message != null ? message : "");
                if (level == null) level = CefSettings.LogSeverity.LOGSEVERITY_DEFAULT;
                switch (level) {
                    case LOGSEVERITY_ERROR, LOGSEVERITY_FATAL -> log.error("[Web GUI] {}", text);
                    case LOGSEVERITY_WARNING                  -> log.warn("[Web GUI] {}", text);
                    case LOGSEVERITY_VERBOSE                  -> log.debug("[Web GUI] {}", text);
                    case LOGSEVERITY_DISABLE                  -> {}
                    default                                   -> log.info("[Web GUI] {}", text);
                }
                return false;
            }
        });
        WebviewPageToClientBridge.register();
        WebviewPageLoadHooks.register();
        McefBridge.markInitialized();
        log.info("WebGUI bridge ready (console log, page↔game, client data).");
    }
}

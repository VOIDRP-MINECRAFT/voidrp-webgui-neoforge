package land.webgui;

import land.webgui.compat.Compat;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

public final class WebviewPageToClientBridge {
    private WebviewPageToClientBridge() {}

    public static void register() {
        CefMessageRouter router = CefMessageRouter.create();
        router.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId,
                                   String request, boolean persistent, CefQueryCallback callback) {
                try {
                    dispatch(request, callback);
                } catch (Throwable t) {
                    WebGUIMod.LOGGER.warn("[webgui page→game] handler error", t);
                    callback.failure(-1, t.getMessage() != null ? t.getMessage() : "error");
                }
                return true;
            }
        }, true);
        MCEF.getClient().getHandle().addMessageRouter(router);
    }

    private static void dispatch(String request, CefQueryCallback callback) {
        if (request == null || request.isBlank()) {
            callback.failure(-2, "empty request");
            return;
        }

        JsonObject obj = tryParseObject(request);

        if (obj == null) {
            WebGUIMod.LOGGER.info("[webgui page→game] {}", request);
            callback.success("{\"ok\":true}");
            return;
        }

        String channel = obj.has("channel") && !obj.get("channel").isJsonNull()
                ? obj.get("channel").getAsString()
                : "message";

        switch (channel) {
            case "log" -> {
                String level = obj.has("level") ? obj.get("level").getAsString() : "info";
                String msg   = obj.has("message") ? obj.get("message").getAsString() : request;
                log(level, msg);
            }
            case "close" -> {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (Compat.screen(mc) instanceof WebViewScreen) {
                        Compat.screen(mc).onClose();
                    } else if (WebHudOverlay.isHudVisible()) {
                        WebHudOverlay.toggleHud(mc);
                    }
                });
            }
            case "open_gui" -> {
                String url = obj.has("url") ? obj.get("url").getAsString() : null;
                if (url != null && !url.isBlank()) {
                    final String finalUrl = url;
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> Compat.setScreen(mc, new WebViewScreen(finalUrl)));
                }
            }
            case "open_hud" -> {
                String url = obj.has("url") ? obj.get("url").getAsString() : null;
                if (url != null && !url.isBlank()) {
                    final String finalUrl = url;
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> WebHudOverlay.applyServerOpen(mc, finalUrl));
                }
            }
            case "run_command" -> {
                String cmd = obj.has("command") ? obj.get("command").getAsString() : null;
                if (cmd != null && !cmd.isBlank()) {
                    String cmdClean = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        if (mc.getConnection() != null) mc.getConnection().sendCommand(cmdClean);
                    });
                }
            }
            default -> {
                if (request.length() > WebviewPayloads.MAX_EVENT_DATA_LENGTH) {
                    WebGUIMod.LOGGER.warn("[webgui page→game] [{}] payload too large ({} bytes), dropping", channel, request.length());
                    break;
                }
                Minecraft mc = Minecraft.getInstance();
                if (mc.getConnection() != null) {
                    String ch  = channel;
                    String pay = request;
                    mc.execute(() -> sendToServer(ch, pay));
                } else {
                    WebGUIMod.LOGGER.info("[webgui page→game] [{}] {}", channel, request);
                }
            }
        }

        callback.success("{\"ok\":true}");
    }

    private static JsonObject tryParseObject(String text) {
        try {
            JsonElement el = JsonParser.parseString(text);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    private static void sendToServer(String channel, String jsonPayload) {
        Compat.sendToServer(new WebviewPayloads.WebviewPageEventC2SPayload(channel, jsonPayload));
    }

    private static void log(String level, String msg) {
        String line = "[webgui page→game] " + msg;
        switch (level.toLowerCase()) {
            case "warn", "warning" -> WebGUIMod.LOGGER.warn(line);
            case "error"           -> WebGUIMod.LOGGER.error(line);
            case "debug"           -> WebGUIMod.LOGGER.debug(line);
            default                -> WebGUIMod.LOGGER.info(line);
        }
    }
}

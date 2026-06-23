package land.webgui;

import land.webgui.server.WebviewServerConfig;
import land.webgui.server.WebviewServerEvents;
import land.webgui.server.WebviewSignedToken;
import land.webgui.server.WebviewUrlBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class WebviewNetworking {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MODE_GUI = 0;
    public static final int MODE_HUD = 1;
    public static final int MAX_URL_LENGTH = 16384;

    private WebviewNetworking() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(WebviewNetworking::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // .optional() — channels do not need to be present on either side; no disconnect on mismatch.
        PayloadRegistrar registrar = event.registrar("1").optional();

        registrar.playToClient(
                WebviewPayloads.OpenWebS2CPayload.TYPE,
                WebviewPayloads.OpenWebS2CPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> WebGUIClientHandlers.handleOpenPayload(payload)));

        registrar.playToClient(
                WebviewPayloads.WebUIMainMenuPayload.TYPE,
                WebviewPayloads.WebUIMainMenuPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> WebGUIMainMenuUrl.setUrl(payload.url())));

        registrar.playToClient(
                WebviewPayloads.WebviewEmitS2CPayload.TYPE,
                WebviewPayloads.WebviewEmitS2CPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> WebviewClientEmit.dispatch(payload.eventName(), payload.jsonPayload())));

        registrar.playToClient(
                WebviewPayloads.WebviewEntityContextS2CPayload.TYPE,
                WebviewPayloads.WebviewEntityContextS2CPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> WebviewClientBridge.setEntityContext(payload.entityJson())));

        registrar.playToServer(
                WebviewPayloads.WebviewPageEventC2SPayload.TYPE,
                WebviewPayloads.WebviewPageEventC2SPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() ->
                        WebviewServerEvents.fire((ServerPlayer) ctx.player(), payload.channel(), payload.jsonPayload())));
    }

    public static void openGui(ServerPlayer player, String url) {
        clearEntityContext(player);
        PacketDistributor.sendToPlayer(player,
                new WebviewPayloads.OpenWebS2CPayload(PROTOCOL_VERSION, MODE_GUI, withPlayerToken(player, url)));
    }

    public static void openHud(ServerPlayer player, String url) {
        clearEntityContext(player);
        PacketDistributor.sendToPlayer(player,
                new WebviewPayloads.OpenWebS2CPayload(PROTOCOL_VERSION, MODE_HUD, withPlayerToken(player, url)));
    }

    public static void openGuiForEntity(ServerPlayer player, String url, String entityJson) {
        sendEntityContext(player, entityJson);
        PacketDistributor.sendToPlayer(player,
                new WebviewPayloads.OpenWebS2CPayload(PROTOCOL_VERSION, MODE_GUI, withPlayerToken(player, url)));
    }

    public static void sendEntityContext(ServerPlayer player, String entityJson) {
        PacketDistributor.sendToPlayer(player, new WebviewPayloads.WebviewEntityContextS2CPayload(entityJson));
    }

    public static void clearEntityContext(ServerPlayer player) {
        sendEntityContext(player, "null");
    }

    public static void emitToPage(ServerPlayer player, String eventName, String jsonPayload) {
        String name = sanitizeStr(eventName, WebviewPayloads.MAX_EVENT_NAME_LENGTH);
        String data = (jsonPayload == null || jsonPayload.isBlank()) ? "null" : sanitizeStr(jsonPayload, WebviewPayloads.MAX_EVENT_DATA_LENGTH);
        PacketDistributor.sendToPlayer(player, new WebviewPayloads.WebviewEmitS2CPayload(name, data));
    }

    public static void sendMainMenuUrl(ServerPlayer player, String url) {
        PacketDistributor.sendToPlayer(player, new WebviewPayloads.WebUIMainMenuPayload(sanitizeUrl(url)));
    }

    private static String withPlayerToken(ServerPlayer player, String url) {
        if (!WebviewServerConfig.enableTokens()) {
            return sanitizeUrl(url);
        }
        String token = WebviewSignedToken.create(player);
        if (token.isEmpty()) {
            return sanitizeUrl(url);
        }
        String withParam = WebviewUrlBuilder.appendQueryParam(url == null ? "" : url, WebviewServerConfig.queryParamName(), token);
        return sanitizeUrl(withParam);
    }

    private static String sanitizeUrl(String url) {
        if (url == null) return "";
        return url.length() > MAX_URL_LENGTH ? url.substring(0, MAX_URL_LENGTH) : url;
    }

    private static String sanitizeStr(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}

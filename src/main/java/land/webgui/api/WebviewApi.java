package land.webgui.api;

import land.webgui.EntityBindingStore;
import land.webgui.WebviewNetworking;
import land.webgui.server.EntityBinding;
import land.webgui.server.WebviewServerEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Call from the server thread. */
public final class WebviewApi {
    private WebviewApi() {}

    public static void openGui(ServerPlayer player, String url) {
        WebviewNetworking.openGui(player, url);
    }

    public static void openHud(ServerPlayer player, String url) {
        WebviewNetworking.openHud(player, url);
    }

    public static void sendMainMenuUrl(ServerPlayer player, String url) {
        WebviewNetworking.sendMainMenuUrl(player, url);
    }

    public static void emitToPage(ServerPlayer player, String eventName, String jsonPayload) {
        WebviewNetworking.emitToPage(player, eventName, jsonPayload);
    }

    public static void onPageEvent(String channel, BiConsumer<ServerPlayer, String> handler) {
        WebviewServerEvents.register((player, ch, payload) -> {
            if (ch.equals(channel)) handler.accept(player, payload);
        });
    }

    public static void bindEntity(UUID entityUuid, String urlTemplate, boolean cancelInteraction) {
        EntityBindingStore.bind(entityUuid, new EntityBinding(urlTemplate, cancelInteraction));
    }

    public static void unbindEntity(UUID entityUuid) {
        EntityBindingStore.unbind(entityUuid);
    }

    public static Optional<EntityBinding> getEntityBinding(UUID entityUuid) {
        return EntityBindingStore.get(entityUuid);
    }

    public static int maxUrlLength() { return WebviewNetworking.MAX_URL_LENGTH; }
    public static int protocolVersion() { return WebviewNetworking.PROTOCOL_VERSION; }
}

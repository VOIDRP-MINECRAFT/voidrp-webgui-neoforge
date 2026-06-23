package land.webgui.server;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WebviewServerEvents {
    private WebviewServerEvents() {}

    private static final List<PageEventHandler> handlers = new CopyOnWriteArrayList<>();

    public static void register(PageEventHandler handler) {
        handlers.add(handler);
    }

    public static void fire(ServerPlayer player, String channel, String payload) {
        for (PageEventHandler h : handlers) {
            h.onPageEvent(player, channel, payload);
        }
    }

    @FunctionalInterface
    public interface PageEventHandler {
        void onPageEvent(ServerPlayer player, String channel, String payload);
    }
}

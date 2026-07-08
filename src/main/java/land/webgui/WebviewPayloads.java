package land.webgui;

import land.webgui.compat.Compat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class WebviewPayloads {
    private WebviewPayloads() {}

    public static final int MAX_EVENT_NAME_LENGTH = 256;
    public static final int MAX_EVENT_DATA_LENGTH = 32_768;

    /** S2C: server emits a named event to the page. */
    public record WebviewEmitS2CPayload(String eventName, String jsonPayload) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewEmitS2CPayload> TYPE =
                Compat.payloadType("emit_to_page");
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewEmitS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_EVENT_NAME_LENGTH), WebviewEmitS2CPayload::eventName,
                        ByteBufCodecs.stringUtf8(MAX_EVENT_DATA_LENGTH), WebviewEmitS2CPayload::jsonPayload,
                        WebviewEmitS2CPayload::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: page sends a named event to the server. */
    public record WebviewPageEventC2SPayload(String channel, String jsonPayload) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewPageEventC2SPayload> TYPE =
                Compat.payloadType("page_event");
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewPageEventC2SPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_EVENT_NAME_LENGTH), WebviewPageEventC2SPayload::channel,
                        ByteBufCodecs.stringUtf8(MAX_EVENT_DATA_LENGTH), WebviewPageEventC2SPayload::jsonPayload,
                        WebviewPageEventC2SPayload::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** displayMode: 0 = GUI, 1 = HUD */
    public record OpenWebS2CPayload(int protocolVersion, int displayMode, String url) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenWebS2CPayload> TYPE =
                Compat.payloadType("open_web");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenWebS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT,  OpenWebS2CPayload::protocolVersion,
                        ByteBufCodecs.VAR_INT,  OpenWebS2CPayload::displayMode,
                        ByteBufCodecs.stringUtf8(WebviewNetworking.MAX_URL_LENGTH), OpenWebS2CPayload::url,
                        OpenWebS2CPayload::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record WebUIMainMenuPayload(String url) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebUIMainMenuPayload> TYPE =
                Compat.payloadType("set_main_menu");
        public static final StreamCodec<RegistryFriendlyByteBuf, WebUIMainMenuPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(WebviewNetworking.MAX_URL_LENGTH), WebUIMainMenuPayload::url,
                        WebUIMainMenuPayload::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S2C: sets (or clears) the entity context for the currently open GUI. entityJson == "null" clears it. */
    public record WebviewEntityContextS2CPayload(String entityJson) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewEntityContextS2CPayload> TYPE =
                Compat.payloadType("entity_context");
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewEntityContextS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_EVENT_DATA_LENGTH), WebviewEntityContextS2CPayload::entityJson,
                        WebviewEntityContextS2CPayload::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}

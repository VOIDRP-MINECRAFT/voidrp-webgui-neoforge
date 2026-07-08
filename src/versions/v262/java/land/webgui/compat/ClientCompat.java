package land.webgui.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import land.webgui.WebGUIMod;

/**
 * Клиентская часть версионного адаптера (Minecraft 26.2). Ссылается на
 * client-only классы — НЕ должен грузиться на dedicated-сервере
 * (вызывается только из клиентского кода).
 */
public final class ClientCompat {
    private ClientCompat() {}

    private static final KeyMapping.Category KEY_CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "main"));

    public static Screen screen(Minecraft client) {
        return client.gui.screen();
    }

    public static void setScreen(Minecraft client, Screen screen) {
        client.gui.setScreen(screen);
    }

    /** Отправка пейлоада с клиента на сервер. */
    public static void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    /** GLFW-хэндл окна. */
    public static long windowHandle(Window window) {
        return window.handle();
    }

    public static void registerKeyCategory(RegisterKeyMappingsEvent event) {
        event.registerCategory(KEY_CATEGORY);
    }

    public static KeyMapping keyMapping(String name, int glfwKey) {
        return new KeyMapping(name, InputConstants.Type.KEYSYM, glfwKey, KEY_CATEGORY);
    }
}

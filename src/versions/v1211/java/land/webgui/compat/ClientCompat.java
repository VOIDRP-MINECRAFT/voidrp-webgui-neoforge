package land.webgui.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиентская часть версионного адаптера (Minecraft 1.21.1). Ссылается на
 * client-only классы — НЕ должен грузиться на dedicated-сервере
 * (вызывается только из клиентского кода).
 */
public final class ClientCompat {
    private ClientCompat() {}

    public static Screen screen(Minecraft client) {
        return client.screen;
    }

    public static void setScreen(Minecraft client, Screen screen) {
        client.setScreen(screen);
    }

    /** Отправка пейлоада с клиента на сервер. */
    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** GLFW-хэндл окна. */
    public static long windowHandle(Window window) {
        return window.getWindow();
    }

    public static void registerKeyCategory(RegisterKeyMappingsEvent event) {
        // 1.21.1: категория — просто строка перевода, регистрировать нечего.
    }

    public static KeyMapping keyMapping(String name, int glfwKey) {
        return new KeyMapping(name, InputConstants.Type.KEYSYM, glfwKey, "key.categories.webgui");
    }
}

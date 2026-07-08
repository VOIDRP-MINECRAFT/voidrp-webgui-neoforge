package land.webgui.compat;

import java.util.function.Predicate;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import land.webgui.WebGUIMod;

/**
 * Версионный адаптер API (Minecraft 1.21.1 / NeoForge 21.1).
 * Общий код обращается только к этому классу; различия версий живут здесь.
 */
public final class Compat {
    private Compat() {}

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WebGUIMod.MOD_ID, path));
    }

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

    /** Требование уровня прав "оператор" (OP 2) для команд. */
    public static Predicate<CommandSourceStack> opRequirement() {
        return s -> s.hasPermission(2);
    }

    public static String dimensionId(Level level) {
        return level.dimension().location().toString();
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

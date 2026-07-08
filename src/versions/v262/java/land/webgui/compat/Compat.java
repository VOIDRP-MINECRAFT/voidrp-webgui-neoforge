package land.webgui.compat;

import java.util.function.Predicate;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import land.webgui.WebGUIMod;

/**
 * Версионный адаптер API (Minecraft 26.2 / NeoForge 26.2).
 * Общий код обращается только к этому классу; различия версий живут здесь.
 */
public final class Compat {
    private Compat() {}

    private static final KeyMapping.Category KEY_CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "main"));

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, path));
    }

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

    /** Требование уровня прав "оператор" (эквивалент OP 2) для команд. */
    public static Predicate<CommandSourceStack> opRequirement() {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    }

    public static String dimensionId(Level level) {
        return level.dimension().identifier().toString();
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

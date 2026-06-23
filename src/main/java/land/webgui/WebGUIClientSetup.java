package land.webgui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = WebGUIMod.MOD_ID, value = Dist.CLIENT)
public final class WebGUIClientSetup {

    private static KeyMapping keyMainMenu;
    private static KeyMapping keyHudInteractive;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (net.neoforged.fml.ModList.get().isLoaded("mcef")) {
                try {
                    // Probe that the expected CinemaMod MCEF API is actually present.
                    // Forks (e.g. keksuccino Fabric via Connector) may expose mod id "mcef"
                    // but miss this class or have incompatible statics, which would crash
                    // later if we unconditionally reference it.
                    Class.forName("com.cinemamod.mcef.MCEF");
                    McefBridge.mcefPresent = true;
                    McefSetup.scheduleInit();
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new WebGUIClientForgeEvents());
                } catch (Throwable t) {
                    WebGUIMod.LOGGER.warn("MCEF mod found but API is incompatible — in-game browser disabled. ({})", t.toString());
                }
            } else {
                WebGUIMod.LOGGER.warn("MCEF not found — in-game browser features will be disabled.");
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        keyMainMenu = new KeyMapping(
                "key.webgui.main_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "key.categories.webgui");
        keyHudInteractive = new KeyMapping(
                "key.webgui.hud_interactive",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_GRAVE_ACCENT,
                "key.categories.webgui");
        event.register(keyMainMenu);
        event.register(keyHudInteractive);
    }

    public static KeyMapping keyMainMenu() {
        return keyMainMenu;
    }

    public static KeyMapping keyHudInteractive() {
        return keyHudInteractive;
    }
}

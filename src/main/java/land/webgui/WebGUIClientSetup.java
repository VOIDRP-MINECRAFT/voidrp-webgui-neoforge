package land.webgui;

import land.webgui.compat.ClientCompat;

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
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new WebHudRenderHook());
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
        ClientCompat.registerKeyCategory(event);
        keyMainMenu = ClientCompat.keyMapping("key.webgui.main_menu", GLFW.GLFW_KEY_F6);
        keyHudInteractive = ClientCompat.keyMapping("key.webgui.hud_interactive", GLFW.GLFW_KEY_GRAVE_ACCENT);
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

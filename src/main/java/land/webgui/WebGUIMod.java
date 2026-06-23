package land.webgui;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(WebGUIMod.MOD_ID)
public final class WebGUIMod {
    public static final String MOD_ID = "webgui";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WebGUIMod(IEventBus modBus, ModContainer container) {
        WebviewNetworking.register(modBus);

        NeoForge.EVENT_BUS.register(new WebGUIForgeEvents());

        LOGGER.info("WebGUI (NeoForge) loaded — channels registered as optional.");
    }
}

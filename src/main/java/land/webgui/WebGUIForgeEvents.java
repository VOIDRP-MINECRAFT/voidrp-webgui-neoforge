package land.webgui;

import land.webgui.server.EntityBinding;
import land.webgui.server.WebviewEntityContext;
import land.webgui.server.WebviewPlaceholders;
import land.webgui.server.WebviewServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Optional;

public final class WebGUIForgeEvents {

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        WebviewServerConfig.load();
        EntityBindingStore.load();
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        land.webgui.server.WebGUIUpdateChecker.checkAsync();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        String mainMenuUrl = WebviewServerConfig.mainMenuUrl();
        if (!mainMenuUrl.isEmpty()) {
            WebviewNetworking.sendMainMenuUrl(player, mainMenuUrl);
        }

        if (!WebviewServerConfig.autoHudOnJoin()) return;
        String url = WebviewServerConfig.autoHudUrl();
        if (url.isEmpty()) {
            WebGUIMod.LOGGER.warn("webgui: autoHudOnJoin is true but autoHudUrl is empty");
            return;
        }
        WebviewNetworking.openHud(player, url);
        WebGUIMod.LOGGER.info("webgui: auto HUD for {} → {}", player.getName().getString(), url);
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        Optional<EntityBinding> opt = EntityBindingStore.get(event.getTarget().getUUID());
        if (opt.isEmpty()) return;

        EntityBinding b = opt.get();
        String url = WebviewPlaceholders.resolve(b.urlTemplate(), sp, event.getTarget());
        String entityJson = WebviewEntityContext.buildJson(event.getTarget());
        WebviewNetworking.openGuiForEntity(sp, url, entityJson);

        if (b.cancelInteraction()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        WebviewCommands.register(event.getDispatcher());
    }
}

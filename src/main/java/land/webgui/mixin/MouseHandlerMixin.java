package land.webgui.mixin;

import land.webgui.WebGUIClientHandlers;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onMove", at = @At("HEAD"))
    private void webgui$moveToWebHud(long window, double x, double y, CallbackInfo ci) {
        WebGUIClientHandlers.onMouseMove(window, x, y);
    }
}

package land.webgui.mixin;

import com.mojang.blaze3d.platform.Window;
import land.webgui.render.FrameProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowSwapProbeMixin {

    // The finished frame, just before it goes to the screen.
    @Inject(method = "updateDisplay", at = @At("HEAD"), require = 0)
    private void webgui$probeBeforeSwap(CallbackInfo ci) {
        FrameProbe.beforeSwap();
    }
}

package land.webgui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.cinemamod.mcef.MCEFSettings", remap = false)
public class McefSettingsMixin {

    @Inject(
            method = "getDownloadMirror",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void webgui$overrideDownloadMirror(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("https://void-rp.ru/launcher/mcef");
    }
}

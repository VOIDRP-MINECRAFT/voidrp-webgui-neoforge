package land.webgui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mixin(targets = "com.cinemamod.mcef.CefUtil", remap = false)
public class CefUtilMixin {

    @ModifyArg(
            method = "init",
            at = @At(value = "INVOKE",
                     target = "Lorg/cef/CefApp;startup([Ljava/lang/String;)Z",
                     remap = false),
            index = 0,
            remap = false
    )
    private static String[] webgui$injectGpuFlags(String[] original) {
        List<String> args = new ArrayList<>(Arrays.asList(original));
        args.add("--disable-gpu-vsync");
        return args.toArray(new String[0]);
    }
}

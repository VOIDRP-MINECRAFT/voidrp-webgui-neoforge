package land.webgui.mixin;

import land.webgui.PaintGuard;
import org.cef.browser.CefBrowser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Rectangle;
import java.nio.ByteBuffer;

@Mixin(targets = "com.cinemamod.mcef.MCEFBrowser", remap = false)
public class McefBrowserPaintMixin {

    // A solid black picture from Chromium is not uploaded: the last good one stays on screen.
    @Inject(method = "onPaint", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void webgui$dropBlackPicture(CefBrowser browser, boolean popup, Rectangle[] dirtyRects,
                                         ByteBuffer buffer, int width, int height, CallbackInfo ci) {
        if (PaintGuard.onPaint(this, popup, buffer, width, height)) ci.cancel();
    }

    @Inject(method = "onPaintRenderThread_MCEF", at = @At("RETURN"), remap = false, require = 0)
    private void webgui$resetUnpackState(CallbackInfo ci) {
        PaintGuard.resetUnpackState();
    }
}

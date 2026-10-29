package land.webgui;

import land.webgui.render.FrameProbe;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Black flashes over the game: with the F6 menu open the whole window went black every few
 * frames, each time until the browser's next picture arrived, which then looked fine. Two
 * suspects, both handled here and counted in the log every 10 s while a page is drawn:
 * - Chromium hands over a solid black picture: dropped, the previous picture stays on screen;
 * - the GL state differs from what the game believes when we draw: corrected in the renderer.
 */
public final class PaintGuard {
    private static final long REPORT_EVERY_MS = 10_000;

    // Chromium calls onPaint on its own thread or the render thread.
    private static final AtomicInteger pictures = new AtomicInteger();
    private static final AtomicInteger blackDropped = new AtomicInteger();

    // Render thread only: corrections made by the renderer before a draw, and odd state it saw.
    public static int wrongUnit, wrongTexture, samplerBound, blendOff, blendFunc, depthOn;
    public static int foreignTarget, scissorOn, unpackLeft;
    private static int draws, uploads;
    private static long windowStart;
    private static boolean cleanReported;

    private PaintGuard() {}

    /** A new browser picture; true when it is to be dropped. */
    public static boolean onPaint(Object browser, boolean popup, ByteBuffer buffer, int width, int height) {
        if (browser == null || (browser != WebSession.browser() && browser != WebSession.hudBrowser())) return false;
        pictures.incrementAndGet();
        if (popup || !isSolidBlack(buffer, width, height)) return false;
        blackDropped.incrementAndGet();
        return true;
    }

    /**
     * The buffer always holds the whole view (BGRA). Our pages are never pure opaque black
     * across the screen: empty pages are transparent, the menu background is dark blue.
     */
    static boolean isSolidBlack(ByteBuffer buffer, int width, int height) {
        if (buffer == null || width <= 0 || height <= 0) return false;
        int limit = buffer.limit();
        for (int gy = 0; gy < 6; gy++) {
            int y = (int) ((gy + 0.5) * height / 6);
            for (int gx = 0; gx < 8; gx++) {
                int x = (int) ((gx + 0.5) * width / 8);
                int i = (y * width + x) * 4;
                if (i + 3 >= limit) return false;
                if ((buffer.get(i) & 0xFF) > 3 || (buffer.get(i + 1) & 0xFF) > 3
                        || (buffer.get(i + 2) & 0xFF) > 3 || (buffer.get(i + 3) & 0xFF) < 250) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * MCEF uploads a picture with its own row length and skip offsets and leaves them set, so the
     * next texture upload of anything else in the frame reads from the wrong place. Put the
     * defaults back (render thread, right after the upload).
     */
    public static void resetUnpackState() {
        FrameProbe.onPictureUploaded();
        uploads++;
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
    }

    /** After each draw of a browser picture (render thread): a summary line every 10 s. */
    public static void afterDraw() {
        draws++;
        long now = System.currentTimeMillis();
        if (windowStart == 0) {
            windowStart = now;
            return;
        }
        if (now - windowStart < REPORT_EVERY_MS) return;
        long seconds = Math.max(1, (now - windowStart) / 1000);
        int got = pictures.getAndSet(0);
        int dropped = blackDropped.getAndSet(0);
        int fixed = wrongUnit + wrongTexture + samplerBound + blendOff + blendFunc + depthOn;
        int seen = foreignTarget + scissorOn + unpackLeft;
        if (dropped > 0 || fixed > 0 || seen > 0) {
            WebGUIMod.LOGGER.warn("[WebGUI] last {}s: {} draws, {} browser pictures ({} uploaded), {} solid black dropped; "
                            + "GL corrected before drawing: texture unit {}, texture {}, sampler {}, blend off {}, "
                            + "blend func {}, depth test {}; seen: other framebuffer {}, scissor {}, unpack state {}",
                    seconds, draws, got, uploads, dropped, wrongUnit, wrongTexture, samplerBound, blendOff,
                    blendFunc, depthOn, foreignTarget, scissorOn, unpackLeft);
        } else if (!cleanReported) {
            WebGUIMod.LOGGER.info("[WebGUI] browser drawing checked: {} draws, {} browser pictures ({} uploaded) in {}s, "
                    + "no black pictures, GL state as expected", draws, got, uploads, seconds);
            cleanReported = true;
        }
        draws = uploads = 0;
        wrongUnit = wrongTexture = samplerBound = blendOff = blendFunc = depthOn = 0;
        foreignTarget = scissorOn = unpackLeft = 0;
        windowStart = now;
    }
}

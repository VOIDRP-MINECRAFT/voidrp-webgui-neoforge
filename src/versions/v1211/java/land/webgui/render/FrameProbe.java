package land.webgui.render;

import land.webgui.WebGUIMod;
import land.webgui.WebViewScreen;
import land.webgui.compat.ClientCompat;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;

import java.nio.ByteBuffer;

/**
 * Where does a black frame come from? With the F6 menu open the whole window turns black for a
 * frame or two whenever the page repaints widely, yet the drawing state is right (PaintGuard).
 * This probe reads a few pixels at each stage of every menu frame:
 * - the page texture as the browser left it;
 * - the game image right after the page is drawn;
 * - the game image and the window's back buffer just before the frame goes to the screen.
 * Black on screen with a good back buffer: the frame is lost after the game (driver, Windows).
 * Black back buffer with a good game image: the final copy to the window failed.
 * Black game image: our draw or something after it.
 * Reading pixels makes the CPU wait for the GPU, so it runs only while the menu is open and
 * for one minute per game session.
 */
public final class FrameProbe {
    private static final long BUDGET_MS = 60_000;
    private static final long REPORT_EVERY_MS = 10_000;
    private static final int DETAILED_REPORTS = 3;
    private static final float[][] POINTS = {
            {0.5f, 0.5f}, {0.25f, 0.25f}, {0.75f, 0.25f}, {0.25f, 0.75f}, {0.75f, 0.75f}};

    private static final ByteBuffer PIXEL = BufferUtils.createByteBuffer(4);
    private static final int[] TEXELS = new int[POINTS.length];
    private static final int[] IMAGE_AFTER_DRAW = new int[POINTS.length];
    private static final int[] IMAGE_AT_SWAP = new int[POINTS.length];
    private static final int[] BACK = new int[POINTS.length];

    private static Boolean canReadTexture;
    private static long spentMs, lastFrameAt, windowStart;
    private static boolean menuDrawn, textureBlackNow, textureClearNow, imageBlackAfterDrawNow;
    private static int picturesThisFrame, picturesInWindow;
    private static int frames, backBlack, imageBlackAtSwap, imageBlackAfterDraw, textureBlack, textureClear,
            blackWithPicture, detailed;

    private FrameProbe() {}

    /** A browser picture was uploaded into a page texture (render thread). */
    public static void onPictureUploaded() {
        picturesThisFrame++;
    }

    /** Right after the menu page is drawn into the game image. */
    public static void afterMenuDraw(int texId, int texW, int texH) {
        if (spentMs >= BUDGET_MS) return;
        menuDrawn = true;
        textureBlackNow = false;
        textureClearNow = false;
        if (texW > 0 && texH > 0 && readTexels(texId, texW, texH)) {
            textureBlackNow = allBlack(TEXELS, true);
            textureClearNow = allClear(TEXELS);
        }
        var target = Minecraft.getInstance().getMainRenderTarget();
        readPixels(target.frameBufferId, target.width, target.height, IMAGE_AFTER_DRAW);
        imageBlackAfterDrawNow = allBlack(IMAGE_AFTER_DRAW, false);
    }

    /** Just before the frame is handed to the screen (Window.updateDisplay). */
    public static void beforeSwap() {
        int pictures = picturesThisFrame;
        picturesThisFrame = 0;
        if (!menuDrawn) {
            lastFrameAt = 0;
            return;
        }
        menuDrawn = false;
        var mc = Minecraft.getInstance();
        if (!(ClientCompat.screen(mc) instanceof WebViewScreen)) return;

        var target = mc.getMainRenderTarget();
        readPixels(target.frameBufferId, target.width, target.height, IMAGE_AT_SWAP);
        boolean imageBlack = allBlack(IMAGE_AT_SWAP, false);
        readPixels(0, mc.getWindow().getWidth(), mc.getWindow().getHeight(), BACK);
        boolean backBufferBlack = allBlack(BACK, false);

        frames++;
        picturesInWindow += pictures;
        if (textureBlackNow) textureBlack++;
        if (textureClearNow) textureClear++;
        if (imageBlackAfterDrawNow) imageBlackAfterDraw++;
        if (imageBlack) imageBlackAtSwap++;
        if (backBufferBlack) {
            backBlack++;
            if (pictures > 0) blackWithPicture++;
        }
        boolean anyBlack = backBufferBlack || imageBlack || imageBlackAfterDrawNow || textureBlackNow || textureClearNow;
        if (anyBlack && detailed < DETAILED_REPORTS) {
            detailed++;
            WebGUIMod.LOGGER.warn("[WebGUI] black frame #{}: page texture {}, game image after page draw {}, "
                            + "game image before showing {}, window back buffer {}; browser pictures in this frame {}",
                    detailed, hex(TEXELS), hex(IMAGE_AFTER_DRAW), hex(IMAGE_AT_SWAP), hex(BACK), pictures);
        }

        long now = System.currentTimeMillis();
        if (lastFrameAt != 0) spentMs += Math.min(now - lastFrameAt, 100);
        lastFrameAt = now;
        if (windowStart == 0) windowStart = now;
        boolean last = spentMs >= BUDGET_MS;
        if (now - windowStart >= REPORT_EVERY_MS || last) {
            WebGUIMod.LOGGER.warn("[WebGUI] frame check, last {}s with the menu open: {} frames, {} browser pictures; "
                            + "black in the window back buffer {} (of them with a new browser picture {}); "
                            + "game image black before showing {}, right after the page draw {}; "
                            + "page texture black {}, fully transparent {}{}",
                    Math.max(1, (now - windowStart) / 1000), frames, picturesInWindow, backBlack, blackWithPicture,
                    imageBlackAtSwap, imageBlackAfterDraw, textureBlack, textureClear,
                    last ? " — check finished for this session" : "");
            frames = picturesInWindow = backBlack = blackWithPicture = imageBlackAtSwap = 0;
            imageBlackAfterDraw = textureBlack = textureClear = 0;
            windowStart = now;
        }
    }

    /** Texels as B,G,R,A (the browser's byte order). Needs OpenGL 4.5. */
    private static boolean readTexels(int texId, int w, int h) {
        if (canReadTexture == null) canReadTexture = GL.getCapabilities().OpenGL45;
        if (!canReadTexture) return false;
        int[] saved = savePackState();
        for (int i = 0; i < POINTS.length; i++) {
            PIXEL.clear();
            GL45.glGetTextureSubImage(texId, 0, (int) (POINTS[i][0] * w), (int) (POINTS[i][1] * h), 0, 1, 1, 1,
                    GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, PIXEL);
            TEXELS[i] = PIXEL.getInt(0);
        }
        restorePackState(saved);
        return true;
    }

    /** Pixels as R,G,B,A from a framebuffer (0 = the window's back buffer). */
    private static void readPixels(int framebuffer, int w, int h, int[] out) {
        int[] saved = savePackState();
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
        if (framebuffer == 0) GL11.glReadBuffer(GL11.GL_BACK);
        for (int i = 0; i < POINTS.length; i++) {
            PIXEL.clear();
            GL11.glReadPixels((int) (POINTS[i][0] * w), (int) (POINTS[i][1] * h), 1, 1,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, PIXEL);
            out[i] = PIXEL.getInt(0);
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        restorePackState(saved);
    }

    // Reads into client memory: no pack buffer, no row offsets left by someone else.
    private static int[] savePackState() {
        int[] s = {
                GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING),
                GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH),
                GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS),
                GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS)};
        if (s[0] != 0) GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        if (s[1] != 0) GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
        if (s[2] != 0) GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
        if (s[3] != 0) GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
        return s;
    }

    private static void restorePackState(int[] s) {
        if (s[0] != 0) GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, s[0]);
        if (s[1] != 0) GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, s[1]);
        if (s[2] != 0) GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, s[2]);
        if (s[3] != 0) GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, s[3]);
    }

    /** All points have no colour (the first three bytes, whatever their order). */
    private static boolean allBlack(int[] px, boolean opaqueOnly) {
        for (int p : px) {
            int a = p >>> 24, c0 = p & 0xFF, c1 = (p >>> 8) & 0xFF, c2 = (p >>> 16) & 0xFF;
            if (c0 > 3 || c1 > 3 || c2 > 3) return false;
            if (opaqueOnly && a < 250) return false;
        }
        return true;
    }

    private static boolean allClear(int[] px) {
        for (int p : px) if ((p >>> 24) != 0) return false;
        return true;
    }

    private static String hex(int[] px) {
        StringBuilder sb = new StringBuilder();
        for (int p : px) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format("%08x", Integer.reverseBytes(p)));
        }
        return sb.toString();
    }
}

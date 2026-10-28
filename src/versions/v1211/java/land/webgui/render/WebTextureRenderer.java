package land.webgui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import land.webgui.PaintGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/**
 * Отрисовка GL-текстуры браузера MCEF на весь экран (1.21.1: Tesselator + position_tex шейдер).
 */
public final class WebTextureRenderer {
    private WebTextureRenderer() {}

    private static Boolean samplersSupported;

    public static void blitFullTexture(GuiGraphics context, int texId, int texW, int texH, int w, int h) {
        // The HUD page is transparent: without blending its empty pixels (black, alpha 0) cover the
        // whole screen. Blend used to be left on by whatever drew before us — when another overlay
        // switched it off, the game flashed black frames. Set it ourselves, restore after.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texId);
        int foreignSampler = matchGlToGame(texId);
        var buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        var mat = context.pose().last().pose();
        buf.addVertex(mat, 0, h, 0).setUv(0, 1);
        buf.addVertex(mat, w, h, 0).setUv(1, 1);
        buf.addVertex(mat, w, 0, 0).setUv(1, 0);
        buf.addVertex(mat, 0, 0, 0).setUv(0, 0);
        BufferUploader.drawWithShader(buf.buildOrThrow());
        if (foreignSampler != 0) GL33.glBindSampler(0, foreignSampler);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        PaintGuard.afterDraw();
    }

    /**
     * The game keeps its own copy of the GL state and skips calls it thinks change nothing. A mod
     * that touches GL directly leaves that copy wrong, and the page is then drawn with another
     * texture, without blending or through a sampler made for another texture: a black frame.
     * Set things the game's way, ask OpenGL what is really set, correct it, and count each
     * correction for the log. Returns the sampler object found on unit 0 (put back after the
     * draw), or 0.
     */
    private static int matchGlToGame(int texId) {
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(texId);
        if (GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) != GL13.GL_TEXTURE0) {
            PaintGuard.wrongUnit++;
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
        }
        if (GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) != texId) {
            PaintGuard.wrongTexture++;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        }
        int sampler = 0;
        if (samplersSupported == null) samplersSupported = GL.getCapabilities().OpenGL33;
        if (samplersSupported) {
            sampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            if (sampler != 0) {
                PaintGuard.samplerBound++;
                GL33.glBindSampler(0, 0);
            }
        }
        if (!GL11.glIsEnabled(GL11.GL_BLEND)) {
            PaintGuard.blendOff++;
            GL11.glEnable(GL11.GL_BLEND);
        }
        if (GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB) != GL11.GL_SRC_ALPHA
                || GL11.glGetInteger(GL14.GL_BLEND_DST_RGB) != GL11.GL_ONE_MINUS_SRC_ALPHA) {
            PaintGuard.blendFunc++;
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        if (GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) {
            PaintGuard.depthOn++;
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        if (GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != Minecraft.getInstance().getMainRenderTarget().frameBufferId) {
            PaintGuard.foreignTarget++;
        }
        if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) PaintGuard.scissorOn++;
        if (GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH) != 0) PaintGuard.unpackLeft++;
        return sampler;
    }
}

package land.webgui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

/**
 * Отрисовка GL-текстуры браузера MCEF на весь экран (1.21.1: Tesselator + position_tex шейдер).
 */
public final class WebTextureRenderer {
    private WebTextureRenderer() {}

    public static void blitFullTexture(GuiGraphics context, int texId, int texW, int texH, int w, int h) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texId);
        var buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        var mat = context.pose().last().pose();
        buf.addVertex(mat, 0, h, 0).setUv(0, 1);
        buf.addVertex(mat, w, h, 0).setUv(1, 1);
        buf.addVertex(mat, w, 0, 0).setUv(1, 0);
        buf.addVertex(mat, 0, 0, 0).setUv(0, 0);
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }
}

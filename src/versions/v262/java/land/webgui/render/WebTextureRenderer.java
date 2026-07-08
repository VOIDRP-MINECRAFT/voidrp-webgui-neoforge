package land.webgui.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Отрисовка GL-текстуры браузера MCEF на весь экран (26.2: новый GPU-пайплайн).
 * Сырую GL-текстуру Chromium оборачиваем в GpuTextureView через наследники
 * GlTexture/GlTextureView; текстурой владеет MCEF, поэтому close() — no-op.
 */
public final class WebTextureRenderer {
    private WebTextureRenderer() {}

    private static int cachedId = -1;
    private static int cachedW = -1;
    private static int cachedH = -1;
    private static GpuTextureView cachedView;

    public static void blitFullTexture(GuiGraphicsExtractor graphics, int texId, int texW, int texH, int w, int h) {
        GpuTextureView view = viewFor(texId, Math.max(1, texW), Math.max(1, texH));
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        graphics.blit(view, sampler, 0, 0, w, h, 0f, 1f, 0f, 1f);
    }

    private static GpuTextureView viewFor(int texId, int texW, int texH) {
        if (cachedView == null || cachedId != texId || cachedW != texW || cachedH != texH) {
            cachedView = new ExternalGlTextureView(new ExternalGlTexture(texId, texW, texH));
            cachedId = texId;
            cachedW = texW;
            cachedH = texH;
        }
        return cachedView;
    }

    /** GL-текстура, созданная вне движка (Chromium/MCEF); жизненным циклом управляет MCEF. */
    private static final class ExternalGlTexture extends GlTexture {
        ExternalGlTexture(int id, int width, int height) {
            super(GpuTexture.USAGE_TEXTURE_BINDING, "webgui/mcef browser", GpuFormat.RGBA8_UNORM,
                    width, height, 1, 1, id, null);
        }

        @Override
        public void close() {
            // Текстурой владеет MCEF — движку её удалять нельзя.
        }
    }

    private static final class ExternalGlTextureView extends GlTextureView {
        ExternalGlTextureView(GlTexture texture) {
            super(texture, 0, 1, null);
        }

        @Override
        public void close() {
            // см. ExternalGlTexture#close
        }
    }
}

package land.webgui;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;

public class WebViewScreen extends Screen {

    private static boolean guiPageReady;
    private final String initialUrl;
    private MCEFBrowser browser;

    public WebViewScreen(String startUrl) {
        super(Component.translatable("screen.webgui.title"));
        this.initialUrl = startUrl == null || startUrl.isBlank() ? StartUrls.primary() : startUrl;
    }

    @Override
    protected void init() {
        super.init();
        if (!MCEF.isInitialized()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(Component.translatable("message.webgui.mcef_not_ready"));
            }
            this.onClose();
            return;
        }
        if (browser != null) {
            resizeBrowser();
            return;
        }
        WebHudOverlay.onGuiOpened();
        guiPageReady = false;
        browser = WebSession.openForGui(initialUrl);
        resizeBrowser();
        browser.setFocus(true);
    }

    private int getBrowserWidth() { return Math.max(1, this.width); }
    private int getBrowserHeight() { return Math.max(1, this.height); }

    private boolean isInBrowserBounds(double x, double y) {
        return x >= 0 && y >= 0 && x < this.width && y < this.height;
    }

    private int browserLocalMouseX(double x) {
        return (int) (x * this.minecraft.getWindow().getGuiScale());
    }

    private int browserLocalMouseY(double y) {
        return (int) (y * this.minecraft.getWindow().getGuiScale());
    }

    private void resizeBrowser() {
        if (browser != null && this.minecraft != null) {
            browser.resize(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight());
        }
    }

    @Override
    public void resize(Minecraft p_96575_, int p_96576_, int p_96577_) {
        super.resize(p_96575_, p_96576_, p_96577_);
        resizeBrowser();
    }

    @Override
    public void onClose() {
        guiPageReady = false;
        WebSession.closeGuiAndRestoreHud();
        super.onClose();
        if (this.minecraft != null) {
            WebHudOverlay.onGuiClosed(this.minecraft);
        }
    }

    static void onGuiBrowserLoadStart(MCEFBrowser browser) {
        if (browser == null) return;
        if (WebSession.mode() == WebSession.Mode.GUI_SCREEN && browser == WebSession.browser()) {
            guiPageReady = false;
        }
    }

    static void onGuiBrowserLoadFinished(MCEFBrowser browser) {
        if (browser == null) return;
        if (WebSession.mode() == WebSession.Mode.GUI_SCREEN && browser == WebSession.browser()) {
            guiPageReady = true;
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (browser != null && guiPageReady && browser.isTextureReady()) {
            int texId = browser.getRenderer().getTextureID();
            if (texId != 0) {
                renderRawTexture(context, texId, getBrowserWidth(), getBrowserHeight());
            }
        }
    }

    private static void renderRawTexture(GuiGraphics context, int texId, int w, int h) {
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

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        // no darkening — web fills the entire screen
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (browser == null || !isInBrowserBounds(mouseX, mouseY)) return false;
        int bx = browserLocalMouseX(mouseX);
        int by = browserLocalMouseY(mouseY);
        browser.sendMouseMove(bx, by);
        browser.sendMousePress(bx, by, button);
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (browser == null) return false;
        int bx = browserLocalMouseX(mouseX);
        int by = browserLocalMouseY(mouseY);
        browser.sendMouseMove(bx, by);
        browser.sendMouseRelease(bx, by, button);
        browser.setFocus(true);
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null && isInBrowserBounds(mouseX, mouseY)) {
            browser.sendMouseMove(browserLocalMouseX(mouseX), browserLocalMouseY(mouseY));
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (browser == null || !isInBrowserBounds(mouseX, mouseY)) return false;
        browser.sendMouseWheel(browserLocalMouseX(mouseX), browserLocalMouseY(mouseY), verticalAmount, 0);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (browser == null) return false;
        browser.sendKeyPress(keyCode, scanCode, modifiers);
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (super.keyReleased(keyCode, scanCode, modifiers)) return true;
        if (browser == null) return false;
        browser.sendKeyRelease(keyCode, scanCode, modifiers);
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (super.charTyped(chr, modifiers)) return true;
        if (browser == null) return false;
        browser.sendKeyTyped(chr, modifiers);
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}

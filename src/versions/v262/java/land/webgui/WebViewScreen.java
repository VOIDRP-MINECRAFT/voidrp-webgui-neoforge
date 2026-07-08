package land.webgui;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import land.webgui.render.WebTextureRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Полноэкранный браузер (вариант для Minecraft 26.2: extract-рендер и новые input-события). */
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
    public void resize(int newWidth, int newHeight) {
        super.resize(newWidth, newHeight);
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (browser != null && guiPageReady && browser.isTextureReady()) {
            int texId = browser.getRenderer().getTextureID();
            if (texId != 0) {
                WebTextureRenderer.blitFullTexture(graphics, texId,
                        this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight(),
                        getBrowserWidth(), getBrowserHeight());
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // без затемнения — веб-страница занимает весь экран
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (browser == null || !isInBrowserBounds(event.x(), event.y())) return false;
        int bx = browserLocalMouseX(event.x());
        int by = browserLocalMouseY(event.y());
        browser.sendMouseMove(bx, by);
        browser.sendMousePress(bx, by, event.button());
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (browser == null) return false;
        int bx = browserLocalMouseX(event.x());
        int by = browserLocalMouseY(event.y());
        browser.sendMouseMove(bx, by);
        browser.sendMouseRelease(bx, by, event.button());
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
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) return true;
        if (browser == null) return false;
        browser.sendKeyPress(event.key(), event.scancode(), event.modifiers());
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (super.keyReleased(event)) return true;
        if (browser == null) return false;
        browser.sendKeyRelease(event.key(), event.scancode(), event.modifiers());
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (super.charTyped(event)) return true;
        if (browser == null) return false;
        browser.sendKeyTyped((char) event.codepoint(), 0);
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}

package land.webgui;

// No MCEF / org.cef imports here — this class is loaded early in the Mixin chain
// (MouseHandlerMixin → WebGUIClientHandlers → WebHudOverlay → McefBridge).
// jcef (org.cef.*) is added to the classpath lazily by MCEF, so any reference to
// those classes here would cause NoClassDefFoundError during Mixin preprocessing.
// All org.cef.* code lives in McefSetup which is loaded only after MCEF is ready.
public final class McefBridge {
    private McefBridge() {}

    static volatile boolean mcefPresent = false;
    private static volatile boolean mcefInitialized = false;

    public static boolean isMcefInitialized() {
        return mcefInitialized;
    }

    static void markInitialized() {
        mcefInitialized = true;
    }
}

package land.webgui.render;

/** The black-frame probe is only needed on 1.21.1 (see the v1211 FrameProbe); here it does nothing. */
public final class FrameProbe {
    private FrameProbe() {}

    public static void onPictureUploaded() {}

    public static void beforeSwap() {}
}

package land.webgui;

public final class WebGUIMainMenuUrl {
    private static String url = StartUrls.HTTPS_FALLBACK;
    private WebGUIMainMenuUrl() {}
    public static String getUrl() { return url; }
    public static void setUrl(String u) { if (u != null && !u.isBlank()) url = u; }
}

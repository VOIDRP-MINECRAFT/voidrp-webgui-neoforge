package land.webgui.server;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class WebviewUrlBuilder {
    private WebviewUrlBuilder() {}

    public static String appendQueryParam(String url, String name, String value) {
        if (url == null) url = "";
        String pair = URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
        int hash = url.indexOf('#');
        String base = hash >= 0 ? url.substring(0, hash) : url;
        String fragment = hash >= 0 ? url.substring(hash) : "";
        return base.contains("?") ? base + "&" + pair + fragment : base + "?" + pair + fragment;
    }
}

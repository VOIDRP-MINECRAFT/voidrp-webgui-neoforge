package land.webgui.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import land.webgui.WebGUIMod;
import net.neoforged.fml.ModList;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class WebGUIUpdateChecker {
    private WebGUIUpdateChecker() {}

    public static void checkAsync() {
        String url = WebviewServerConfig.updateCheckUrl();
        if (url.isEmpty()) return;

        String current = ModList.get().getModContainerById(WebGUIMod.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("");
        if (current.isEmpty()) return;

        Thread t = new Thread(() -> {
            try {
                HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "WebGUI-Mod/" + current)
                        .header("Accept", "application/json")
                        .GET().build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) return;
                parseAndLog(current, resp.body());
            } catch (Exception e) {
                WebGUIMod.LOGGER.debug("webgui: update check failed: {}", e.getMessage());
            }
        }, "webgui-update-check");
        t.setDaemon(true);
        t.start();
    }

    private static void parseAndLog(String current, String body) {
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            String remote;
            String downloadUrl = "";
            if (o.has("version") && !o.get("version").isJsonNull()) {
                remote = o.get("version").getAsString();
                if (o.has("url") && !o.get("url").isJsonNull()) downloadUrl = o.get("url").getAsString();
            } else if (o.has("tag_name") && !o.get("tag_name").isJsonNull()) {
                remote = o.get("tag_name").getAsString();
                if (o.has("html_url") && !o.get("html_url").isJsonNull()) downloadUrl = o.get("html_url").getAsString();
            } else {
                return;
            }
            remote = stripMeta(remote.trim().replaceFirst("^[vV]", ""));
            String local = stripMeta(current.trim().replaceFirst("^[vV]", ""));
            if (!remote.equals(local)) {
                WebGUIMod.LOGGER.info("WebGUI update available: {} → {}", local, remote);
                if (!downloadUrl.isEmpty()) WebGUIMod.LOGGER.info("Download: {}", downloadUrl);
            }
        } catch (Exception e) {
            WebGUIMod.LOGGER.debug("webgui: update check parse error: {}", e.getMessage());
        }
    }

    private static String stripMeta(String v) {
        int plus = v.indexOf('+');
        return plus >= 0 ? v.substring(0, plus) : v;
    }
}

package us.dingl.VowSMPPlugin.Update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import us.dingl.VowSMPPlugin.ConfigKey;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/// downloads the latest GitHub release jar into the server's update folder.
/// Paper swaps it in on the next restart - the file has to have the same name as the current jar.
public class Updater {

    private final VowSMPPlugin plugin;
    private final File currentJar;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL) // release downloads redirect to a CDN
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public Updater(VowSMPPlugin plugin, File currentJar) {
        this.plugin = plugin;
        this.currentJar = currentJar;
    }

    /// runs off the main thread, reports back to the sender when done
    public void update(CommandSender sender) {
        if (!running.compareAndSet(false, true)) {
            sender.sendMessage("An update is already running.");
            return;
        }

        String repo = plugin.getConfig().getString(ConfigKey.UPDATE_REPO.getPath(), "");
        if (!repo.matches("[\\w.-]+/[\\w.-]+")) {
            running.set(false);
            sender.sendMessage("Invalid " + ConfigKey.UPDATE_REPO.getPath() + " in config: '" + repo + "'");
            return;
        }

        sender.sendMessage("Checking " + repo + " for updates...");
        CompletableFuture.runAsync(() -> {
            try {
                reply(sender, download(repo));
            } catch (Exception e) {
                plugin.getLogger().warning("Update failed: " + e);
                reply(sender, "Update failed: " + e.getMessage());
            } finally {
                running.set(false);
            }
        });
    }

    private String download(String repo) throws Exception {
        HttpResponse<String> response = http.send(
                request("https://api.github.com/repos/" + repo + "/releases/latest")
                        .header("Accept", "application/vnd.github+json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("GitHub returned " + response.statusCode() + " (is there a release yet?)");
        }

        JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
        String tag = release.get("tag_name").getAsString();
        String latest = tag.startsWith("v") ? tag.substring(1) : tag;
        String current = plugin.getPluginMeta().getVersion();
        if (latest.equals(current)) {
            return "Already on the latest version (" + current + ").";
        }

        String jarUrl = null;
        for (JsonElement asset : release.getAsJsonArray("assets")) {
            JsonObject obj = asset.getAsJsonObject();
            if (obj.get("name").getAsString().endsWith(".jar")) {
                jarUrl = obj.get("browser_download_url").getAsString();
                break;
            }
        }
        if (jarUrl == null) {
            throw new IllegalStateException("Release " + tag + " has no jar attached");
        }

        Path updateDir = Bukkit.getUpdateFolderFile().toPath();
        Files.createDirectories(updateDir);
        Path target = updateDir.resolve(currentJar.getName());
        Path temp = updateDir.resolve(currentJar.getName() + ".part");

        HttpResponse<Path> jar = http.send(request(jarUrl).build(), HttpResponse.BodyHandlers.ofFile(temp));
        if (jar.statusCode() != 200) {
            Files.deleteIfExists(temp);
            throw new IllegalStateException("Jar download returned " + jar.statusCode());
        }
        // only replace the real file once the download fully finished, so a restart never picks up half a jar
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        return "Downloaded " + tag + " (currently " + current + "). Restart the server to apply it.";
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "VowSMPPlugin-Updater"); // GitHub rejects requests without one
    }

    private void reply(CommandSender sender, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));
    }
}

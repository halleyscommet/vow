package us.dingl.VowSMPPlugin.Chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import us.dingl.VowSMPPlugin.ConfigKey;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// /ai - a chatbot backed by the Gemini API. Each player has their own short conversation (kept in memory,
/// gone on quit, restart or /ai reset), and only they see the replies.
///
/// The API key comes from config (gemini.api-key), or the GEMINI_API_KEY environment variable if that's empty.
public final class GeminiChat implements Listener {

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final int MAX_OUTPUT_TOKENS = 500;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final VowSMPPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    /// player -> their conversation so far, as Gemini "contents" entries, oldest first. Main thread only.
    private final Map<UUID, Deque<JsonObject>> history = new HashMap<>();
    /// players waiting on a reply, so they can't stack up requests
    private final Set<UUID> pending = new HashSet<>();
    /// player -> when they can ask again (epoch millis)
    private final Map<UUID, Long> readyAt = new HashMap<>();

    public GeminiChat(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    /// sends the message and replies to the player when Gemini answers. Main thread only.
    public void ask(Player player, String message) {
        UUID id = player.getUniqueId();
        String key = apiKey();
        if (key.isEmpty()) {
            player.sendMessage(Component.text("The AI isn't set up yet - an admin needs to add a Gemini API key.", NamedTextColor.RED));
            return;
        }
        if (pending.contains(id)) {
            player.sendMessage(Component.text("Still thinking about your last message...", NamedTextColor.GRAY));
            return;
        }
        long wait = readyAt.getOrDefault(id, 0L) - System.currentTimeMillis();
        if (wait > 0) {
            player.sendMessage(Component.text("Wait " + (wait + 999) / 1000 + "s before asking again.", NamedTextColor.RED));
            return;
        }

        FileConfiguration config = plugin.getConfig();
        String model = config.getString(ConfigKey.GEMINI_MODEL.getPath(), "gemini-2.5-flash");
        String systemPrompt = config.getString(ConfigKey.GEMINI_SYSTEM_PROMPT.getPath(), "");
        int maxTurns = Math.max(0, config.getInt(ConfigKey.GEMINI_HISTORY.getPath(), 10));
        long cooldownMillis = Math.max(0, config.getInt(ConfigKey.GEMINI_COOLDOWN.getPath(), 5)) * 1000L;

        Deque<JsonObject> turns = history.computeIfAbsent(id, k -> new ArrayDeque<>());
        JsonObject userTurn = turn("user", message);

        JsonObject body = new JsonObject();
        JsonArray contents = new JsonArray();
        turns.forEach(contents::add);
        contents.add(userTurn);
        body.add("contents", contents);
        // the server's rules, vows and the player's own state ride along with every message, so they stay current
        String instructions = systemPrompt.replace("%player%", player.getName()).strip()
                + "\n\n" + ServerContext.build(plugin, player);
        JsonObject system = new JsonObject();
        system.add("parts", parts(instructions));
        body.add("systemInstruction", system);
        JsonObject generation = new JsonObject();
        generation.addProperty("maxOutputTokens", MAX_OUTPUT_TOKENS);
        JsonObject thinking = new JsonObject();
        thinking.addProperty("thinkingBudget", 0); // chat replies don't need it, and it's faster and cheaper off
        generation.add("thinkingConfig", thinking);
        body.add("generationConfig", generation);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(ENDPOINT, URLEncoder.encode(model, StandardCharsets.UTF_8))))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        pending.add(id);
        readyAt.put(id, System.currentTimeMillis() + cooldownMillis);
        player.sendMessage(Component.text("You -> AI: ", NamedTextColor.DARK_AQUA).append(Component.text(message, NamedTextColor.GRAY)));

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    pending.remove(id);
                    Player online = Bukkit.getPlayer(id);
                    String reply;
                    try {
                        if (error != null) throw new GeminiException("couldn't reach Gemini (" + error.getClass().getSimpleName() + ")");
                        reply = parseReply(response);
                    } catch (GeminiException e) {
                        plugin.getLogger().warning("Gemini request for " + player.getName() + " failed: " + e.getMessage());
                        if (online != null) online.sendMessage(Component.text("AI error: " + e.getMessage(), NamedTextColor.RED));
                        return;
                    }

                    // only keep the exchange if they're still here - quitting clears their history
                    if (online == null) return;
                    turns.addLast(userTurn);
                    turns.addLast(turn("model", reply));
                    while (turns.size() > maxTurns * 2) turns.removeFirst();
                    online.sendMessage(Component.text("AI: ", NamedTextColor.AQUA)
                            .append(Component.text(plainText(reply), NamedTextColor.WHITE)));
                }));
    }

    /// forgets the player's conversation
    public void reset(Player player) {
        history.remove(player.getUniqueId());
    }

    private String parseReply(HttpResponse<String> response) throws GeminiException {
        JsonObject json;
        try {
            json = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new GeminiException("bad response (HTTP " + response.statusCode() + ")");
        }

        if (response.statusCode() != 200) {
            String message = json.has("error") && json.getAsJsonObject("error").has("message")
                    ? json.getAsJsonObject("error").get("message").getAsString() : "HTTP " + response.statusCode();
            throw new GeminiException(message);
        }

        JsonArray candidates = json.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new GeminiException("no answer (the message may have been blocked)");
        }
        JsonObject candidate = candidates.get(0).getAsJsonObject();
        StringBuilder text = new StringBuilder();
        JsonObject content = candidate.getAsJsonObject("content");
        if (content != null && content.has("parts")) {
            for (JsonElement part : content.getAsJsonArray("parts")) {
                JsonObject p = part.getAsJsonObject();
                if (p.has("text") && !p.has("thought")) text.append(p.get("text").getAsString());
            }
        }
        if (text.isEmpty()) {
            String reason = candidate.has("finishReason") ? candidate.get("finishReason").getAsString() : "unknown";
            throw new GeminiException("no answer (" + reason + ")");
        }
        return text.toString().strip();
    }

    /// chat has no markdown, so drop the bold/italic/heading/code marks it likes to use
    private static String plainText(String reply) {
        return reply.replaceAll("\\*\\*|__|`", "")
                .replaceAll("(?m)^#+\\s*", "")
                .replaceAll("(?m)^\\s*\\*\\s+", "- ");
    }

    private String apiKey() {
        String key = plugin.getConfig().getString(ConfigKey.GEMINI_API_KEY.getPath(), "");
        if (key == null || key.isBlank()) key = System.getenv().getOrDefault("GEMINI_API_KEY", "");
        return key.trim();
    }

    private static JsonObject turn(String role, String text) {
        JsonObject turn = new JsonObject();
        turn.addProperty("role", role);
        turn.add("parts", parts(text));
        return turn;
    }

    private static JsonArray parts(String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        JsonArray parts = new JsonArray();
        parts.add(part);
        return parts;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        history.remove(id);
        readyAt.remove(id);
    }

    private static final class GeminiException extends Exception {
        GeminiException(String message) {
            super(message);
        }
    }
}

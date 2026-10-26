package us.dingl.VowSMPPlugin.Listeners;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import us.dingl.VowSMPPlugin.ConfigKey;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ResourcePackListener implements Listener {

    /// where the pack comes from - resource-pack.source in config
    public enum Source {
        /// the zip attached to this version's GitHub release, with the hash baked in at build time
        RELEASE,
        /// resource-pack.url and resource-pack.hash
        CONFIG
    }

    /// what gets sent, or why nothing can be
    public record Pack(String url, String hash, String problem) {
        static Pack of(String url, String hash) {
            return new Pack(url, hash == null || hash.isBlank() ? null : hash, null);
        }

        static Pack missing(String problem) {
            return new Pack(null, null, problem);
        }
    }

    private static final UUID PACK_ID =
            UUID.nameUUIDFromBytes("vow-smp-resource-pack".getBytes(StandardCharsets.UTF_8));

    private static final long SEND_DELAY_TICKS = 20L;
    private static final long MAX_PROTECTION_MS = 60_000L;

    /// the pack zipped from resourcepack/ at build time and attached to the release with this jar
    private static final String RELEASE_PACK_NAME = "VowResourcePack.zip";

    private final VowSMPPlugin plugin;
    /// SHA-1 of that zip, baked into the jar by the build. null if it's missing (e.g. an IDE build).
    private final String releasePackHash;

    private final Map<UUID, Long> protectedUntil = new ConcurrentHashMap<>();

    public ResourcePackListener(VowSMPPlugin plugin) {
        this.plugin = plugin;
        this.releasePackHash = readReleasePackHash();
    }

    private String readReleasePackHash() {
        try (InputStream in = plugin.getResource("resource-pack.properties")) {
            if (in == null) return null;
            Properties properties = new Properties();
            properties.load(in);
            String hash = properties.getProperty("sha1");
            return hash == null || hash.isBlank() ? null : hash.trim();
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read the bundled resource pack hash: " + e.getMessage());
            return null;
        }
    }

    private boolean isProtected(UUID id) {
        Long until = protectedUntil.get(id);
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            protectedUntil.remove(id);
            return false;
        }
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        protectedUntil.put(player.getUniqueId(), System.currentTimeMillis() + MAX_PROTECTION_MS);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                sendRequiredResourcePack(player);
            }
        }, SEND_DELAY_TICKS);
    }

    public Source getSource() {
        String value = plugin.getConfig().getString(ConfigKey.RESOURCE_PACK_SOURCE.getPath(), "release");
        return value.equalsIgnoreCase("config") ? Source.CONFIG : Source.RELEASE;
    }

    /// saves the new source and sends the pack it points at to everyone online
    public void setSource(Source source) {
        plugin.getConfig().set(ConfigKey.RESOURCE_PACK_SOURCE.getPath(), source.name().toLowerCase(Locale.ROOT));
        plugin.saveConfig();
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendRequiredResourcePack(player);
        }
    }

    /// the pack the current source points at
    public Pack currentPack() {
        if (getSource() == Source.RELEASE) {
            if (releasePackHash == null) {
                return Pack.missing("this build has no bundled pack (only release builds do)");
            }
            return Pack.of("https://github.com/" + plugin.getConfig().getString(ConfigKey.UPDATE_REPO.getPath())
                    + "/releases/download/v" + plugin.getPluginMeta().getVersion() + "/" + RELEASE_PACK_NAME,
                    releasePackHash);
        }

        String url = plugin.getConfig().getString(ConfigKey.RESOURCE_PACK_URL.getPath());
        if (url == null || url.isBlank()) {
            return Pack.missing("resource-pack.url is not set");
        }
        return Pack.of(url, plugin.getConfig().getString(ConfigKey.RESOURCE_PACK_HASH.getPath()));
    }

    private void sendRequiredResourcePack(Player player) {
        UUID id = player.getUniqueId();
        Pack pack = currentPack();
        if (pack.problem() != null) {
            plugin.getLogger().warning("Resource pack source is " + getSource().name().toLowerCase(Locale.ROOT)
                    + " but " + pack.problem() + "; skipping resource pack.");
            protectedUntil.remove(id);
            return;
        }

        ResourcePackInfo.Builder builder;
        try {
            builder = ResourcePackInfo.resourcePackInfo().id(PACK_ID).uri(URI.create(pack.url()));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("resource-pack.url is not a valid URL: " + pack.url());
            protectedUntil.remove(id);
            return;
        }
        if (pack.hash() != null) {
            builder.hash(pack.hash());
        }

        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(builder.build())
                .required(true)
                .prompt(Component.text("You must accept the resource pack to play on Vow SMP."))
                .build();

        player.sendResourcePacks(request);
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) return; // some other plugin's pack

        switch (event.getStatus()) {
            case ACCEPTED, DOWNLOADED -> { } // still in progress
            default -> protectedUntil.remove(event.getPlayer().getUniqueId()); // loaded, declined, failed...
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isProtected(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (damager instanceof Player player && isProtected(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        protectedUntil.remove(event.getPlayer().getUniqueId());
    }
}

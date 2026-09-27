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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ResourcePackListener implements Listener {

    private static final UUID PACK_ID =
            UUID.nameUUIDFromBytes("vow-smp-resource-pack".getBytes(StandardCharsets.UTF_8));

    private static final long SEND_DELAY_TICKS = 20L;
    private static final long MAX_PROTECTION_MS = 60_000L;

    private final VowSMPPlugin plugin;

    private final Map<UUID, Long> protectedUntil = new ConcurrentHashMap<>();

    public ResourcePackListener(VowSMPPlugin plugin) {
        this.plugin = plugin;
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

    private void sendRequiredResourcePack(Player player) {
        UUID id = player.getUniqueId();
        String url = plugin.getConfig().getString(ConfigKey.RESOURCE_PACK_URL.getPath());
        String hash = plugin.getConfig().getString(ConfigKey.RESOURCE_PACK_HASH.getPath());

        if (url == null || url.isBlank()) {
            plugin.getLogger().warning("resource-pack.url is not set; skipping resource pack.");
            protectedUntil.remove(id);
            return;
        }

        ResourcePackInfo.Builder builder;
        try {
            builder = ResourcePackInfo.resourcePackInfo().id(PACK_ID).uri(URI.create(url));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("resource-pack.url is not a valid URL: " + url);
            protectedUntil.remove(id);
            return;
        }
        if (hash != null && !hash.isBlank()) {
            builder.hash(hash);
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

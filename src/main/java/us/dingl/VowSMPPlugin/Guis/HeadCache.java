package us.dingl.VowSMPPlugin.Guis;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/// Player head skins, fetched once each, slowly, and remembered.
/// Building a head from this never triggers a Mojang lookup by itself.
public final class HeadCache {

    private static final long RETRY_AFTER_MS = 10 * 60 * 1000L; // after a failure (e.g. 429), wait before retrying
    private static final long FETCH_INTERVAL_TICKS = 40L;       // one lookup every 2 seconds

    private final VowSMPPlugin plugin;
    private final Map<UUID, PlayerProfile> ready = new ConcurrentHashMap<>();
    private final Map<UUID, Long> failedAt = new ConcurrentHashMap<>();
    private final Set<UUID> queued = ConcurrentHashMap.newKeySet();
    private final Queue<UUID> queue = new ConcurrentLinkedQueue<>();
    private BukkitTask worker;

    public HeadCache(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        worker = Bukkit.getScheduler().runTaskTimer(plugin, this::fetchNext, FETCH_INTERVAL_TICKS, FETCH_INTERVAL_TICKS);
    }

    public void stop() {
        if (worker != null) {
            worker.cancel();
            worker = null;
        }
    }

    /// A player head for this UUID. Uses the cached skin if there is one, otherwise a plain head
    /// and the skin is queued to be fetched for next time.
    public ItemStack head(UUID id, Component name, List<Component> lore) {
        PlayerProfile profile = ready.get(id);
        if (profile == null) {
            request(id);
        }

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        item.editMeta(SkullMeta.class, meta -> {
            if (profile != null) {
                meta.setPlayerProfile(profile);
            }
            meta.displayName(name);
            meta.lore(lore);
        });
        return item;
    }

    private void request(UUID id) {
        Long failed = failedAt.get(id);
        if (failed != null && System.currentTimeMillis() - failed < RETRY_AFTER_MS) {
            return;
        }
        if (queued.add(id)) {
            queue.add(id);
        }
    }

    private void fetchNext() {
        UUID id = queue.poll();
        if (id == null) return;
        queued.remove(id);

        // update() runs the network call off the main thread
        Bukkit.createProfile(id).update().whenComplete((updated, error) -> {
            if (error != null || updated == null || !updated.hasTextures()) {
                failedAt.put(id, System.currentTimeMillis());
            } else {
                failedAt.remove(id);
                ready.put(id, updated);
            }
        });
    }
}

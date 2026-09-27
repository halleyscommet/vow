package us.dingl.VowSMPPlugin.Ritual;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/// Rewrites the biome data in chunk packets sent to every player in the storm's world,
/// so anyone who is inside the storm sees the red sky. The real chunks on the server are never touched.
public final class StormBiome extends PacketListenerAbstract implements Listener {

    private static final NamespacedKey BIOME_KEY = new NamespacedKey("vow", "red_sky_storm");
    // extra blocks past the wall so the sky blend isn't cut off right at the edge
    private static final int MARGIN = 8;
    // resending chunks is not free, so spread it over a few ticks
    private static final int CHUNKS_PER_TICK = 8;
    // how long after someone arrives before the region is resent, so their first chunks have gone out
    private static final long ARRIVAL_RESEND_DELAY = 20L;

    private final VowSMPPlugin plugin;

    // players whose chunk packets get rewritten. Read on the netty thread, so it has to be concurrent.
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();

    // read on the netty thread, written on the main thread.
    // `active` is written LAST in enable() so the other fields are visible by the time it's true.
    private volatile boolean active;
    private volatile int biomeId = -1;
    private volatile double centerX;
    private volatile double centerZ;
    private volatile double limitSq;
    private volatile World world;

    private BukkitTask refreshTask;

    public StormBiome(VowSMPPlugin plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        PacketEvents.getAPI().getEventManager().registerListener(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // Main thread API

    public void enable(World world, Location center, double radius) {
        int id = resolveBiomeId();
        if (id < 0) return; // resolveBiomeId already logged why

        double limit = radius + MARGIN;
        this.world = world;
        this.biomeId = id;
        this.centerX = center.getX();
        this.centerZ = center.getZ();
        this.limitSq = limit * limit;

        viewers.clear();
        for (Player player : world.getPlayers()) {
            viewers.add(player.getUniqueId());
        }

        this.active = true; // last

        refreshRegion();
    }

    public void disable() {
        if (!active) return;

        active = false;   // stop rewriting BEFORE resending, so the resend carries the real biomes
        refreshRegion();  // builds its chunk list from `viewers` right now...
        viewers.clear();  // ...so it's safe to clear them straight after
    }

    public void close() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        HandlerList.unregisterAll(this);
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }

    // Keeping the viewer set up to date

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (active && player.getWorld().equals(world)) {
            viewers.add(player.getUniqueId());
            scheduleArrivalResend();
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!active) return;

        Player player = event.getPlayer();
        if (player.getWorld().equals(world)) {
            viewers.add(player.getUniqueId());
            scheduleArrivalResend();
        } else {
            viewers.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        viewers.remove(event.getPlayer().getUniqueId());
    }

    /// The chunks sent around the moment someone arrives may have gone out before they were in
    /// `viewers`, so resend the region once. Repeat calls just restart the resend.
    private void scheduleArrivalResend() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active) refreshRegion();
        }, ARRIVAL_RESEND_DELAY);
    }

    // Biome ID lookup

    /// The client-side biome ID is the biome's raw ID in the server's internal biome registry.
    private int resolveBiomeId() {
        try {
            // 1. find the biome through the API, which already knows about the datapack biome
            Registry<Biome> apiRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
            Biome apiBiome = apiRegistry.get(BIOME_KEY);
            if (apiBiome == null) {
                plugin.getLogger().warning("Biome " + BIOME_KEY + " isn't registered. Is the storm_sky datapack loaded? (needs a full restart)");
                return -1;
            }

            // 2. get the internal biome object behind it
            Object nmsBiome = findMethod(apiBiome.getClass(), "getHandle", 0).invoke(apiBiome);

            // 3. get the internal biome registry
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            Object access = server.getClass().getMethod("registryAccess").invoke(server);
            Object biomeKey = Class.forName("net.minecraft.core.registries.Registries")
                    .getField("BIOME").get(null);
            Class<?> resourceKeyClass = Class.forName("net.minecraft.resources.ResourceKey");
            Object registry = Class.forName("net.minecraft.core.RegistryAccess")
                    .getMethod("lookupOrThrow", resourceKeyClass)
                    .invoke(access, biomeKey);

            // 4. ask it for the raw ID
            return (int) findMethod(registry.getClass(), "getId", 1).invoke(registry, nmsBiome);
        } catch (ReflectiveOperationException | ClassCastException e) {
            plugin.getLogger().log(Level.SEVERE, "Couldn't resolve the storm biome ID", e);
            return -1;
        }
    }

    /// finds a public method by name and parameter count, searching inherited ones too
    private static Method findMethod(Class<?> type, String name, int paramCount) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name + " (" + paramCount + " params)");
    }

    // Resending chunks

    /// Resends every chunk in the storm area that at least one viewer already has.
    /// World#refreshChunk goes to everyone tracking the chunk, which is fine because all of them are viewers.
    private void refreshRegion() {
        // can't schedule tasks while the plugin is shutting down
        if (!plugin.isEnabled()) return;

        World target = this.world;
        if (target == null) return;

        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }

        List<Player> online = new ArrayList<>();
        for (UUID id : viewers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        if (online.isEmpty()) return;

        double limit = Math.sqrt(limitSq);
        int minChunkX = (int) Math.floor(centerX - limit) >> 4;
        int maxChunkX = (int) Math.floor(centerX + limit) >> 4;
        int minChunkZ = (int) Math.floor(centerZ - limit) >> 4;
        int maxChunkZ = (int) Math.floor(centerZ + limit) >> 4;

        Queue<int[]> queue = new ArrayDeque<>();
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                long key = Chunk.getChunkKey(x, z);
                for (Player player : online) {
                    if (player.isChunkSent(key)) {
                        queue.add(new int[]{x, z});
                        break;
                    }
                }
            }
        }
        if (queue.isEmpty()) return;

        refreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < CHUNKS_PER_TICK && !queue.isEmpty(); i++) {
                    int[] c = queue.poll();
                    if (target.isChunkLoaded(c[0], c[1])) {
                        target.refreshChunk(c[0], c[1]);
                    }
                }
                if (queue.isEmpty()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // Netty thread

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.CHUNK_DATA) return;
        if (!active) return;

        int id = this.biomeId;
        if (id < 0) return;

        UUID uuid = event.getUser().getUUID();
        if (!viewers.contains(uuid)) return;

        WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
        Column column = wrapper.getColumn();

        double cx = this.centerX;
        double cz = this.centerZ;
        double limitSq = this.limitSq;

        for (BaseChunk section : column.getChunks()) {
            if (!(section instanceof Chunk_v1_18 modern)) continue;

            // biomes are stored as a 4x4x4 grid of cells per 16x16x16 section
            DataPalette biomes = modern.getBiomeData();
            for (int cellX = 0; cellX < 4; cellX++) {
                for (int cellZ = 0; cellZ < 4; cellZ++) {
                    double dx = column.getX() * 16 + cellX * 4 + 2 - cx;
                    double dz = column.getZ() * 16 + cellZ * 4 + 2 - cz;
                    if (dx * dx + dz * dz > limitSq) continue;

                    for (int cellY = 0; cellY < 4; cellY++) {
                        biomes.set(cellX, cellY, cellZ, id);
                    }
                }
            }
        }

        // we read the packet through a wrapper, so it has to be re-encoded from that wrapper
        event.markForReEncode(true);
    }
}

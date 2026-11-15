package us.dingl.VowSMPPlugin.Easter;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.help.HelpTopic;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;
import us.dingl.VowSMPPlugin.ConfigKey;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// an easter egg: blocks that hiss out campfire smoke and clang like an anvil every so often.
/// Sources are saved in config as "world,x,y,z" and managed with /steam (see SteamCommand),
/// which only the players in ALLOWED can see.
public final class SteamVents implements Listener {

    private static final Set<String> ALLOWED = Set.of("yelahflow_");
    /// Paper lists every plugin command in /help to everyone, ignoring requires(). Its help topics are tied
    /// to this instead - off for everyone (ops included) and handed only to the players above.
    private static final String HELP_PERMISSION = "vow.steam";
    private static final List<String> HELP_TOPICS = List.of("/steam", "/vowsmpplugin:steam");

    public static final int DEFAULT_INTERVAL = 30;

    private final VowSMPPlugin plugin;
    private final Set<String> sources = new LinkedHashSet<>();
    private int interval;
    private BukkitTask task;

    public SteamVents(VowSMPPlugin plugin) {
        this.plugin = plugin;
        sources.addAll(plugin.getConfig().getStringList(ConfigKey.STEAM_SOURCES.getPath()));
        interval = Math.max(1, plugin.getConfig().getInt(ConfigKey.STEAM_INTERVAL.getPath(), DEFAULT_INTERVAL));
        restartTimer();

        PluginManager pm = plugin.getServer().getPluginManager();
        if (pm.getPermission(HELP_PERMISSION) == null) {
            pm.addPermission(new Permission(HELP_PERMISSION, PermissionDefault.FALSE));
        }
        Bukkit.getOnlinePlayers().forEach(this::grantHelp);
        // help topics are built once the server finishes starting, so wait a tick
        Bukkit.getScheduler().runTask(plugin, () -> HELP_TOPICS.forEach(name -> {
            HelpTopic topic = Bukkit.getHelpMap().getHelpTopic(name);
            if (topic != null) topic.amendCanSee(HELP_PERMISSION);
        }));
    }

    public static boolean isAllowed(Player player) {
        return ALLOWED.contains(player.getName().toLowerCase(Locale.ROOT));
    }

    /// returns false if it was already a source
    public boolean add(Block block) {
        if (!sources.add(key(block))) return false;
        save();
        return true;
    }

    /// returns false if it wasn't a source
    public boolean remove(Block block) {
        if (!sources.remove(key(block))) return false;
        save();
        return true;
    }

    public int clear() {
        int count = sources.size();
        sources.clear();
        save();
        return count;
    }

    public List<String> list() {
        return new ArrayList<>(sources);
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int ticks) {
        interval = ticks;
        plugin.getConfig().set(ConfigKey.STEAM_INTERVAL.getPath(), ticks);
        plugin.saveConfig();
        restartTimer();
    }

    private void restartTimer() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::puff, interval, interval);
    }

    private void puff() {
        for (String source : sources) {
            Location loc = parse(source);
            // never load a chunk just for this - nobody's there to hear it
            if (loc == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;

            World world = loc.getWorld();
            Location top = loc.clone().add(0.5, 1.0, 0.5);
            world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, top, 6, 0.2, 0.1, 0.2, 0.02, null, true);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, top, 10, 0.25, 0.1, 0.25, 0.03, null, true);
            world.spawnParticle(Particle.LARGE_SMOKE, top, 8, 0.3, 0.2, 0.3, 0.05);
            world.spawnParticle(Particle.CLOUD, top, 6, 0.2, 0.1, 0.2, 0.05);

            world.playSound(top, Sound.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 1.0f, 0.5f + (float) Math.random());
            world.playSound(top, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.0f, 0.6f);
        }
    }

    private void save() {
        plugin.getConfig().set(ConfigKey.STEAM_SOURCES.getPath(), new ArrayList<>(sources));
        plugin.saveConfig();
    }

    private static String key(Block block) {
        return block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    /// null if the world is gone or the entry is broken
    private static Location parse(String source) {
        String[] parts = source.split(",");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void grantHelp(Player player) {
        if (isAllowed(player)) player.addAttachment(plugin, HELP_PERMISSION, true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        grantHelp(event.getPlayer());
    }
}

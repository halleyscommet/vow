package us.dingl.VowSMPPlugin.Bounty;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import us.dingl.VowSMPPlugin.ConfigKey;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/// the event bounty tag. Whoever holds it is on a gold scoreboard team (gold name above their head and in tab).
/// When a holder is killed by another player, the tag moves to whoever got the kill credit.
/// Any other death (fall, mobs, lava, themselves) and they keep it.
public final class BountyManager implements Listener {

    private static final String TEAM_NAME = "vow_bounty";

    private final VowSMPPlugin plugin;
    private final Set<UUID> holders = new LinkedHashSet<>();

    public BountyManager(VowSMPPlugin plugin) {
        this.plugin = plugin;
        for (String s : plugin.getConfig().getStringList(ConfigKey.BOUNTY_HOLDERS.getPath())) {
            try {
                holders.add(UUID.fromString(s));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring invalid UUID in " + ConfigKey.BOUNTY_HOLDERS.getPath() + ": " + s);
            }
        }
    }

    /// rebuilds the team from the saved holders - the team itself lives in the world's scoreboard
    /// and could be stale (renamed players, edited config, someone poking it with /team)
    public void start() {
        Team team = team();
        for (String entry : new ArrayList<>(team.getEntries())) {
            team.removeEntry(entry);
        }
        for (UUID id : holders) {
            team.addEntry(plugin.getPlayerName(id));
        }
    }

    public boolean has(UUID id) {
        return holders.contains(id);
    }

    public Set<UUID> getHolders() {
        return Collections.unmodifiableSet(holders);
    }

    /// returns false if they already had it
    public boolean give(UUID id) {
        if (!holders.add(id)) return false;
        team().addEntry(plugin.getPlayerName(id));
        save();
        return true;
    }

    /// returns false if they didn't have it
    public boolean take(UUID id) {
        if (!holders.remove(id)) return false;
        team().removeEntry(plugin.getPlayerName(id));
        save();
        return true;
    }

    public void clear() {
        for (UUID id : List.copyOf(holders)) {
            take(id);
        }
    }

    // MONITOR so vows that cancel or tweak the death have had their say first
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (!has(victim.getUniqueId())) return;
        if (killer == null || killer.equals(victim)) return; // not killed by a player - they keep it

        take(victim.getUniqueId());
        boolean alreadyHad = !give(killer.getUniqueId());

        Bukkit.broadcast(Component.text(killer.getName() + " killed " + victim.getName()
                + (alreadyHad ? " and took their bounty!" : " and claimed the bounty!"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.6f, 1.2f);
        }
    }

    /// names can change between sessions, so re-add under the current one
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Team team = team();
        if (has(player.getUniqueId())) {
            team.addEntry(player.getName());
        } else if (team.hasEntry(player.getName())) {
            team.removeEntry(player.getName());
        }
    }

    private Team team() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(TEAM_NAME);
        if (team == null) {
            team = board.registerNewTeam(TEAM_NAME);
        }
        // checked every time in case someone changed it with /team
        if (!team.hasColor() || team.color() != NamedTextColor.GOLD) {
            team.color(NamedTextColor.GOLD);
        }
        return team;
    }

    private void save() {
        plugin.getConfig().set(ConfigKey.BOUNTY_HOLDERS.getPath(), holders.stream().map(UUID::toString).toList());
        plugin.saveConfig();
    }
}

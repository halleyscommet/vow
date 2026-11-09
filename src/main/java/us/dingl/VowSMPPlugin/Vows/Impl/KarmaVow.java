package us.dingl.VowSMPPlugin.Vows.Impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.TurtleMasterBan;
import us.dingl.VowSMPPlugin.Vows.Vow;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/// /blink teleports her 10 blocks the way she's looking (like /tp ^ ^ ^10) on a 20 second cooldown, with the
/// ender pearl sound for everyone nearby. She stops short of anything solid in the way, so she can't end up inside a wall.
/// In exchange, she has 8 hearts instead of 10 and can't use Turtle Master.
public class KarmaVow extends Vow implements TurtleMasterBan.Banned {

    private static final double DISTANCE = 10.0;
    /// how finely the path is checked for walls - thinner than any block with a hitbox (panes are 0.125)
    private static final double STEP = 0.1;
    private static final double HEALTH_PENALTY = -4.0;
    private static final int COOLDOWN_TICKS = 20 * 20;
    /// how often the action bar counts down
    private static final long COOLDOWN_DISPLAY_TICKS = 2L;

    private final NamespacedKey healthKey;
    /// server tick her blink is ready again
    private final Map<UUID, Integer> readyAt = new HashMap<>();

    public KarmaVow(VowSMPPlugin plugin) {
        super(plugin);
        healthKey = new NamespacedKey(plugin, "karma_health");

        // the lives bar only refreshes every 2 seconds, so count the cooldown down ourselves
        Bukkit.getScheduler().runTaskTimer(plugin, this::cooldownTick, COOLDOWN_DISPLAY_TICKS, COOLDOWN_DISPLAY_TICKS);
    }

    @Override
    public String id() {
        return "karma";
    }

    @Override
    public String name() {
        return "Karma vow";
    }

    @Override
    public String description() {
        return "Type /blink to teleport " + (int) DISTANCE + " blocks the way you're looking ("
                + COOLDOWN_TICKS / 20 + " second cooldown), "
                + "but you only have 8 hearts and can't use Turtle Master.";
    }

    @Override
    public Material icon() {
        return Material.ENDER_PEARL;
    }

    @Override
    public void onGain(Player player) {
        applyHealthPenalty(player);
    }

    @Override
    public void onLose(Player player) {
        removeHealthPenalty(player);
        readyAt.remove(player.getUniqueId());
    }

    @Override
    public void tick(Player player, int lives) {
        // something else (like /attribute) could have stripped it
        applyHealthPenalty(player);
    }

    @Override
    public Component actionBarStatus(Player player) {
        Component label = Component.text("Blink: ", NamedTextColor.GRAY);
        int remaining = remainingCooldown(player);
        if (remaining <= 0) {
            return label.append(Component.text("READY", NamedTextColor.GREEN));
        }
        return label.append(Component.text((remaining + 19) / 20 + "s", NamedTextColor.RED));
    }

    /// runs /blink. Returns false if it's on cooldown, there was no room, or something (a stun, the
    /// ritual storm) blocked it - only a blink that happens starts the cooldown.
    public boolean activate(Player player) {
        int remaining = remainingCooldown(player);
        if (remaining > 0) {
            player.sendMessage(Component.text("Blink is on cooldown for " + (remaining + 19) / 20 + "s.", NamedTextColor.RED));
            return false;
        }

        Location from = player.getLocation();
        Vector direction = from.getDirection();
        World world = player.getWorld();
        BoundingBox box = player.getBoundingBox();

        // walk the path and keep the furthest spot her whole hitbox fits
        double reached = 0;
        for (double d = STEP; d <= DISTANCE + 1e-9; d += STEP) {
            double step = Math.min(d, DISTANCE);
            if (world.hasCollisionsIn(box.clone().shift(direction.clone().multiply(step)))) break;
            reached = step;
        }
        if (reached < 0.5) {
            player.sendActionBar(Component.text("No room to blink", NamedTextColor.RED));
            return false;
        }

        Location to = from.clone().add(direction.multiply(reached));
        // teleports fail while riding something
        if (player.isInsideVehicle()) player.leaveVehicle();
        // as an ender pearl, so whatever blocks pearls (a Jax stun, the ritual storm) blocks this too
        if (!player.teleport(to, TeleportCause.ENDER_PEARL)) return false;

        effects(from);
        effects(to);
        readyAt.put(player.getUniqueId(), Bukkit.getCurrentTick() + COOLDOWN_TICKS);
        plugin.getLivesActionBar().update(player.getUniqueId());
        return true;
    }

    private int remainingCooldown(Player player) {
        Integer ready = readyAt.get(player.getUniqueId());
        return ready == null ? 0 : ready - Bukkit.getCurrentTick();
    }

    private void cooldownTick() {
        int now = Bukkit.getCurrentTick();
        // keep each player in the map for one more refresh after it runs out, so the bar flips to READY right away
        readyAt.entrySet().removeIf(entry -> {
            plugin.getLivesActionBar().update(entry.getKey());
            return entry.getValue() <= now;
        });
    }

    /// what a landing ender pearl plays and shows, for everyone in earshot
    private static void effects(Location at) {
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
        at.getWorld().spawnParticle(Particle.PORTAL, at.clone().add(0, 1, 0), 32, 0.3, 0.6, 0.3, 0.5);
    }

    private void applyHealthPenalty(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null || maxHealth.getModifier(healthKey) != null) return;
        maxHealth.addModifier(new AttributeModifier(healthKey, HEALTH_PENALTY, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void removeHealthPenalty(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(healthKey);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // the modifier is saved with the player, so it's still there if she lost the vow while offline
        if (!has(event.getPlayer())) {
            removeHealthPenalty(event.getPlayer());
        }
    }
}

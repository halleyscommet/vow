package us.dingl.VowSMPPlugin.Vows.Impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/// /swap trades places with the player he's looking at (20 blocks, in sight) on a 30 second cooldown.
public class Whimply2Vow extends Vow {

    private static final double RANGE = 20.0;
    /// how far off his crosshair a player can be and still get swapped
    private static final double AIM_TOLERANCE = 0.5;
    private static final int COOLDOWN_TICKS = 30 * 20;
    /// how often the action bar counts down
    private static final long COOLDOWN_DISPLAY_TICKS = 2L;

    /// server tick his swap is ready again
    private final Map<UUID, Integer> readyAt = new HashMap<>();

    public Whimply2Vow(VowSMPPlugin plugin) {
        super(plugin);
        // the lives bar only refreshes every 2 seconds, so count the cooldown down ourselves
        Bukkit.getScheduler().runTaskTimer(plugin, this::cooldownTick, COOLDOWN_DISPLAY_TICKS, COOLDOWN_DISPLAY_TICKS);
    }

    @Override
    public String id() {
        return "whimply2";
    }

    @Override
    public String name() {
        return "Whimply 2 vow";
    }

    @Override
    public String description() {
        return "Type /swap to trade places with the player you're looking at ("
                + (int) RANGE + " blocks, " + COOLDOWN_TICKS / 20 + " second cooldown).";
    }

    @Override
    public Material icon() {
        return Material.ENDER_EYE;
    }

    @Override
    public void onLose(Player player) {
        readyAt.remove(player.getUniqueId());
    }

    @Override
    public Component actionBarStatus(Player player) {
        Component label = Component.text("Swap: ", NamedTextColor.GRAY);
        int remaining = remainingCooldown(player);
        if (remaining <= 0) {
            return label.append(Component.text("READY", NamedTextColor.GREEN));
        }
        return label.append(Component.text((remaining + 19) / 20 + "s", NamedTextColor.RED));
    }

    /// runs /swap. Returns false if it's on cooldown, there's no one to swap with, or a teleport was
    /// blocked (a Jax stun or the ritual storm) - only a swap that happens starts the cooldown.
    public boolean activate(Player whimply) {
        int remaining = remainingCooldown(whimply);
        if (remaining > 0) {
            whimply.sendMessage(Component.text("Swap is on cooldown for " + (remaining + 19) / 20 + "s.", NamedTextColor.RED));
            return false;
        }

        Player target = findTarget(whimply);
        if (target == null) {
            whimply.sendMessage(Component.text("No one in range to swap with.", NamedTextColor.RED));
            return false;
        }

        Location whimplyAt = whimply.getLocation();
        Location targetAt = target.getLocation();
        // each keeps facing the way they were
        Location whimplyTo = targetAt.clone();
        whimplyTo.setYaw(whimplyAt.getYaw());
        whimplyTo.setPitch(whimplyAt.getPitch());
        Location targetTo = whimplyAt.clone();
        targetTo.setYaw(targetAt.getYaw());
        targetTo.setPitch(targetAt.getPitch());

        // teleports fail while riding something
        whimply.leaveVehicle();
        target.leaveVehicle();
        // his side goes as an ender pearl, so a Jax stun or the ritual storm stops him like it stops pearls
        if (!whimply.teleport(whimplyTo, TeleportCause.ENDER_PEARL)) return false;
        if (!target.teleport(targetTo, TeleportCause.PLUGIN)) {
            // their side was blocked (the storm) - put him back so nobody ends up stacked
            whimply.teleport(whimplyAt, TeleportCause.PLUGIN);
            whimply.sendMessage(Component.text("Couldn't swap with " + target.getName() + ".", NamedTextColor.RED));
            return false;
        }

        effects(whimplyAt);
        effects(targetAt);
        whimply.sendMessage(Component.text("Swapped places with " + target.getName() + ".", NamedTextColor.GOLD));
        target.sendMessage(Component.text(whimply.getName() + " swapped places with you!", NamedTextColor.RED));
        readyAt.put(whimply.getUniqueId(), Bukkit.getCurrentTick() + COOLDOWN_TICKS);
        plugin.getLivesActionBar().update(whimply.getUniqueId());
        return true;
    }

    /// the closest player whose (slightly padded) hitbox is under his crosshair, within range and in sight
    private Player findTarget(Player whimply) {
        Vector eye = whimply.getEyeLocation().toVector();
        Vector direction = whimply.getEyeLocation().getDirection();

        Player closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Player other : whimply.getWorld().getPlayers()) {
            if (other.equals(whimply) || other.isDead()
                    || other.getGameMode() == GameMode.SPECTATOR || other.getGameMode() == GameMode.CREATIVE) {
                continue;
            }

            RayTraceResult hit = other.getBoundingBox().expand(AIM_TOLERANCE).rayTrace(eye, direction, RANGE);
            if (hit == null) continue;

            double distance = hit.getHitPosition().distance(eye);
            if (distance < closestDistance && whimply.hasLineOfSight(other)) {
                closest = other;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private static void effects(Location at) {
        at.getWorld().playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
        at.getWorld().spawnParticle(Particle.REVERSE_PORTAL, at.clone().add(0, 1, 0), 32, 0.3, 0.6, 0.3, 0.05);
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
}

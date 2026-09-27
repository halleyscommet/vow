package us.dingl.VowSMPPlugin.Listeners.Ritual;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;
import us.dingl.VowSMPPlugin.Ritual.Storm;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

public class StormConfinementListener implements Listener {

    private static final double LAUNCH_SPEED = 2;
    private static final double LAUNCH_UP = 1;

    private final VowSMPPlugin plugin;
    private final Storm storm;

    public StormConfinementListener(VowSMPPlugin plugin, Storm storm) {
        this.plugin = plugin;
        this.storm = storm;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isConfined(player)) return;

        Location clamped = clampToStorm(event.getTo());
        if (clamped == null) return;

        event.setTo(clamped);
        launchInward(player, clamped);
    }

    // PlayerMoveEvent handlers never see teleports (ender pearls, chorus fruit, /home, /tpa...),
    // and PlayerPortalEvent has its own handler list, so both need their own check
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        blockEscape(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        blockEscape(event);
    }

    private void blockEscape(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!isConfined(player) || isInsideStorm(event.getTo())) return;

        event.setCancelled(true);
        player.sendMessage(Component.text("You can't leave the storm during the ritual.", NamedTextColor.RED));
    }

    // a player riding something doesn't fire PlayerMoveEvent, the vehicle moves instead
    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (storm.getConfinedPlayerId() == null) return;

        Vehicle vehicle = event.getVehicle();
        for (Entity passenger : vehicle.getPassengers()) {
            if (!(passenger instanceof Player player) || !isConfined(player)) continue;

            Location clamped = clampToStorm(event.getTo());
            if (clamped == null) return;

            vehicle.removePassenger(player);
            player.teleport(clamped.setDirection(player.getLocation().getDirection()));
            launchInward(player, clamped);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!storm.isInitiator(player.getUniqueId())) return;

        // dying ends the ritual, no reward
        storm.fail();
        Bukkit.broadcast(Component.text("The revival for ")
                .append(Component.text(player.getName(), NamedTextColor.DARK_RED, TextDecoration.ITALIC, TextDecoration.BOLD))
                .append(Component.text(" has failed!")));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // don't leave a storm running (or starting) for a player who isn't there
        if (storm.isInitiator(event.getPlayer().getUniqueId())) {
            storm.stop();
        }
    }

    private boolean isConfined(Player player) {
        return player.getUniqueId().equals(storm.getConfinedPlayerId()) && storm.getCenter() != null;
    }

    /// small tolerance so our own clamped positions (which sit right on the wall) still count as inside
    private boolean isInsideStorm(Location location) {
        Location center = storm.getCenter();
        if (location == null || !location.getWorld().equals(center.getWorld())) return false;

        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        double limit = storm.getRadius() + 1;
        return dx * dx + dz * dz <= limit * limit;
    }

    /// null if the location is inside the storm, otherwise the closest point on the wall.
    /// anything in another world counts as outside and gets pulled back to the center's world
    private Location clampToStorm(Location to) {
        Location center = storm.getCenter();
        double radius = storm.getRadius();

        if (!to.getWorld().equals(center.getWorld())) {
            return center.clone().add(0, 1, 0);
        }

        double dx = to.getX() - center.getX();
        double dz = to.getZ() - center.getZ();
        double distSq = dx * dx + dz * dz;
        if (distSq <= radius * radius) return null;

        double scale = radius / Math.sqrt(distSq);
        Location clamped = to.clone();
        clamped.setX(center.getX() + dx * scale);
        clamped.setZ(center.getZ() + dz * scale);
        return clamped;
    }

    private void launchInward(Player player, Location from) {
        Location center = storm.getCenter();
        double dx = from.getX() - center.getX();
        double dz = from.getZ() - center.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist == 0) return;

        Vector launch = new Vector(-dx / dist * LAUNCH_SPEED, LAUNCH_UP, -dz / dist * LAUNCH_SPEED);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isValid()) {
                player.setVelocity(launch);
            }
        });
    }
}

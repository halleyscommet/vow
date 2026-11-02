package us.dingl.VowSMPPlugin.Vows.Impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// double jump (double tap space in the air) on a 5 second cooldown, shown in the action bar.
/// She takes no fall damage when landing from a double jump.
/// In exchange, she has 8 hearts instead of 10.
/// (Traps are also banned, but admins enforce that.)
public class HarperVow extends Vow {

    /// rises about 2.5 blocks from where she taps, so she can clear 2 block walls with room to spare
    private static final double JUMP_UP = 0.618;
    private static final double JUMP_FORWARD = 0.7;
    private static final int COOLDOWN_TICKS = 5 * 20;
    /// both presses have to land this close together, same as vanilla's double tap to fly
    private static final int DOUBLE_TAP_TICKS = 7;
    /// how often the cooldown in the action bar counts down
    private static final long COOLDOWN_DISPLAY_TICKS = 2L;

    private static final double HEALTH_PENALTY = -4.0;

    private final NamespacedKey healthKey;
    /// server tick of her last jump press, to spot the second tap
    private final Map<UUID, Integer> lastJumpPress = new HashMap<>();
    /// server tick her double jump is ready again
    private final Map<UUID, Integer> readyAt = new HashMap<>();
    /// players who double jumped and haven't landed yet, so the landing doesn't hurt
    private final Set<UUID> midJump = new HashSet<>();

    public HarperVow(VowSMPPlugin plugin) {
        super(plugin);
        healthKey = new NamespacedKey(plugin, "harper_health");

        // the lives bar only refreshes every 2 seconds, so count the cooldown down ourselves
        Bukkit.getScheduler().runTaskTimer(plugin, this::cooldownTick, COOLDOWN_DISPLAY_TICKS, COOLDOWN_DISPLAY_TICKS);
    }

    @Override
    public String id() {
        return "harper";
    }

    @Override
    public String name() {
        return "Harper vow";
    }

    @Override
    public String description() {
        return "Double tap space to double jump (5 second cooldown, no fall damage), but you only have 8 hearts "
                + "and can never use traps.";
    }

    @Override
    public Material icon() {
        return Material.FEATHER;
    }

    @Override
    public void onGain(Player player) {
        applyHealthPenalty(player);
    }

    @Override
    public void onLose(Player player) {
        removeHealthPenalty(player);
        lastJumpPress.remove(player.getUniqueId());
        readyAt.remove(player.getUniqueId());
        midJump.remove(player.getUniqueId());
    }

    @Override
    public void tick(Player player, int lives) {
        // something else (like /attribute) could have stripped it
        applyHealthPenalty(player);
    }

    @Override
    public Component actionBarStatus(Player player) {
        int remaining = remainingCooldown(player);
        if (remaining <= 0) {
            return Component.text("Double jump: ", NamedTextColor.GRAY)
                    .append(Component.text("READY", NamedTextColor.GREEN));
        }
        return Component.text("Double jump: ", NamedTextColor.GRAY)
                .append(Component.text(String.format(Locale.ROOT, "%.1fs", remaining / 20.0), NamedTextColor.RED));
    }

    // double jump

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        // only fresh presses of space - this fires for every input change, including letting go
        if (!event.getInput().isJump() || player.getCurrentInput().isJump() || !has(player)) return;

        UUID id = player.getUniqueId();
        int now = Bukkit.getCurrentTick();
        Integer last = lastJumpPress.put(id, now);
        if (last == null || now - last > DOUBLE_TAP_TICKS) return;
        if (!canDoubleJump(player) || remainingCooldown(player) > 0) return;

        // so a third tap doesn't count as another double tap
        lastJumpPress.remove(id);
        readyAt.put(id, now + COOLDOWN_TICKS);
        midJump.add(id);

        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() > 0) {
            forward.normalize().multiply(JUMP_FORWARD);
        }
        player.setVelocity(forward.setY(JUMP_UP));
        // start the fall over from the top of the new jump
        player.setFallDistance(0);
        plugin.getLivesActionBar().update(id);
    }

    /// only in mid-air, and not while flying, gliding, swimming, climbing or riding
    private boolean canDoubleJump(Player player) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return false;
        if (player.isDead() || player.isOnGround() || player.isFlying() || player.isGliding()) return false;
        if (player.isInWater() || player.isInLava() || player.isClimbing() || player.isInsideVehicle()) return false;
        return true;
    }

    private int remainingCooldown(Player player) {
        Integer ready = readyAt.get(player.getUniqueId());
        return ready == null ? 0 : ready - Bukkit.getCurrentTick();
    }

    private void cooldownTick() {
        // landing is handled before scheduled tasks run, so any fall damage has already been cancelled by now.
        // anything that resets fall distance counts as landing too
        midJump.removeIf(id -> {
            Player player = Bukkit.getPlayer(id);
            return player == null || player.isOnGround() || player.isInWater() || player.isClimbing()
                    || player.isFlying() || player.isGliding() || player.isInsideVehicle();
        });

        int now = Bukkit.getCurrentTick();
        // keep each player in the map for one more refresh after it runs out, so the bar flips to READY right away
        readyAt.entrySet().removeIf(entry -> {
            plugin.getLivesActionBar().update(entry.getKey());
            return entry.getValue() <= now;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (event.getEntity() instanceof Player player && midJump.remove(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastJumpPress.remove(event.getPlayer().getUniqueId());
        midJump.remove(event.getPlayer().getUniqueId());
    }

    // 8 hearts

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

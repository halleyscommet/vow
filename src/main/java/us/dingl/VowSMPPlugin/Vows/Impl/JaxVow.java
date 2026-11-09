package us.dingl.VowSMPPlugin.Vows.Impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import us.dingl.VowSMPPlugin.ConfigKey;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.TrustList;
import us.dingl.VowSMPPlugin.Vows.TrustingVow;
import us.dingl.VowSMPPlugin.Vows.TurtleMasterBan;
import us.dingl.VowSMPPlugin.Vows.Vow;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/// /stun freezes the nearest player he's aiming at within 8 blocks for 1 second, on a 60 second cooldown.
/// Stunned players can't move at all (they hang in the air if they were falling) but can still look
/// around, attack and block. Players he trusts with /vow trust are never targeted. He can't use
/// Turtle Master, and other people's Turtle Master splashes don't affect him (see TurtleMasterBan).
public class JaxVow extends Vow implements TrustingVow, TurtleMasterBan.Banned {

    private static final double RANGE = 8.0;
    /// how far off his crosshair a player can be and still get stunned
    private static final double AIM_TOLERANCE = 0.75;
    private static final int STUN_TICKS = 20;
    private static final int COOLDOWN_TICKS = 60 * 20;
    /// how often the action bar checks for a new target or countdown
    private static final long DISPLAY_TICKS = 2L;
    /// how far (squared) a stunned player can drift before they're put back
    private static final double DRIFT_SQUARED = 0.0001;

    /// what gets blocked outright while stunned - the movement check catches everything else
    private static final Set<TeleportCause> BLOCKED_TELEPORTS =
            Set.of(TeleportCause.ENDER_PEARL, TeleportCause.CONSUMABLE_EFFECT); // consumable = chorus fruit

    /// one modifier key for all of them, so they're easy to find and remove. Transient, so never saved.
    private final NamespacedKey stunKey;
    /// the attribute changes that freeze someone in place. Doing it with attributes (not just teleporting
    /// them back) keeps it smooth on their screen - the movement check only has to catch the odd edge case.
    private final Map<Attribute, AttributeModifier> stunModifiers;

    /// server tick his stun is ready again
    private final Map<UUID, Integer> readyAt = new HashMap<>();
    /// stunned player -> where they're held and when it wears off
    private final Map<UUID, Stun> stunned = new HashMap<>();
    /// what each Jax's action bar last showed, so it's only resent when it changes
    private final Map<UUID, Object> lastShown = new HashMap<>();
    /// players he trusts - his stun never targets them
    private final TrustList trustList;

    private record Stun(Location anchor, int endsAt) {}

    public JaxVow(VowSMPPlugin plugin) {
        super(plugin);
        stunKey = new NamespacedKey(plugin, "jax_stun");
        stunModifiers = Map.of(
                // multiply by zero: no walking, no jumping, no falling
                Attribute.MOVEMENT_SPEED, multiplyByZero(),
                Attribute.JUMP_STRENGTH, multiplyByZero(),
                Attribute.GRAVITY, multiplyByZero(),
                // hits still land, they just don't push
                Attribute.KNOCKBACK_RESISTANCE, add(1),
                Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, add(1));
        trustList = new TrustList(plugin, ConfigKey.JAX_TRUSTED);

        Bukkit.getScheduler().runTaskTimer(plugin, this::stunTick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::displayTick, DISPLAY_TICKS, DISPLAY_TICKS);
    }

    @Override
    public String id() {
        return "jax";
    }

    @Override
    public String name() {
        return "Jax vow";
    }

    @Override
    public String description() {
        return "Type /stun to freeze the player you're aiming at (" + (int) RANGE + " blocks) in place for "
                + STUN_TICKS / 20 + " second (" + COOLDOWN_TICKS / 20 + " second cooldown). "
                + "They can still look around, attack and block. Use /vow trust to protect teammates. "
                + "You can't use Turtle Master.";
    }

    @Override
    public Material icon() {
        return Material.COBWEB;
    }

    @Override
    public void onLose(Player player) {
        readyAt.remove(player.getUniqueId());
        lastShown.remove(player.getUniqueId());
    }

    @Override
    public void shutdown() {
        // transient modifiers would otherwise stay until they relog
        for (UUID id : List.copyOf(stunned.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) unfreeze(player);
        }
        stunned.clear();
    }

    @Override
    public TrustList trustList() {
        return trustList;
    }

    @Override
    public Component actionBarStatus(Player player) {
        Component label = Component.text("Stun: ", NamedTextColor.GRAY);
        int remaining = remainingCooldown(player);
        if (remaining > 0) {
            int seconds = (remaining + 19) / 20;
            return label.append(Component.text(seconds + "s", NamedTextColor.RED));
        }
        Player target = findTarget(player);
        if (target == null) {
            return label.append(Component.text("READY", NamedTextColor.GREEN))
                    .append(Component.text(" (no target)", NamedTextColor.DARK_GRAY));
        }
        return label.append(Component.text(target.getName(), NamedTextColor.GOLD));
    }

    /// runs /stun. Returns false if it's on cooldown or there's no one to stun.
    public boolean activate(Player jax) {
        int remaining = remainingCooldown(jax);
        if (remaining > 0) {
            jax.sendMessage(Component.text("Stun is on cooldown for " + (remaining + 19) / 20 + "s.", NamedTextColor.RED));
            return false;
        }

        Player target = findTarget(jax);
        if (target == null) {
            jax.sendMessage(Component.text("No one in range to stun.", NamedTextColor.RED));
            return false;
        }

        freeze(target);
        readyAt.put(jax.getUniqueId(), Bukkit.getCurrentTick() + COOLDOWN_TICKS);
        jax.sendMessage(Component.text("Stunned " + target.getName() + ".", NamedTextColor.GOLD));
        jax.playSound(jax.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1.4f);
        target.sendMessage(Component.text("You were stunned by " + jax.getName() + "!", NamedTextColor.RED));
        plugin.getLivesActionBar().update(jax.getUniqueId());
        return true;
    }

    public boolean isStunned(Player player) {
        return stunned.containsKey(player.getUniqueId());
    }

    /// the closest player whose (slightly padded) hitbox is under his crosshair, within range and in sight
    private Player findTarget(Player jax) {
        Vector eye = jax.getEyeLocation().toVector();
        Vector direction = jax.getEyeLocation().getDirection();

        Player closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Player other : jax.getWorld().getPlayers()) {
            if (other.equals(jax) || other.isDead() || trustList.isTrusted(jax, other)
                    || other.getGameMode() == GameMode.SPECTATOR || other.getGameMode() == GameMode.CREATIVE) {
                continue;
            }

            BoundingBox box = other.getBoundingBox().expand(AIM_TOLERANCE);
            RayTraceResult hit = box.rayTrace(eye, direction, RANGE);
            if (hit == null) continue;

            double distance = hit.getHitPosition().distance(eye);
            if (distance < closestDistance && jax.hasLineOfSight(other)) {
                closest = other;
                closestDistance = distance;
            }
        }
        return closest;
    }

    // stunning

    private void freeze(Player target) {
        // off horses and boats, and out of an elytra glide, so nothing carries them along
        target.leaveVehicle();
        target.setGliding(false);
        target.setVelocity(new Vector());

        for (Map.Entry<Attribute, AttributeModifier> entry : stunModifiers.entrySet()) {
            AttributeInstance attribute = target.getAttribute(entry.getKey());
            if (attribute == null) continue;
            // a second stun while still stunned just restarts the timer
            attribute.removeModifier(stunKey);
            attribute.addTransientModifier(entry.getValue());
        }
        stunned.put(target.getUniqueId(), new Stun(target.getLocation(), Bukkit.getCurrentTick() + STUN_TICKS));

        Location at = target.getLocation().add(0, target.getHeight() / 2, 0);
        target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 25, 0.3, 0.5, 0.3, 0.05);
        target.getWorld().playSound(at, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.8f);
    }

    private void unfreeze(Player player) {
        stunned.remove(player.getUniqueId());
        removeModifiers(player);
    }

    private void removeModifiers(Player player) {
        for (Attribute type : stunModifiers.keySet()) {
            AttributeInstance attribute = player.getAttribute(type);
            if (attribute != null) attribute.removeModifier(stunKey);
        }
    }

    private void stunTick() {
        int now = Bukkit.getCurrentTick();
        Iterator<Map.Entry<UUID, Stun>> it = stunned.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Stun> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
            } else if (entry.getValue().endsAt() <= now) {
                it.remove();
                removeModifiers(player);
            } else {
                // cancels pushes from water, pistons and anything else that sets velocity
                player.setVelocity(new Vector());
            }
        }
    }

    /// walking, falling, swimming, riptide, wind charges... anything that moves them gets put back.
    /// Only the position is reset, so they can keep looking around.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Stun stun = stunned.get(event.getPlayer().getUniqueId());
        if (stun == null) return;

        Location to = event.getTo();
        Location anchor = stun.anchor();
        if (!Objects.equals(to.getWorld(), anchor.getWorld()) || to.distanceSquared(anchor) > DRIFT_SQUARED) {
            Location back = anchor.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleportAttempt(PlayerTeleportEvent event) {
        if (isStunned(event.getPlayer()) && BLOCKED_TELEPORTS.contains(event.getCause())) {
            event.setCancelled(true);
        }
    }

    /// admin /tp, portals and other plugins still move them - hold them at the new spot instead
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleported(PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Stun stun = stunned.get(id);
        if (stun != null) {
            stunned.put(id, new Stun(event.getTo().clone(), stun.endsAt()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player && isStunned(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (isStunned(event.getPlayer())) unfreeze(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isStunned(player)) unfreeze(player);
        lastShown.remove(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // never saved, but cheap to make sure after a crash or a reload
        removeModifiers(event.getPlayer());
    }

    // action bar

    /// resends his bar whenever his target or the countdown changes. The lives bar only refreshes every 2 seconds.
    private void displayTick() {
        int now = Bukkit.getCurrentTick();
        readyAt.values().removeIf(ready -> ready <= now);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!has(player)) continue;

            int remaining = remainingCooldown(player);
            Object state;
            if (remaining > 0) {
                state = (remaining + 19) / 20;
            } else {
                Player target = findTarget(player);
                state = target == null ? "none" : target.getUniqueId();
            }
            if (!state.equals(lastShown.put(player.getUniqueId(), state))) {
                plugin.getLivesActionBar().update(player.getUniqueId());
            }
        }
    }

    private int remainingCooldown(Player player) {
        Integer ready = readyAt.get(player.getUniqueId());
        return ready == null ? 0 : ready - Bukkit.getCurrentTick();
    }

    private AttributeModifier multiplyByZero() {
        return new AttributeModifier(stunKey, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    private AttributeModifier add(double amount) {
        return new AttributeModifier(stunKey, amount, AttributeModifier.Operation.ADD_NUMBER);
    }
}

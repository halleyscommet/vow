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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/// his hits (melee or projectile, any weapon) go straight through shields - full damage, no shield stun.
/// /swap trades places with the player he's looking at (20 blocks, in sight) on a 30 second cooldown.
/// In exchange, he can't raise a shield himself.
@SuppressWarnings("deprecation") // DamageModifier is the only way to touch shield blocking in the damage event
public class WhimplyVow extends Vow {

    /// the server's own per-modifier formulas (armor, resistance, ...). Not exposed by the API, but we need
    /// them to recompute everything after blocking - when a shield eats the hit, armor etc. were computed on 0.
    private static final Field MODIFIER_FUNCTIONS;

    static {
        try {
            MODIFIER_FUNCTIONS = EntityDamageEvent.class.getDeclaredField("modifierFunctions");
            MODIFIER_FUNCTIONS.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final double SWAP_RANGE = 20.0;
    /// how far off his crosshair a player can be and still get swapped
    private static final double AIM_TOLERANCE = 0.5;
    private static final int SWAP_COOLDOWN_TICKS = 30 * 20;
    /// how often the action bar counts down
    private static final long COOLDOWN_DISPLAY_TICKS = 2L;

    /// server tick his swap is ready again
    private final Map<UUID, Integer> readyAt = new HashMap<>();

    /// victims whose shield we just ignored, waiting for the MONITOR handler to fake the hurt effects
    private final Set<UUID> pierced = new HashSet<>();

    public WhimplyVow(VowSMPPlugin plugin) {
        super(plugin);
        // the lives bar only refreshes every 2 seconds, so count the cooldown down ourselves
        Bukkit.getScheduler().runTaskTimer(plugin, this::cooldownTick, COOLDOWN_DISPLAY_TICKS, COOLDOWN_DISPLAY_TICKS);
    }

    @Override
    public String id() {
        return "whimply";
    }

    @Override
    public String name() {
        return "Whimply vow";
    }

    @Override
    public String description() {
        return "Your hits go straight through shields. Type /swap to trade places with the player you're looking at ("
                + (int) SWAP_RANGE + " blocks, " + SWAP_COOLDOWN_TICKS / 20 + " second cooldown). "
                + "You can't use a shield.";
    }

    @Override
    public Material icon() {
        return Material.NETHERITE_AXE;
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

    // swap

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

        swapEffects(whimplyAt);
        swapEffects(targetAt);
        whimply.sendMessage(Component.text("Swapped places with " + target.getName() + ".", NamedTextColor.GOLD));
        target.sendMessage(Component.text(whimply.getName() + " swapped places with you!", NamedTextColor.RED));
        readyAt.put(whimply.getUniqueId(), Bukkit.getCurrentTick() + SWAP_COOLDOWN_TICKS);
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

            RayTraceResult hit = other.getBoundingBox().expand(AIM_TOLERANCE).rayTrace(eye, direction, SWAP_RANGE);
            if (hit == null) continue;

            double distance = hit.getHitPosition().distance(eye);
            if (distance < closestDistance && whimply.hasLineOfSight(other)) {
                closest = other;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private static void swapEffects(Location at) {
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

    // shields

    // LOWEST so anything else that adjusts damage sees the un-blocked numbers
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getDamageSource().getCausingEntity() instanceof Player attacker) || !has(attacker)) return;
        if (!event.isApplicable(DamageModifier.BLOCKING) || event.getDamage(DamageModifier.BLOCKING) >= 0) return;

        Map<DamageModifier, ? extends Function<? super Double, Double>> functions = modifierFunctions(event);

        // replay the server's modifier chain with blocking zeroed. With BLOCKING back at 0 the server also
        // skips the shield's durability loss and the axe stun.
        double remaining = 0;
        boolean afterBlocking = false;
        for (DamageModifier modifier : DamageModifier.values()) {
            if (!event.isApplicable(modifier)) continue;

            if (modifier == DamageModifier.BLOCKING) {
                event.setDamage(modifier, 0);
                afterBlocking = true;
                continue;
            }
            if (afterBlocking) {
                event.setDamage(modifier, functions.get(modifier).apply(remaining));
            }
            remaining += event.getDamage(modifier);
        }

        pierced.add(event.getEntity().getUniqueId());
    }

    /// the server decided "blocked" before the event, so it still plays the shield sound and skips the red
    /// flash, hurt sound and knockback sync. Put those back so it actually looks like a hit.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageMonitor(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!pierced.remove(entity.getUniqueId()) || event.isCancelled()) return;
        if (!(entity instanceof LivingEntity victim) || event.getFinalDamage() <= 0) return;

        Location from = event.getDamageSource().getSourceLocation();
        if (from != null) {
            double dx = from.getX() - victim.getX();
            double dz = from.getZ() - victim.getZ();
            victim.playHurtAnimation((float) (Math.toDegrees(Math.atan2(dz, dx)) - victim.getYaw()));
        } else {
            victim.playHurtAnimation(0);
        }

        Sound hurtSound = victim.getHurtSound();
        if (hurtSound != null) victim.getWorld().playSound(victim, hurtSound, 1f, 1f);

        // knockback is applied after this event but never sent to a player's client while "blocked" -
        // re-send their velocity once the attack has finished
        if (victim instanceof Player) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (victim.isValid()) victim.setVelocity(victim.getVelocity());
            });
        }
    }

    @EventHandler
    public void onUseShield(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.SHIELD) return;

        // only stops raising the shield - he can still open doors and chests with it in hand
        if (has(event.getPlayer())) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<DamageModifier, ? extends Function<? super Double, Double>> modifierFunctions(EntityDamageEvent event) {
        try {
            return (Map<DamageModifier, ? extends Function<? super Double, Double>>) MODIFIER_FUNCTIONS.get(event);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}

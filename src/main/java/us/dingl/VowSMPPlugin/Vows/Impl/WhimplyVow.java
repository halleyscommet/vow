package us.dingl.VowSMPPlugin.Vows.Impl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import org.bukkit.inventory.ItemStack;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/// his hits (melee or projectile, any weapon) go straight through shields - full damage, no shield stun.
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

    /// victims whose shield we just ignored, waiting for the MONITOR handler to fake the hurt effects
    private final Set<UUID> pierced = new HashSet<>();

    public WhimplyVow(VowSMPPlugin plugin) {
        super(plugin);
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
        return "Your hits go straight through shields, but you can't use a shield.";
    }

    @Override
    public Material icon() {
        return Material.NETHERITE_AXE;
    }

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

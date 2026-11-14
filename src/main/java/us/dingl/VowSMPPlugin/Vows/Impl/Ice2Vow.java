package us.dingl.VowSMPPlugin.Vows.Impl;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.TurtleMasterBan;
import us.dingl.VowSMPPlugin.Vows.Vow;

/// golden apples give Absorption II instead of I. In exchange, he can't use Turtle Master (see TurtleMasterBan).
public class Ice2Vow extends Vow implements TurtleMasterBan.Banned {

    public Ice2Vow(VowSMPPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "ice2";
    }

    @Override
    public String name() {
        return "Ice 2 vow";
    }

    @Override
    public String description() {
        return "Golden apples give you Absorption II instead of I, but you can't use Turtle Master.";
    }

    @Override
    public Material icon() {
        return Material.GOLDEN_APPLE;
    }

    /// a golden apple's Absorption I becomes II. Enchanted golden apples already give IV, so they're left alone.
    @EventHandler(ignoreCancelled = true)
    public void onAbsorption(EntityPotionEffectEvent event) {
        PotionEffect effect = event.getNewEffect();
        if (event.getCause() != EntityPotionEffectEvent.Cause.FOOD || effect == null
                || !effect.getType().equals(PotionEffectType.ABSORPTION) || effect.getAmplifier() != 0) return;
        if (!(event.getEntity() instanceof Player player) || !has(player)) return;

        event.setCancelled(true);
        player.addPotionEffect(effect.withAmplifier(1));
    }
}

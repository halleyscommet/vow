package us.dingl.VowSMPPlugin.Vows.Impl;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;

/// at 7 lives: Speed II. Every death costs 3 lives instead of 1.
public class GoodwyllVow extends Vow {

    private static final int TRIGGER_LIVES = 7;
    private static final int LIVES_LOST = 3;

    public GoodwyllVow(VowSMPPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "goodwyll";
    }

    @Override
    public String name() {
        return "Goodwyll vow";
    }

    @Override
    public String description() {
        return "At " + TRIGGER_LIVES + " lives you get Speed II, but every death costs "
                + LIVES_LOST + " lives.";
    }

    @Override
    public void tick(Player player, int lives) {
        if (lives >= TRIGGER_LIVES) {
            // amplifier 1 = Speed II. 40 ticks so it never runs out between 20-tick refreshes
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, true, false, true));
        }
    }

    @Override
    public int livesLostOnDeath(Player player, int livesBefore, int lost) {
        return LIVES_LOST;
    }
}

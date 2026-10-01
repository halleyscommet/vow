package us.dingl.VowSMPPlugin.Vows.Impl;

import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;

/// at 7 lives: Speed II. Dying at 7 lives costs 3 lives instead of 1.
public class ColleraVow extends Vow {

    public ColleraVow(VowSMPPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "collera";
    }

    @Override
    public String name() {
        return "Collera vow";
    }

    @Override
    public String description() {
        return "You can invite players from off the server with /vow invite, but they only have one life, "
                + "and you lose 2 lives every death.";
    }

    @Override
    public int livesLostOnDeath(Player player, int livesBefore, int lost) {
        return 2;
    }
}
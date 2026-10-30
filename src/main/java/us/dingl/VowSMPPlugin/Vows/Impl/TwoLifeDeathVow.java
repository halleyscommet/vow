package us.dingl.VowSMPPlugin.Vows.Impl;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;

/// every death costs 2 lives instead of 1, and nothing else. For vows whose upsides are
/// handled by admins but still need a downside the plugin enforces.
public class TwoLifeDeathVow extends Vow {

    private static final int LIVES_LOST = 2;

    public TwoLifeDeathVow(VowSMPPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "twolifedeath";
    }

    @Override
    public String name() {
        return "Two Life Death vow";
    }

    @Override
    public String description() {
        return "Every death costs " + LIVES_LOST + " lives instead of 1.";
    }

    @Override
    public Material icon() {
        return Material.WITHER_SKELETON_SKULL;
    }

    @Override
    public int livesLostOnDeath(Player player, int livesBefore, int lost) {
        return LIVES_LOST;
    }
}

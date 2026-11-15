package us.dingl.VowSMPPlugin.Vows.Impl;

import us.dingl.VowSMPPlugin.ConfigKey;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

/// the Guru vow without the resurrection ritual: he still drops to 0 lives and loses 2 per death,
/// but gets the smite item as soon as he takes the vow.
public class Guru2Vow extends GuruVow {

    public Guru2Vow(VowSMPPlugin plugin) {
        super(plugin, "guru2", ConfigKey.GURU2_TRUSTED, false);
    }

    @Override
    public String name() {
        return "Guru 2 vow";
    }
}

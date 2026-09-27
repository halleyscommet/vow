package us.dingl.VowSMPPlugin.Gui;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class GuiManager {

    private static JavaPlugin plugin;

    private GuiManager() {}

    public static void init(JavaPlugin plugin) {
        GuiManager.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(new GuiListener(), plugin);
    }

    static JavaPlugin getPlugin() {
        return plugin;
    }
}
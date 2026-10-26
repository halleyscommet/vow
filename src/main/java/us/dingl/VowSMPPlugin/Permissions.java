package us.dingl.VowSMPPlugin;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

/// every permission the plugin checks. Use these constants instead of typing the strings.
public final class Permissions {

    // everyone
    public static final String LIVES = "vow.lives";
    public static final String WITHDRAW = "vow.withdraw";
    public static final String AREA_MINE = "vow.3x3";
    public static final String RESISTANCE = "vow.res";
    public static final String TRUST = "vow.trust";
    public static final String MYTHICS = "vow.mythics";

    /// ops: /vow admin and everything under it
    public static final String ADMIN = "vow.admin";

    private Permissions() {}

    public static void register(PluginManager pm) {
        add(pm, LIVES, PermissionDefault.TRUE, "Check your lives and toggle the action bar");
        add(pm, WITHDRAW, PermissionDefault.TRUE, "Withdraw lives as items");
        add(pm, AREA_MINE, PermissionDefault.TRUE, "Toggle the Haley vow's 3x3 mining");
        add(pm, RESISTANCE, PermissionDefault.TRUE, "Use the Karma vow's resistance");
        add(pm, TRUST, PermissionDefault.TRUE, "Pick who the Guru vow's smite ignores");
        add(pm, MYTHICS, PermissionDefault.TRUE, "See who holds each mythic item");

        add(pm, ADMIN, PermissionDefault.OP, "Use the admin panel and /vow admin commands");
    }

    private static void add(PluginManager pm, String name, PermissionDefault def, String description) {
        if (pm.getPermission(name) == null) {
            pm.addPermission(new Permission(name, description, def));
        }
    }
}

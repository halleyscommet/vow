package us.dingl.VowSMPPlugin;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.util.LinkedHashMap;
import java.util.Map;

/// every permission the plugin checks. Use these constants instead of typing the strings.
public final class Permissions {

    // everyone
    public static final String LIVES = "vow.lives";
    public static final String WITHDRAW = "vow.withdraw";
    public static final String AREA_MINE = "vow.3x3";
    public static final String INVITE = "vow.invite";

    // ops
    public static final String STATS = "vow.stats";
    public static final String MENU = "vow.menu";
    public static final String VOWS_LIST = "vow.vows.list";
    public static final String LEADERBOARD = "vow.leaderboard";
    public static final String VOWS_MANAGE = "vow.vows.manage";
    public static final String INVITES_FORGIVE = "vow.invites.forgive";
    public static final String RITUAL = "vow.ritual";
    public static final String UPDATE = "vow.update";
    public static final String DEBUG = "vow.debug";

    /// grants every op permission above
    public static final String ADMIN = "vow.admin";

    private Permissions() {}

    public static void register(PluginManager pm) {
        add(pm, LIVES, PermissionDefault.TRUE, "Check your lives and toggle the action bar");
        add(pm, WITHDRAW, PermissionDefault.TRUE, "Withdraw lives as items");
        add(pm, AREA_MINE, PermissionDefault.TRUE, "Toggle the Haley vow's 3x3 mining");
        add(pm, INVITE, PermissionDefault.TRUE, "Invite players with the Collera vow");

        Map<String, Boolean> adminChildren = new LinkedHashMap<>();
        add(pm, adminChildren, STATS, "See death and lives stats");
        add(pm, adminChildren, MENU, "Open the config menu");
        add(pm, adminChildren, LEADERBOARD, "Toggle and move the leaderboard");
        add(pm, adminChildren, VOWS_LIST, "List every vow and who has it");
        add(pm, adminChildren, VOWS_MANAGE, "Give and remove player vows");
        add(pm, adminChildren, INVITES_FORGIVE, "Let Collera invite a player again after their invite ended");
        add(pm, adminChildren, RITUAL, "Start and stop the ritual storm");
        add(pm, adminChildren, UPDATE, "Download plugin updates");
        add(pm, adminChildren, DEBUG, "Testing tools");

        if (pm.getPermission(ADMIN) == null) {
            pm.addPermission(new Permission(ADMIN, "Every Vow SMP admin permission", PermissionDefault.OP, adminChildren));
        }
    }

    private static void add(PluginManager pm, Map<String, Boolean> parent, String name, String description) {
        add(pm, name, PermissionDefault.OP, description);
        parent.put(name, true);
    }

    private static void add(PluginManager pm, String name, PermissionDefault def, String description) {
        if (pm.getPermission(name) == null) {
            pm.addPermission(new Permission(name, description, def));
        }
    }
}

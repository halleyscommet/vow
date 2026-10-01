package us.dingl.VowSMPPlugin.Commands.Vow;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Permissions;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Impl.ColleraVow;

import java.util.UUID;

/// /vow invite <name> - the Collera vow whitelists someone from off the server. They get 0 lives,
/// and dying unwhitelists and kicks them (see DeathListener).
public class InviteSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public InviteSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("invite")
                .requires(CommandUtil.vowHolder(plugin, ColleraVow.class, Permissions.INVITE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            Player player = CommandUtil.requirePlayer(ctx);
                            return invite(player, StringArgumentType.getString(ctx, "player"));
                        }));
    }

    private int invite(Player inviter, String name) {
        if (!(plugin.getVowManager().getVow(inviter.getUniqueId()) instanceof ColleraVow)) {
            inviter.sendMessage(Component.text("Only the Collera vow can invite players.", NamedTextColor.RED));
            return 0;
        }

        // they've probably never joined, so the name -> uuid lookup may have to ask Mojang. Do that off the main thread.
        PlayerProfile profile = Bukkit.createProfile(name);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean found = profile.complete(false);
            Bukkit.getScheduler().runTask(plugin, () -> finishInvite(inviter, name, found ? profile.getId() : null));
        });

        inviter.sendMessage(Component.text("Looking up " + name + "...", NamedTextColor.GRAY));
        return Command.SINGLE_SUCCESS;
    }

    private void finishInvite(Player inviter, String name, UUID id) {
        if (id == null) {
            inviter.sendMessage(Component.text("There's no player named " + name + ".", NamedTextColor.RED));
            return;
        }

        // only people from off the server - otherwise their death would unwhitelist a regular player
        OfflinePlayer target = Bukkit.getOfflinePlayer(id);
        if (plugin.isInvited(id)) {
            inviter.sendMessage(Component.text(name + " is already invited.", NamedTextColor.RED));
            return;
        }
        if (target.isWhitelisted() || target.isOp() || target.hasPlayedBefore() || target.isOnline()) {
            inviter.sendMessage(Component.text(name + " is already on the server.", NamedTextColor.RED));
            return;
        }

        plugin.addInvite(id, inviter.getUniqueId());
        inviter.sendMessage(Component.text(
                "Invited " + name + ". They have no lives, and dying will remove them from the server.",
                NamedTextColor.YELLOW));
    }
}

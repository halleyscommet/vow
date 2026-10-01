package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import us.dingl.VowSMPPlugin.Permissions;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.UUID;

/// /vow forgive <name> - for when an invitee's invite ended unfairly (like dying right after logging out).
/// Lets a Collera invite them again even though they've played before.
public class ForgiveSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public ForgiveSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("forgive")
                .requires(CommandUtil.permission(Permissions.INVITES_FORGIVE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String name = StringArgumentType.getString(ctx, "player");
                            CommandUtil.lookupUuid(plugin, name, id -> forgive(sender, name, id));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void forgive(CommandSender sender, String name, UUID id) {
        if (id == null) {
            sender.sendMessage(Component.text("There's no player named " + name + ".", NamedTextColor.RED));
            return;
        }
        if (plugin.isInvited(id)) {
            sender.sendMessage(Component.text(name + " is still invited, there's nothing to forgive.", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(id);
        if (target.isWhitelisted() || target.isOp()) {
            sender.sendMessage(Component.text(name + " is already on the server, there's nothing to forgive.", NamedTextColor.RED));
            return;
        }

        plugin.forgiveInvite(id);
        sender.sendMessage(Component.text(
                "Forgave " + name + ". A Collera can invite them again with /vow invite " + name + ".",
                NamedTextColor.YELLOW));
    }
}

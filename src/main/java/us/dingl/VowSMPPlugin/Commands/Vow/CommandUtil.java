package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Permissions;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;

import java.util.function.Predicate;

public final class CommandUtil {

    private static final SimpleCommandExceptionType NOT_PLAYER =
            new SimpleCommandExceptionType(() -> "This command can only be used by players");

    private CommandUtil() {}

    public static Player requirePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (ctx.getSource().getSender() instanceof Player player) {
            return player;
        }
        throw NOT_PLAYER.create();
    }

    /// for .requires(...) - hides the command entirely from anyone without the permission
    public static Predicate<CommandSourceStack> permission(String permission) {
        return source -> source.getSender().hasPermission(permission);
    }

    /// for .requires(...) on vow-specific commands - only players with that vow (and the permission) see it.
    /// Admins see it too so they know it exists, so the command itself still has to check for the vow.
    /// VowManager resends a player's commands when their vow changes, so this stays up to date.
    public static Predicate<CommandSourceStack> vowHolder(VowSMPPlugin plugin, Class<? extends Vow> vow, String permission) {
        return source -> {
            CommandSender sender = source.getSender();
            if (sender.hasPermission(Permissions.ADMIN)) return true;
            return sender instanceof Player player
                    && sender.hasPermission(permission)
                    && vow.isInstance(plugin.getVowManager().getVow(player.getUniqueId()));
        };
    }

    /// resolves an ArgumentTypes.player() argument
    public static Player getPlayer(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        return ctx.getArgument(name, PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
    }
}

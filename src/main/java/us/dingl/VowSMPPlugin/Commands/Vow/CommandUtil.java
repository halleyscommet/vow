package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;

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

    /// resolves an ArgumentTypes.player() argument
    public static Player getPlayer(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        return ctx.getArgument(name, PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
    }
}

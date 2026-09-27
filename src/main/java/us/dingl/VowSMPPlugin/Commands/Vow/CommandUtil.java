package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

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
}

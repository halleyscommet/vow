package us.dingl.VowSMPPlugin.Commands.Admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import us.dingl.VowSMPPlugin.Commands.Vow.CommandUtil;
import us.dingl.VowSMPPlugin.Commands.Vow.SubCommand;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.function.IntUnaryOperator;

/// /vow admin lives <player> [set|add <amount>] - works for offline players too
public class LivesAdminCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public LivesAdminCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("lives")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            plugin.getLoggedPlayers().forEach(id -> builder.suggest(plugin.getPlayerName(id)));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> change(ctx, null))
                        .then(Commands.literal("set")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0, VowSMPPlugin.MAX_LIVES))
                                        .executes(ctx -> {
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            return change(ctx, current -> amount - current);
                                        })))
                        .then(Commands.literal("add")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(-VowSMPPlugin.MAX_LIVES, VowSMPPlugin.MAX_LIVES))
                                        .executes(ctx -> {
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            return change(ctx, current -> amount);
                                        }))));
    }

    /// {@code change} maps their current lives to how much to add. null just shows their lives.
    private int change(CommandContext<CommandSourceStack> ctx, IntUnaryOperator change) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "player");

        CommandUtil.lookupUuid(plugin, name, id -> {
            if (id == null) {
                sender.sendMessage(Component.text("No player named " + name, NamedTextColor.RED));
                return;
            }
            int lives = plugin.getPlayerLives(id);
            if (change != null) {
                lives = plugin.updatePlayerLifeCounter(id, change.applyAsInt(lives));
            }
            sender.sendMessage(plugin.getPlayerName(id) + " has " + lives + "/" + VowSMPPlugin.MAX_LIVES + " lives.");
        });
        return Command.SINGLE_SUCCESS;
    }
}

package us.dingl.VowSMPPlugin.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Commands.Vow.CommandUtil;
import us.dingl.VowSMPPlugin.Commands.Vow.SubCommand;
import us.dingl.VowSMPPlugin.Items.LifeItem;
import us.dingl.VowSMPPlugin.Permissions;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

/// registered both as its own command and as a /vow subcommand
public class WithdrawCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public WithdrawCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("withdraw")
                .requires(CommandUtil.permission(Permissions.WITHDRAW))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, VowSMPPlugin.MAX_LIVES))
                        .executes(ctx -> {
                            Player player = CommandUtil.requirePlayer(ctx);
                            int count = IntegerArgumentType.getInteger(ctx, "count");

                            if (count > plugin.getPlayerLives(player.getUniqueId())) {
                                player.sendMessage("Count must be less than or equal to the amount of lives you have");
                                return 0;
                            }

                            plugin.updatePlayerLifeCounter(player.getUniqueId(), -count);
                            player.give(LifeItem.create(count));

                            Component actionBarText = Component.text(
                                            "Withdrew " + count + ((count > 1) ? " lives." : " life.")
                                    )
                                    .color(NamedTextColor.RED)
                                    .decorate(TextDecoration.BOLD)
                                    .decoration(TextDecoration.ITALIC, false);

                            player.sendActionBar(actionBarText);

                            return Command.SINGLE_SUCCESS;
                        }));
    }
}

package us.dingl.VowSMPPlugin.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Commands.Vow.CommandUtil;
import us.dingl.VowSMPPlugin.Items.LifeItem;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

public class WithdrawCommand {

    private final VowSMPPlugin plugin;

    public WithdrawCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("withdraw")
                .requires(source -> source.getSender().hasPermission("vow.withdraw"))
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
                        }))
                .build();
    }
}

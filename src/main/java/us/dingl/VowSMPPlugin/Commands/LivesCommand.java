package us.dingl.VowSMPPlugin.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Commands.Vow.CommandUtil;
import us.dingl.VowSMPPlugin.Commands.Vow.SubCommand;
import us.dingl.VowSMPPlugin.Permissions;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

/// registered both as its own command and as a /vow subcommand
public class LivesCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public LivesCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("lives")
                .requires(CommandUtil.permission(Permissions.LIVES))
                .executes(ctx -> {
                    Player player = CommandUtil.requirePlayer(ctx);
                    int lives = plugin.getPlayerLives(player.getUniqueId());

                    player.sendMessage(Component.text(
                            "You have " + lives + "/" + VowSMPPlugin.MAX_LIVES + " lives.",
                            NamedTextColor.YELLOW));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("actionbar")
                        .executes(ctx -> {
                            Player player = CommandUtil.requirePlayer(ctx);
                            boolean enabled = !plugin.isPlayerLivesShown(player.getUniqueId());
                            return setActionBar(player, enabled);
                        })
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    Player player = CommandUtil.requirePlayer(ctx);
                                    return setActionBar(player, BoolArgumentType.getBool(ctx, "enabled"));
                                })));
    }

    private int setActionBar(Player player, boolean enabled) {
        plugin.getLivesActionBar().setEnabled(player, enabled);
        player.sendMessage(Component.text(
                (enabled ? "Enabled" : "Disabled") + " showing lives in the action bar.",
                NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }
}

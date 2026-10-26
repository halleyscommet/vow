package us.dingl.VowSMPPlugin.Commands.Admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Commands.Vow.CommandUtil;
import us.dingl.VowSMPPlugin.Commands.Vow.SubCommand;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

public class RitualAdminCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public RitualAdminCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("ritual")
                .then(Commands.literal("start")
                        .executes(ctx -> {
                            Player player = CommandUtil.requirePlayer(ctx);
                            if (!plugin.getStorm().start(player, false)) {
                                player.sendMessage("A storm is already active. Use /vow admin ritual stop first.");
                                return 0;
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            plugin.getStorm().stop();
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}

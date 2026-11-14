package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Permissions;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Impl.WhimplyVow;

/// /swap - the Whimply vow's position swap. Registered both as its own command and as a /vow subcommand.
public class SwapSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public SwapSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("swap")
                .requires(CommandUtil.vowHolder(plugin, WhimplyVow.class, Permissions.SWAP))
                .executes(ctx -> {
                    Player player = CommandUtil.requirePlayer(ctx);
                    if (!(plugin.getVowManager().getVow(player.getUniqueId(), WhimplyVow.class) instanceof WhimplyVow whimply)) {
                        player.sendMessage(Component.text("Only the Whimply vow can use this.", NamedTextColor.RED));
                        return 0;
                    }
                    return whimply.activate(player) ? Command.SINGLE_SUCCESS : 0;
                });
    }
}

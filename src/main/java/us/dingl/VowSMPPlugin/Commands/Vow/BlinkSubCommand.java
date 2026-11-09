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
import us.dingl.VowSMPPlugin.Vows.Impl.KarmaVow;

/// /blink - the Karma vow's 10 block teleport. Registered both as its own command and as a /vow subcommand.
public class BlinkSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public BlinkSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("blink")
                .requires(CommandUtil.vowHolder(plugin, KarmaVow.class, Permissions.BLINK))
                .executes(ctx -> {
                    Player player = CommandUtil.requirePlayer(ctx);
                    if (!(plugin.getVowManager().getVow(player.getUniqueId(), KarmaVow.class) instanceof KarmaVow karma)) {
                        player.sendMessage(Component.text("Only the Karma vow can use this.", NamedTextColor.RED));
                        return 0;
                    }
                    return karma.activate(player) ? Command.SINGLE_SUCCESS : 0;
                });
    }
}

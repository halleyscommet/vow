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
import us.dingl.VowSMPPlugin.Vows.Impl.JaxVow;

/// /stun - the Jax vow's stun. Registered both as its own command and as a /vow subcommand.
public class StunSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public StunSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("stun")
                .requires(CommandUtil.vowHolder(plugin, JaxVow.class, Permissions.STUN))
                .executes(ctx -> {
                    Player player = CommandUtil.requirePlayer(ctx);
                    if (!(plugin.getVowManager().getVow(player.getUniqueId(), JaxVow.class) instanceof JaxVow jax)) {
                        player.sendMessage(Component.text("Only the Jax vow can use this.", NamedTextColor.RED));
                        return 0;
                    }
                    return jax.activate(player) ? Command.SINGLE_SUCCESS : 0;
                });
    }
}

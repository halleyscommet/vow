package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Permissions;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Impl.HaleyVow;

/// /vow 3x3 [enabled] - toggles the Haley vow's sneak-to-mine-3x3
public class AreaMineSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public AreaMineSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("3x3")
                .requires(CommandUtil.vowHolder(plugin, HaleyVow.class, Permissions.AREA_MINE))
                .executes(ctx -> set(CommandUtil.requirePlayer(ctx), null))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> set(CommandUtil.requirePlayer(ctx), BoolArgumentType.getBool(ctx, "enabled"))));
    }

    /// null flips the current setting
    private int set(Player player, Boolean enabled) {
        if (!(plugin.getVowManager().getVow(player.getUniqueId()) instanceof HaleyVow haley)) {
            player.sendMessage(Component.text("Only the Haley vow can mine 3x3.", NamedTextColor.RED));
            return 0;
        }

        boolean value = enabled != null ? enabled : !haley.isAreaMiningEnabled(player);
        haley.setAreaMiningEnabled(player, value);
        player.sendMessage(Component.text(
                (value ? "Enabled" : "Disabled") + " 3x3 mining while sneaking.",
                NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }
}

package us.dingl.VowSMPPlugin.Chat;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.ConfigKey;
import us.dingl.VowSMPPlugin.Mythics.MythicManager;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;
import us.dingl.VowSMPPlugin.Vows.VowManager;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/// what the chatbot knows about the server, rebuilt for every message so it's always current: how the
/// server works, every vow (straight from each vow's own description), and the asking player's own state.
///
/// Other players' vows and lives are left out on purpose - knowing who has what is part of the game.
/// Mythic holders are in, since /mythics shows those to everyone anyway.
final class ServerContext {

    private ServerContext() {}

    /// main thread only - it reads plugin state
    static String build(VowSMPPlugin plugin, Player player) {
        StringBuilder out = new StringBuilder();
        VowManager vows = plugin.getVowManager();
        UUID id = player.getUniqueId();

        out.append("""
                HOW THE SERVER WORKS
                - Lives: every player has a life count, from 0 to %d. Dying costs 1 life (some vows make it more). \
                If another player killed you, they get the lives you lost (as many as fit under the max, and only if \
                they have at least 1 life themselves); the rest drop as a Life item. Dying to anything else drops the lost lives as a Life item where you died.
                - Life items: right click one to claim it (can't claim at 0 lives or above the max). \
                /withdraw <count> turns your own lives into Life items you can trade or give away.
                - 0 lives: you're not banned, you keep playing, but you can't claim Life items. To come back you do the \
                Resurrection Ritual: plunge a Curtana (a special iron sword) into the Altar at X 0, Z 0. That starts a \
                10 minute storm around the altar - a red sky, a wall you can't leave, and lightning striking around you. \
                Survive the 10 minutes and you get 3 lives back. Die during it and the ritual fails. Only one ritual can run at a time.
                - Vows: each player takes vows that give them powers, usually with a downside. Everyone gets 1 vow slot; \
                having at least %s lives unlocks more, and dropping back below takes away your newest vow. \
                Vows are given out by the admins. Items a vow gives are soulbound: kept on death, can't be dropped or stored.
                - Mythics: one-of-a-kind items marked "✦ Mythic". Whoever last had one in their inventory is its holder; \
                /mythics shows who holds each one, and it's announced when one changes hands.
                - Commands players can use: /lives (your lives; /lives actionbar toggles showing them), /withdraw <count>, \
                /mythics, /vow trust [player] (Guru, Guru 2 and Jax vows: pick players your abilities ignore), \
                /vow 3x3 (Haley vow: toggle 3x3 mining), /blink (Karma vow), /stun (Jax vow), /swap (Whimply 2 vow), /ai (you).
                """.formatted(VowSMPPlugin.MAX_LIVES, extraSlotThresholds(plugin)));

        out.append("\nEVERY VOW ON THE SERVER\n");
        for (Vow vow : vows.getVows()) {
            out.append("- ").append(vow.name()).append(": ").append(vow.description()).append('\n');
        }

        MythicManager mythics = plugin.getMythicManager();
        if (!mythics.getMythics().isEmpty()) {
            out.append("\nMYTHIC ITEMS AND WHO HOLDS THEM\n");
            for (MythicManager.Mythic mythic : mythics.getMythics()) {
                String name = PlainTextComponentSerializer.plainText().serialize(mythic.name());
                String holder = mythic.holder() == null ? "nobody yet" : plugin.getPlayerName(mythic.holder());
                out.append("- ").append(name).append(": ").append(holder).append('\n');
            }
        }

        List<Vow> own = vows.getVows(id);
        int lives = plugin.getPlayerLives(id);
        out.append("\nTHE PLAYER YOU'RE TALKING TO\n")
                .append("- Name: ").append(player.getName()).append('\n')
                .append("- Lives: ").append(lives).append(" of ").append(VowSMPPlugin.MAX_LIVES).append('\n')
                .append("- Vows: ").append(own.isEmpty() ? "none" : own.stream().map(Vow::name).collect(Collectors.joining(", ")))
                .append(" (").append(vows.getSlots(id)).append(" slot").append(vows.getSlots(id) == 1 ? "" : "s").append(")\n")
                .append("- World: ").append(player.getWorld().getName()).append('\n');

        String online = Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
        out.append("\nONLINE RIGHT NOW: ").append(online).append('\n');

        return out.toString();
    }

    private static String extraSlotThresholds(VowSMPPlugin plugin) {
        List<Integer> counts = plugin.getConfig().getIntegerList(ConfigKey.EXTRA_VOW_LIVES.getPath());
        if (counts.isEmpty()) return "(currently no extra slots)";
        return counts.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
}

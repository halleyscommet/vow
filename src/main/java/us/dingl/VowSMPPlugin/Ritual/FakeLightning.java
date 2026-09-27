package us.dingl.VowSMPPlugin.Ritual;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class FakeLightning {

    /// strikes at the player's own position (used by /vow ritual lightning)
    public void summon(VowSMPPlugin plugin, Player player) {
        Location loc = player.getLocation();
        summon(player, loc.getX(), loc.getY(), loc.getZ());
    }

    /// strikes at an arbitrary position, visible only to this player
    public void summon(Player player, double x, double y, double z) {
        int fakeEntityId = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE / 2, Integer.MAX_VALUE);

        WrapperPlayServerSpawnEntity fakeLightning = new WrapperPlayServerSpawnEntity(
                fakeEntityId,
                Optional.of(UUID.randomUUID()),
                EntityTypes.LIGHTNING_BOLT,
                new Vector3d(x, y, z),
                0f, 0f, 0f, 0, Optional.empty()
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(player, fakeLightning);
    }
}
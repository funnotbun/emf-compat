package strm.emfcompat.horsesync;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import strm.emfcompat.horsesync.compat.EMFCompat;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Drops recorded horse body offsets for horses no longer being rendered.
 *
 * <p>The NeoForge module does this from a {@code ClientTickEvent.Pre} subscriber; Fabric's
 * equivalent is a lifecycle callback, so the body is the same and only the way in differs.</p>
 */
public final class HorseOffsetCleanup {

    private static int cleanupCounter = 0;

    private HorseOffsetCleanup() {
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> tick());
    }

    private static void tick() {
        if (!EMFHorseSyncClient.isEnabled()) {
            EMFCompat.horseBodyOffsets.clear();
            return;
        }

        if (++cleanupCounter % 200 != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            EMFCompat.horseBodyOffsets.clear();
            return;
        }

        var activeHorses = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                .filter(e -> e instanceof AbstractHorse)
                .map(Entity::getUUID)
                .collect(Collectors.toSet());
        EMFCompat.horseBodyOffsets.keySet().retainAll(activeHorses);
    }
}

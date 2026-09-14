package strm.emfcompat.create;

import net.minecraft.world.entity.player.Player;
import dev.juaanp.barehanded.api.BarehandedAPI;

/**
 * Thin wrapper over Barehanded's public API.
 *
 * <p>Only {@code isPlayerGrabbing} is used, and deliberately so: the rest of {@code BarehandedAPI}
 * takes or returns Sable types, and touching those would drag Sable into this addon's compile
 * classpath for no gain. This class is only ever reached behind {@link CreateMods#BAREHANDED},
 * so it never loads when the mod is absent.</p>
 */
public final class BarehandedCompat {

    private BarehandedCompat() {
    }

    /** Returns {@code true} while the player has a structure held in their bare hands. */
    public static boolean isGrabbing(Player player) {
        try {
            return BarehandedAPI.isPlayerGrabbing(player);
        } catch (Throwable t) {
            // EMF swallows a Throwable out of animation evaluation by disabling the model's
            // animations for good, so nothing on this path may escape.
            return false;
        }
    }
}

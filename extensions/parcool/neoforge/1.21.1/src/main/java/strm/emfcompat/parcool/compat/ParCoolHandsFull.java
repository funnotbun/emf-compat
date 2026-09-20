package strm.emfcompat.parcool.compat;

import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/**
 * Whether the player's hands are carrying something that is not an item — a Carry On block or a
 * Barehanded structure.
 *
 * <p>Read by reflection rather than through those addons' own modules: this module cannot depend on
 * either of them, and the check is needed in game logic, not in a render pass, so the render-side
 * pose registry the core keeps is no use here. Both lookups are resolved once and never throw:
 * anything unexpected means "hands free", which leaves ParCool behaving exactly as it does today.</p>
 */
public final class ParCoolHandsFull {

    private static final String CARRY_ON_MANAGER = "tschipp.carryon.common.carry.CarryOnDataManager";
    private static final String BAREHANDED_API = "dev.juaanp.barehanded.api.BarehandedAPI";

    private static boolean resolved;
    private static Method carryOnGetData;
    private static Method carryOnIsCarrying;
    private static Method barehandedIsGrabbing;

    private ParCoolHandsFull() {
    }

    /** True while the player is holding a block or a structure in both hands. */
    public static boolean handsFull(Player player) {
        if (player == null) return false;
        resolve();
        try {
            if (carryOnGetData != null && carryOnIsCarrying != null) {
                Object data = carryOnGetData.invoke(null, player);
                if (data != null && (Boolean) carryOnIsCarrying.invoke(data)) return true;
            }
            if (barehandedIsGrabbing != null && (Boolean) barehandedIsGrabbing.invoke(null, player)) {
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
        return false;
    }

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> manager = Class.forName(CARRY_ON_MANAGER);
            carryOnGetData = manager.getMethod("getCarryData", Player.class);
            carryOnIsCarrying = carryOnGetData.getReturnType().getMethod("isCarrying");
        } catch (ReflectiveOperationException | RuntimeException e) {
            carryOnGetData = null;
            carryOnIsCarrying = null;
        }
        try {
            barehandedIsGrabbing = Class.forName(BAREHANDED_API)
                    .getMethod("isPlayerGrabbing", Player.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            barehandedIsGrabbing = null;
        }
    }
}

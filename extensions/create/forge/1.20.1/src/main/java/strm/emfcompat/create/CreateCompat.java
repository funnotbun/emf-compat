package strm.emfcompat.create;

import net.minecraft.world.entity.player.Player;

/**
 * Create integration helpers.
 *
 * <p>Skyhook is not handled with an EMF pause / force-vanilla-model condition — that stops the
 * pack animation outright, faces included, and throws the pack's model away while hanging.
 * Create poses the whole body during model setup, so the pose is captured and restored over EMF
 * by {@link strm.emfcompat.create.mixin.PlayerSkyhookRendererMixin}.</p>
 */
public final class CreateCompat {

    private CreateCompat() {
    }

    /**
     * Returns true while the player is skyhooking and NEA's item-swap animation should stay out
     * of the way. Gated on the master switch and the NEA item-swap toggle.
     */
    public static boolean shouldDisableItemSwap(Player player) {
        if (!EMFCompatCreateMod.isEnabled() || !EMFCompatCreateMod.isNeaItemSwap()) return false;
        return SkyhookHelper.isSkyhooking(player.getUUID());
    }
}

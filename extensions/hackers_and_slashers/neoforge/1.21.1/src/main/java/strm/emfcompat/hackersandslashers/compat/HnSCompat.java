package strm.emfcompat.hackersandslashers.compat;

import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Reads which of Hackers 'n Slashers' animation layers are currently playing on a player.
 *
 * <p>The mod drives everything through zigythebird's Player Animation library, registering one
 * layer per kind of movement, and exposes their ids as public constants on
 * {@code net.dndats.api.animations.PlayerAnimator}. The ids are rebuilt here rather than imported
 * so this addon compiles without the mod on the classpath — Hackers 'n Slashers is published
 * under "All rights reserved" and is on no Maven, so depending on its jar would mean a local file
 * that a fresh clone (or CI) does not have. Only the animation library, which is on a Maven, is
 * needed to compile.</p>
 *
 * <p>The ids are checked against 2.0-beta2.5. If a future version renames a layer the matching
 * capture simply stops firing — no crash, and the other layers keep working.</p>
 */
public final class HnSCompat {

    /** The mod's own id, and the namespace all of its animation layers live under. */
    public static final String MOD_ID = "hackersandslashers";

    private static final ResourceLocation ATTACK_LAYER = layer("attack_layer");
    private static final ResourceLocation DEFENSE_LAYER = layer("defense_layer");
    private static final ResourceLocation ACTION_LAYER = layer("action_layer");
    private static final ResourceLocation PARKOUR_LAYER = layer("parkour_layer");
    private static final ResourceLocation POSE_ACTION_LAYER = layer("pose_action_layer");
    private static final ResourceLocation POSE_LAYER = layer("pose_layer");

    /**
     * The layers that play a deliberate, finite movement — a swing, a block, a roll. These are the
     * ones worth holding against the resource pack, and they share one pose source so that
     * whichever is running wins the arms outright.
     */
    private static final ResourceLocation[] ACTION_LAYERS = {
            ATTACK_LAYER, DEFENSE_LAYER, ACTION_LAYER, PARKOUR_LAYER, POSE_ACTION_LAYER
    };

    private HnSCompat() {
    }

    private static ResourceLocation layer(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** {@code true} while any of the action layers is playing. */
    public static boolean isActionActive(AbstractClientPlayer player) {
        for (ResourceLocation layer : ACTION_LAYERS) {
            if (isLayerActive(player, layer)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code true} while the stance layer is playing — the way the mod holds a player who is
     * merely carrying a weapon, as opposed to swinging it. It lasts for as long as the weapon is
     * held, which is why it is captured separately and at a lower priority.
     */
    public static boolean isStanceActive(AbstractClientPlayer player) {
        return isLayerActive(player, POSE_LAYER);
    }

    private static boolean isLayerActive(AbstractClientPlayer player, ResourceLocation layer) {
        try {
            IAnimation animation = PlayerAnimationAccess.getPlayerAnimationLayer(player, layer);
            return animation != null && animation.isActive();
        } catch (Throwable t) {
            // Nothing may escape a render-time check: EMF answers a Throwable out of animation
            // evaluation by disabling every animation on the model for the rest of the session.
            return false;
        }
    }
}

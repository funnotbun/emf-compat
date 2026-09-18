package strm.emfcompat.parcool.compat;

import com.alrex.parcool.client.animation.system.BlendingModelTransform;
import org.jetbrains.annotations.Nullable;

/**
 * ParCool 4's transform for a player, recomputed as if the animations a resource pack plays itself
 * were not running. Implemented on ParCool's {@code AnimationProcessor} by a mixin.
 *
 * <p>ParCool blends a new animation from the one before it, and at its own full weight while the
 * one before is still at full weight. When the pack plays that earlier move, ParCool's pose for it
 * was never on screen, so the new move would start from a pose nobody saw - a vault out of a pack's
 * run snapped straight to ParCool's run. Leaving those animations out makes the new move ease in
 * from nothing, which the core then blends over the pack's animation.</p>
 *
 * <p>Both are also eased in over a short minimum whenever ParCool starts showing a pose after none,
 * since some moves start at full weight.</p>
 */
public interface ParCool4PackTransforms {

    /** The transform for the model's limbs and head: without any pack-played animation. */
    @Nullable
    BlendingModelTransform emfcompat$limbs(@Nullable BlendingModelTransform full);

    /**
     * The transform for the torso, which ParCool puts on the pose stack: without the pack-played
     * animations whose torso transform is only a lean, but with the ones that also turn the body.
     */
    @Nullable
    BlendingModelTransform emfcompat$body(@Nullable BlendingModelTransform full);

    /** The torso yaw, degrees at its weight, of the transform last put on the pose stack for drawing. */
    float emfcompat$shownTorsoYaw();

    void emfcompat$showTorso(@Nullable BlendingModelTransform shown);
}

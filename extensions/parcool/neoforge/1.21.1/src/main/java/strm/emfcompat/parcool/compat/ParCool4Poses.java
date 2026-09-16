package strm.emfcompat.parcool.compat;

import com.alrex.parcool.client.animation.system.AnimatableModelPart;
import com.alrex.parcool.client.animation.system.BlendingModelTransform;
import com.alrex.parcool.client.animation.system.IPlayerAnimatorHolder;
import com.alrex.parcool.client.animation.system.PlayerAnimator;
import com.alrex.parcool.client.animation.system.data.Transform;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.parcool.mixin.ParCool4AnimatorAccessor;
import strm.emfcompat.parcool.mixin.ParCool4ProcessorAccessor;
import strm.emfcompat.parcool.mixin.ParCool4WorkingEntryAccessor;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reads what ParCool 4 is doing to the model. Kept out of the mixin on purpose: a switch over
 * ParCool's enum makes javac emit a synthetic inner class, which a mixin class is a poor
 * place for. It is only ever reached from the ParCool 4 mixin, which the mixin plugin skips
 * unless ParCool 4 is installed, so ParCool 4 classes are never resolved without it.
 */
public final class ParCool4Poses {

    /**
     * Animations that move the player about rather than perform a move: running, swimming,
     * crawling, crouching for a jump. Their arms are only swinging along, so an action that wants
     * the arms - a Better Combat swing - may have them, while ParCool keeps the legs and body.
     * Everything else (vaults, hanging, wall runs, rolls) needs its arms and keeps them.
     */
    private static final Set<String> LOCOMOTION = Set.of(
            "parcool:fast_run", "parcool:fast_swim", "parcool:crawl",
            "parcool:jump_charging", "parcool:charge_jump");

    /** Sources at or above this priority take the arms from a locomotion animation. */
    private static final int ARM_ACTION_PRIORITY = 0;

    private ParCool4Poses() {
    }

    /**
     * Captures ParCool's pose for this frame, or clears it when ParCool is leaving the model to
     * vanilla and the pack.
     *
     * <p>ParCool eases every animation in and out on its own: while its blend factor is below 1 it
     * lets vanilla's {@code setupAnim} run and blends towards its pose from there. Taking the model
     * as it stands would hand the core that vanilla mix, and the move would start from vanilla's
     * idle rather than from what the pack was showing. So the core gets ParCool's pose at full
     * weight plus the factor, and does the same blend over the pack instead.</p>
     */
    public static void capture(AbstractClientPlayer player, PlayerModel<?> model) {
        BlendingModelTransform transform = transform(player);
        if (transform == null) {
            ParCoolPose.clear(player);
            return;
        }

        Set<ParCoolPose.Part> owned;
        if (transform.isOverwriting()) {
            // ParCool reset the model and wrote only the parts it animates, so the parts it left
            // alone sit at their default pose on purpose - it owns the whole model.
            owned = ParCoolPose.WHOLE_MODEL;
        } else {
            // Blending: the torso is always among ParCool's parts - it is blended back towards
            // neutral either way, since ParCool leans the body through the pose stack instead.
            owned = EnumSet.of(ParCoolPose.Part.BODY);
            for (AnimatableModelPart part : transform.transformation().transforms().keySet()) {
                owned.add(map(part));
            }
        }

        if (onlyLocomotion(player)
                && PoseManager.hasArmPoseExcept(player.getUUID(), ParCoolPose.SOURCE, ARM_ACTION_PRIORITY)) {
            owned = EnumSet.copyOf(owned);
            owned.remove(ParCoolPose.Part.LEFT_ARM);
            owned.remove(ParCoolPose.Part.RIGHT_ARM);
        }

        float weight = transform.blendFactor();
        ParCoolPose.capture(player, model, owned, (part, modelPart) -> target(transform, part, modelPart), weight);
    }

    @Nullable
    private static BlendingModelTransform transform(AbstractClientPlayer player) {
        if (!(player instanceof IPlayerAnimatorHolder)) return null;
        // The two states in which ParCool 4 skips its own animation (see its PlayerModelMixin).
        if (player.isFallFlying() || player.isPassenger()) return null;
        PlayerAnimator animator = PlayerAnimator.get(player);
        // Without the animations a pack plays: those are left to the pack, and what remains eases in
        // over it rather than out of a ParCool pose that was never on screen.
        return ((ParCool4PackTransforms) (Object) ((ParCool4AnimatorAccessor) animator).emfcompat$processor())
                .emfcompat$limbs(animator.getCurrentTransformation());
    }

    /** Whether every animation ParCool is running on this player is plain locomotion. */
    private static boolean onlyLocomotion(AbstractClientPlayer player) {
        List<?> running = ((ParCool4ProcessorAccessor) ((ParCool4AnimatorAccessor) PlayerAnimator.get(player))
                .emfcompat$processor()).emfcompat$animators();
        if (running.isEmpty()) return false;
        for (Object entry : running) {
            ResourceLocation id = ((ParCool4WorkingEntryAccessor) entry).emfcompat$registration().location();
            if (!LOCOMOTION.contains(id.toString())) return false;
        }
        return true;
    }

    /** ParCool's own apply, onto a part already at its default pose. The body stays neutral. */
    private static void target(BlendingModelTransform transform, ParCoolPose.Part part, ModelPart modelPart) {
        if (part == ParCoolPose.Part.BODY) return;
        Transform partTransform = transform.transformation().transforms().get(unmap(part));
        if (partTransform != null) {
            partTransform.apply(modelPart);
        }
    }

    private static ParCoolPose.Part map(AnimatableModelPart part) {
        return switch (part) {
            case HEAD -> ParCoolPose.Part.HEAD;
            case BODY -> ParCoolPose.Part.BODY;
            case LEFT_ARM -> ParCoolPose.Part.LEFT_ARM;
            case RIGHT_ARM -> ParCoolPose.Part.RIGHT_ARM;
            case LEFT_LEG -> ParCoolPose.Part.LEFT_LEG;
            case RIGHT_LEG -> ParCoolPose.Part.RIGHT_LEG;
        };
    }

    private static AnimatableModelPart unmap(ParCoolPose.Part part) {
        return switch (part) {
            case HEAD -> AnimatableModelPart.HEAD;
            case BODY -> AnimatableModelPart.BODY;
            case LEFT_ARM -> AnimatableModelPart.LEFT_ARM;
            case RIGHT_ARM -> AnimatableModelPart.RIGHT_ARM;
            case LEFT_LEG -> AnimatableModelPart.LEFT_LEG;
            case RIGHT_LEG -> AnimatableModelPart.RIGHT_LEG;
        };
    }
}

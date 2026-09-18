package strm.emfcompat.parcool.mixin;

import com.alrex.parcool.client.animation.system.AnimatableModelPart;
import com.alrex.parcool.client.animation.system.AnimationProcessor;
import com.alrex.parcool.client.animation.system.BlendingModelTransform;
import com.alrex.parcool.client.animation.system.ModelTransform;
import com.alrex.parcool.client.animation.system.data.Transform;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.parcool.compat.ParCool4PackTransforms;
import strm.emfcompat.parcool.compat.ParCoolPackVariables;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * See {@link ParCool4PackTransforms}. Recomputed right after ParCool's own once-a-frame call, by
 * taking the pack-played animations out of the running list for the nested calls and putting the
 * list back exactly as it was, then eased in when ParCool starts showing a pose.
 */
@Mixin(value = AnimationProcessor.class, remap = false)
public abstract class ParCool4PackFilterMixin implements ParCool4PackTransforms {

    @Shadow @Final private AbstractClientPlayer owner;
    @SuppressWarnings("rawtypes")
    @Shadow @Final private ArrayList animators;

    @Shadow
    public abstract BlendingModelTransform getTransformation(boolean firstPersonView, float partial);

    /** How long a ParCool pose takes to ease in over the pack at the least. */
    @Unique private static final long EMFCOMPAT$EASE_IN_NANOS = 200_000_000L;

    /** Render thread only; marks the nested calls. */
    @Unique private static boolean emfcompat$recomputing;

    @Unique @Nullable private BlendingModelTransform emfcompat$limbs;
    @Unique @Nullable private BlendingModelTransform emfcompat$body;
    /** When ParCool last started showing a pose after showing none, or -1 while it shows none. */
    @Unique private long emfcompat$shownSince = -1;
    /** The same for the limbs alone. */
    @Unique private long emfcompat$limbsShownSince = -1;
    /** Whether this frame's transforms were worked out; not in first person, where all is ParCool's. */
    @Unique private boolean emfcompat$ready;

    @SuppressWarnings("unchecked")
    @Inject(method = "getTransformation", at = @At("RETURN"))
    private void emfcompat$forThePack(boolean firstPersonView, float partial,
                                      CallbackInfoReturnable<BlendingModelTransform> cir) {
        if (emfcompat$recomputing) return;
        emfcompat$ready = false;
        // In first person the model is not drawn and the pack reads nothing; the camera needs it all.
        if (firstPersonView) return;
        BlendingModelTransform full = cir.getReturnValue();

        List<Object> lean = new ArrayList<>();
        List<Object> turn = new ArrayList<>();
        if (full != null) {
            for (Object entry : animators) {
                // Not a switch: that would put a synthetic inner class in this mixin.
                ParCoolPackVariables.PackPlay play = ParCoolPackVariables.packPlays(owner, entry);
                if (play == ParCoolPackVariables.PackPlay.LEAN) lean.add(entry);
                else if (play == ParCoolPackVariables.PackPlay.TURN) turn.add(entry);
            }
        }

        BlendingModelTransform limbs = full;
        BlendingModelTransform body = full;
        float limbsKept = 1f;
        float bodyKept = 1f;
        if (!lean.isEmpty() || !turn.isEmpty()) {
            List<Object> packPlayed = new ArrayList<>(lean);
            packPlayed.addAll(turn);
            limbsKept = emfcompat$keptUnder(packPlayed, partial);
            bodyKept = emfcompat$keptUnder(lean, partial);
            List<Object> all = new ArrayList<>(animators);
            emfcompat$recomputing = true;
            try {
                animators.removeIf(entry -> lean.contains(entry) || turn.contains(entry));
                limbs = animators.isEmpty() ? null : getTransformation(false, partial);
                body = limbs;
                if (!turn.isEmpty()) {
                    animators.clear();
                    animators.addAll(all);
                    animators.removeIf(lean::contains);
                    body = animators.isEmpty() ? null : getTransformation(false, partial);

                    // ParCool morphs the torso of the move before (a jump's roll, a slide) into the
                    // pack-played one's over that one's fade-in. With the hands held on a ledge the
                    // leftover roll swings the body around them, so it goes three times as fast.
                    int firstTurn = Integer.MAX_VALUE;
                    float turnFactor = 0f;
                    for (int i = 0; i < all.size(); i++) {
                        if (!turn.contains(all.get(i))) continue;
                        firstTurn = Math.min(firstTurn, i);
                        turnFactor = Math.max(turnFactor, ((ParCool4WorkingEntryAccessor) all.get(i))
                                .emfcompat$animator().getCurrentBlendFactor(false, partial));
                    }
                    List<Object> older = new ArrayList<>();
                    for (int i = 0; i < firstTurn && i < all.size(); i++) {
                        if (!lean.contains(all.get(i))) older.add(all.get(i));
                    }
                    if (body != null && !older.isEmpty()) {
                        animators.removeIf(older::contains);
                        BlendingModelTransform newer = animators.isEmpty() ? null : getTransformation(false, partial);
                        body = emfcompat$torsoMorph(body, newer, Math.min(1f, turnFactor * 3f));
                    }
                }
            } finally {
                animators.clear();
                animators.addAll(all);
                emfcompat$recomputing = false;
            }
        }

        // ParCool starts some moves at full weight (a vault fades in over 0 ticks): over its own
        // vanilla pose that is a quick snap, over a pack's run it is a jump cut. So a pose that
        // appears after none eases in over a short minimum, torso included.
        long now = System.nanoTime();
        if (limbs == null && body == null) {
            emfcompat$shownSince = -1;
        } else if (emfcompat$shownSince < 0) {
            emfcompat$shownSince = now;
        }
        // The limbs on their own clock too: under a pack-played move that keeps ParCool's torso (a
        // pole climb) the torso is shown all along, and the limbs of the move after it (sliding down
        // into a hang) snapped in over the pack's.
        if (limbs == null) {
            emfcompat$limbsShownSince = -1;
        } else if (emfcompat$limbsShownSince < 0) {
            emfcompat$limbsShownSince = now;
        }
        float t = Math.min(1f, (now - emfcompat$shownSince) / (float) EMFCOMPAT$EASE_IN_NANOS);
        float ease = t * t * (3f - 2f * t);
        float lt = Math.min(1f, (now - emfcompat$limbsShownSince) / (float) EMFCOMPAT$EASE_IN_NANOS);
        float limbsEase = lt * lt * (3f - 2f * lt);
        emfcompat$limbs = emfcompat$eased(limbs, Math.min(ease, limbsEase), limbsKept, true);
        emfcompat$body = emfcompat$eased(body, ease, bodyKept, false);
        // Only a ledge hang turns the torso a wall at a time; under a bar ParCool turns it smoothly itself.
        boolean ledge = false;
        boolean pole = false;
        for (Object entry : turn) {
            String move = ParCoolPackVariables.moveOf(entry);
            if ("hang".equals(move)) ledge = true;
            if ("pole".equals(move)) pole = true;
        }
        emfcompat$body = emfcompat$turnSmoothly(emfcompat$body, ledge, partial, now);
        // Climbing a pole the pack plays FA's ladder climb, which sets its own lean: of ParCool's torso
        // only the turn to face the pole stays, not the 15 degrees it leans the body back by.
        if (pole) emfcompat$body = emfcompat$yawOnly(emfcompat$body);
        emfcompat$ready = true;
    }

    /**
     * How much of the animations left in the list still shows once the removed ones are gone.
     * ParCool lets a newer animation take over the older ones as it fades in - a hang takes over the
     * slide that led into it - and the pack's animation has to do the same when it plays that newer
     * one, or the older ParCool pose would stay on screen until it ended on its own.
     */
    @Unique
    private float emfcompat$keptUnder(List<Object> removed, float partial) {
        int newestKept = -1;
        for (int i = 0; i < animators.size(); i++) {
            if (!removed.contains(animators.get(i))) newestKept = i;
        }
        float kept = 1f;
        for (int i = newestKept + 1; i < animators.size() && newestKept >= 0; i++) {
            float factor = ((ParCool4WorkingEntryAccessor) animators.get(i)).emfcompat$animator()
                    .getCurrentBlendFactor(false, partial);
            // Three times ParCool's own pace: ParCool morphs the older pose into the newer one, but
            // here the older pose would only be shown over the pack's move, which is already there.
            kept *= 1f - Math.max(0f, Math.min(1f, factor * 3f));
        }
        return kept;
    }

    /** How long the torso takes to come round to a new wall, hanging: time constant. */
    @Unique private static final float EMFCOMPAT$TURN_SECONDS = 0.12f;

    /** The torso's yaw in the world as last drawn, degrees; NaN while not smoothing. */
    @Unique private float emfcompat$worldYaw = Float.NaN;
    @Unique private long emfcompat$turnNanos;

    /**
     * Hanging, ParCool turns the torso to face the wall it holds, and going round a corner that wall
     * steps from one face to the corner to the next in as many ticks - a quarter turn in 0.15 s. The
     * torso's yaw in the world is eased towards ParCool's instead, and the difference is turned in
     * ahead of ParCool's own rotation, around the same vertical axis the renderer turns the body by.
     */
    @Unique
    @Nullable
    private BlendingModelTransform emfcompat$turnSmoothly(@Nullable BlendingModelTransform transform, boolean hanging,
                                                         float partial, long now) {
        Transform torso = transform == null ? null : transform.transformation().transforms().get(AnimatableModelPart.BODY);
        if (!hanging || torso == null) {
            emfcompat$worldYaw = Float.NaN;
            return transform;
        }
        org.joml.Vector3f forward = torso.rotation().transform(new org.joml.Vector3f(0, 0, 1));
        float stackYaw = (float) Math.toDegrees(Math.atan2(forward.x, forward.z));
        float bodyRot = net.minecraft.util.Mth.rotLerp(partial, owner.yBodyRotO, owner.yBodyRot);
        // The renderer turns by (180 - bodyRot), then by the torso's rotation.
        float target = stackYaw - bodyRot;
        if (Float.isNaN(emfcompat$worldYaw)) {
            emfcompat$worldYaw = target;
        } else {
            float dt = Math.min(0.1f, (now - emfcompat$turnNanos) / 1e9f);
            emfcompat$worldYaw += net.minecraft.util.Mth.wrapDegrees(target - emfcompat$worldYaw)
                    * (1f - (float) Math.exp(-dt / EMFCOMPAT$TURN_SECONDS));
        }
        emfcompat$turnNanos = now;
        float extra = net.minecraft.util.Mth.wrapDegrees(emfcompat$worldYaw - target);
        if (Math.abs(extra) < 0.01f) return transform;

        org.joml.Quaternionf turn = new org.joml.Quaternionf().rotationY((float) Math.toRadians(extra));
        org.joml.Quaternionf rotation = new org.joml.Quaternionf(turn).mul(torso.rotation());
        org.joml.Vector3f moved = turn.transform(new org.joml.Vector3f(
                torso.translation().x(), torso.translation().y(), torso.translation().z()));
        EnumMap<AnimatableModelPart, Transform> parts = new EnumMap<>(AnimatableModelPart.class);
        parts.putAll(transform.transformation().transforms());
        parts.put(AnimatableModelPart.BODY, new Transform(
                new com.alrex.parcool.client.animation.system.math.Vec3f(moved.x, moved.y, moved.z), rotation));
        return new BlendingModelTransform(new ModelTransform(parts), transform.isOverwriting(),
                transform.blendFactor(), transform.cameraRotation());
    }

    /** The transform with the torso only turned about the vertical: no lean, no shift. */
    @Unique
    @Nullable
    private static BlendingModelTransform emfcompat$yawOnly(@Nullable BlendingModelTransform transform) {
        Transform torso = transform == null ? null : transform.transformation().transforms().get(AnimatableModelPart.BODY);
        if (torso == null) return transform;
        org.joml.Vector3f forward = torso.rotation().transform(new org.joml.Vector3f(0, 0, 1));
        float yaw = (float) Math.atan2(forward.x, forward.z);
        EnumMap<AnimatableModelPart, Transform> parts = new EnumMap<>(AnimatableModelPart.class);
        parts.putAll(transform.transformation().transforms());
        parts.put(AnimatableModelPart.BODY, new Transform(com.alrex.parcool.client.animation.system.math.Vec3f.ZERO,
                new org.joml.Quaternionf().rotationY(yaw)));
        return new BlendingModelTransform(new ModelTransform(parts), transform.isOverwriting(),
                transform.blendFactor(), transform.cameraRotation());
    }

    /** See {@link ParCool4PackTransforms#emfcompat$shownTorsoYaw}. */
    @Unique private float emfcompat$shownYaw;

    @Override
    public float emfcompat$shownTorsoYaw() {
        return emfcompat$shownYaw;
    }

    @Override
    public void emfcompat$showTorso(@Nullable BlendingModelTransform shown) {
        Transform torso = shown == null ? null : shown.transformation().transforms().get(AnimatableModelPart.BODY);
        if (torso == null) {
            emfcompat$shownYaw = 0f;
            return;
        }
        org.joml.Vector3f forward = torso.rotation().transform(new org.joml.Vector3f(0, 0, 1));
        float yaw = (float) Math.toDegrees(Math.atan2(forward.x, forward.z));
        emfcompat$shownYaw = yaw * (shown.isOverwriting() ? 1f : shown.blendFactor());
    }

    /** The torso of one transform morphed into another's, both at their own weight. */
    @Unique
    private static BlendingModelTransform emfcompat$torsoMorph(BlendingModelTransform from,
                                                             @Nullable BlendingModelTransform to, float t) {
        Transform torso = emfcompat$weightedTorso(from).morph(emfcompat$weightedTorso(to), t, true);
        EnumMap<AnimatableModelPart, Transform> parts = new EnumMap<>(AnimatableModelPart.class);
        parts.putAll(from.transformation().transforms());
        parts.put(AnimatableModelPart.BODY, torso);
        return new BlendingModelTransform(new ModelTransform(parts), true, 1f, from.cameraRotation());
    }

    @Unique
    private static Transform emfcompat$weightedTorso(@Nullable BlendingModelTransform transform) {
        Transform torso = transform == null ? null : transform.transformation().transforms().get(AnimatableModelPart.BODY);
        if (torso == null) return Transform.NO_TRANSFORMATION;
        return transform.isOverwriting() ? torso : Transform.NO_TRANSFORMATION.morph(torso, transform.blendFactor(), true);
    }

    /**
     * Caps the transform's weight at the ease-in and scales it by what newer pack-played animations
     * leave of it. The limbs keep ParCool's overwriting flag, which only decides which parts are
     * captured; ParCool's torso hook ignores the weight of an overwriting transform.
     */
    @Unique
    @Nullable
    private static BlendingModelTransform emfcompat$eased(@Nullable BlendingModelTransform transform, float ease,
                                                         float kept, boolean keepOverwriting) {
        if (transform == null) return null;
        float weight = Math.min(transform.blendFactor(), ease) * kept;
        if (weight >= transform.blendFactor()) return transform;
        return new BlendingModelTransform(transform.transformation(), keepOverwriting && transform.isOverwriting(),
                weight, transform.cameraRotation());
    }

    @Override
    public BlendingModelTransform emfcompat$limbs(BlendingModelTransform full) {
        return emfcompat$ready && full != null ? emfcompat$limbs : full;
    }

    @Override
    public BlendingModelTransform emfcompat$body(BlendingModelTransform full) {
        return emfcompat$ready && full != null ? emfcompat$body : full;
    }
}

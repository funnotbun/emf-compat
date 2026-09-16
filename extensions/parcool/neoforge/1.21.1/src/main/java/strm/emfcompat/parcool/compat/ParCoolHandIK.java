package strm.emfcompat.parcool.compat;

import com.alrex.parcool.client.animation.system.AnimatableModelPart;
import com.alrex.parcool.client.animation.system.BlendingModelTransform;
import com.alrex.parcool.client.animation.system.PlayerAnimator;
import com.alrex.parcool.client.animation.system.data.Transform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import strm.emfcompat.parcool.mixin.ParCool4AnimatorAccessor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hands on the ledge: aims each arm at the top of the block in front of it while hanging, for the
 * resource pack to use as its arm angles.
 *
 * <p>A player arm is one bone, so this is aiming rather than a chain solve: the direction from the
 * shoulder to the target gives the arm's x and y rotation in closed form. The target is found in
 * the world - the wall face straight ahead of the shoulder, then the top of that block - and moved
 * into model space through the pose the renderer is about to draw the model with, so ParCool's turn
 * towards the wall and its lift are accounted for whatever they are.</p>
 *
 * <p>The catch is worked out here too, as a vertical offset of the whole body ({@link #catchOffset}):
 * the pack moves the body by it, and the arms are aimed from the moved shoulders, so the hands stay
 * on the ledge while the body drops under them and springs back.</p>
 */
public final class ParCoolHandIK {

    /** Model space: pixels, y down, the model facing -z. Vanilla shoulder pivots. */
    private static final Vector3f RIGHT_SHOULDER = new Vector3f(-5f, 2f, 0f);
    private static final Vector3f LEFT_SHOULDER = new Vector3f(5f, 2f, 0f);
    /** From the shoulder pivot to the end of the hand, in pixels. */
    public static final float ARM_REACH = 10f;
    /** The most a shoulder is raised to let a short arm reach, in pixels. */
    public static final float MAX_LIFT = 4f;
    /** How far past the edge the hand lands on the top face, in blocks. */
    private static final double ONTO_TOP = 0.22;
    /**
     * How far apart, in pixels, the hands go along the ledge when it is behind the player: reaching
     * straight back over the shoulders turns the arms inside out, and a wider grip eases that.
     */
    private static final float BACK_SPREAD = 6f;
    /** A pixel above the top face, so the fingers rest on it rather than in it; in blocks. */
    private static final double HAND_CLEARANCE = 1 / 16.0 * 0.9375;

    /** Body offset of the hardest catch at its start, in pixels; see {@link #catchOffset}. */
    private static final float CATCH_PIXELS = 4f;
    /** A hang that starts within this long of the last one is the same hang. */
    private static final long SAME_HANG_NANOS = 150_000_000L;
    /** How long a fall speed is remembered, so the catch still knows it once the hang has stopped it. */
    private static final long FALL_MEMORY_NANOS = 250_000_000L;

    /**
     * The arm angles for one frame; an arm is not valid when there is no ledge top for its hand.
     * catchOffset is the body's offset for the catch, in pixels, model y (down) positive.
     */
    public record Arms(boolean rightValid, float rightX, float rightY, float rightReach, float rightLift,
                       boolean leftValid, float leftX, float leftY, float leftReach, float leftLift,
                       float catchOffset, float shortfall) {
        static final Arms NONE = new Arms(false, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0);
    }

    /**
     * A hang in progress: when it started and how hard it was caught, -1 to 1 - positive for a catch
     * falling onto the ledge, negative for one jumped up to it.
     */
    private static final class Hang {
        long start;
        long lastSeen;
        float strength;
        /** The highest ParCool has lifted the torso in this hang, blocks. */
        float torsoLift;
        /** The raise the arms last got, pixels, easing back down quickly. */
        float raise;
        /** The raise this hang usually needs, pixels: follows the need slowly, and lets go more slowly. */
        float usualRaise;
    }

    /** How quickly a raise that was only needed for a moment (the pull of a catch) goes away. */
    private static final float RAISE_SECONDS = 0.3f;
    /**
     * How the usual raise follows the need: up within a second, down over several. Turning away from
     * the wall the arms stop needing it, and dropping the body for a look over the shoulder would bob.
     */
    private static final float USUAL_RAISE_UP_SECONDS = 1f;
    private static final float USUAL_RAISE_DOWN_SECONDS = 4f;
    /**
     * Past the catch, a raise the arms suddenly need comes in over this time constant instead of at
     * once: turning, ParCool's poses swing the shoulders for a moment, and a hand a pixel short for
     * that moment shows less than the body hopping up.
     */
    private static final float RAISE_UP_SECONDS = 0.12f;
    /** How long after the catch every raise still comes in at once, holding the hands through the pull. */
    private static final float CATCH_SECONDS = 0.5f;
    /** Model pixels in a block, for the player's 0.9375 model scale. */
    private static final float PIXELS_PER_BLOCK = 16f / 0.9375f;

    /** The fastest vertical movement of the last moments: blocks per tick, up positive, and when. */
    private static final class Fall {
        float speed;
        long at;
    }

    private static final Map<UUID, Hang> HANGS = new HashMap<>();
    private static final Map<UUID, Fall> FALLS = new HashMap<>();

    private record Frame(Matrix4f model, Vec3 camera) {
    }

    private static final long SOLVE_EVERY_NANOS = 2_000_000L;

    /** Each player's model pose as last set up for drawing. Render thread only. */
    private static final Map<UUID, Frame> FRAMES = new HashMap<>();
    private static final Map<UUID, Arms> SOLVED = new HashMap<>();
    private static final Map<UUID, Long> SOLVED_AT = new HashMap<>();

    private ParCoolHandIK() {
    }

    /** Called with the pose stack as it stands right before the model is animated. */
    public static void modelPose(AbstractClientPlayer player, Matrix4f pose) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        FRAMES.put(player.getUUID(), new Frame(new Matrix4f(pose), camera));

        long now = System.nanoTime();
        float speed = (float) (player.getY() - player.yo);
        Fall fall = FALLS.computeIfAbsent(player.getUUID(), k -> new Fall());
        if (Math.abs(speed) >= Math.abs(fall.speed) || now - fall.at > FALL_MEMORY_NANOS) {
            fall.speed = speed;
            fall.at = now;
        }
    }

    /**
     * The body's offset while catching a ledge, in pixels, down positive. Falling onto the ledge it
     * starts a little high, as the hands close on the edge while the body is still coming down, drops
     * under them past rest and springs back; jumping up to it, the other way round. Gone in about a
     * second, and a faster catch swings further.
     */
    private static float catchOffset(Hang hang, long now) {
        float t = (now - hang.start) / 1e9f;
        if (t > 1.2f) return 0f;
        float amplitude = CATCH_PIXELS * hang.strength;
        return amplitude * (float) (Math.exp(-5 * t) * (Math.sin(11 * t) - 0.6 * Math.cos(11 * t)));
    }

    /** The solution for this model evaluation, worked out once however many variables read it. */
    public static Arms arms(AbstractClientPlayer player, @Nullable Vec3 wall) {
        UUID uuid = player.getUUID();
        // Every variable of one model evaluation reads within well under a millisecond.
        long now = System.nanoTime();
        Long solvedAt = SOLVED_AT.get(uuid);
        if (solvedAt != null && now - solvedAt < SOLVE_EVERY_NANOS) return SOLVED.get(uuid);

        Hang hang = HANGS.computeIfAbsent(uuid, k -> new Hang());
        if (now - hang.lastSeen > SAME_HANG_NANOS) {
            Fall fall = FALLS.get(uuid);
            float speed = fall == null ? 0f : fall.speed;
            hang.start = now;
            // Half a block a tick is a hard catch; a hang started standing still still gives a little.
            float strength = Math.max(0.3f, Math.min(1f, Math.abs(speed) * 2f));
            hang.strength = speed > 0f ? -strength : strength;
            hang.torsoLift = 0f;
            hang.raise = 0f;
            hang.usualRaise = 0f;
        }
        float dt = Math.min(0.1f, (now - hang.lastSeen) / 1e9f);
        hang.lastSeen = now;

        // ParCool lifts the torso for the hang and lowers it again, briefly, while blending its
        // look-around poses: that would bob the whole model, so it is taken back out.
        float torsoLift = torsoLift(player);
        hang.torsoLift = Math.max(hang.torsoLift, torsoLift);
        float offset = catchOffset(hang, now) - (hang.torsoLift - torsoLift) * PIXELS_PER_BLOCK;

        float held = Math.max(hang.raise * (float) Math.exp(-dt / RAISE_SECONDS), hang.usualRaise);
        Arms arms = solve(player, wall, offset, held, false);
        float needed = arms.shortfall();
        if ((now - hang.start) / 1e9f > CATCH_SECONDS && needed > held) {
            float eased = held + (needed - held) * (1f - (float) Math.exp(-dt / RAISE_UP_SECONDS));
            arms = solve(player, wall, offset, eased, true);
        }
        float tau = needed > hang.usualRaise ? USUAL_RAISE_UP_SECONDS : USUAL_RAISE_DOWN_SECONDS;
        hang.usualRaise += (needed - hang.usualRaise) * (1f - (float) Math.exp(-dt / tau));
        hang.raise = offset - arms.catchOffset();
        SOLVED.put(uuid, arms);
        SOLVED_AT.put(uuid, now);
        return arms;
    }

    /**
     * The angles a hanging arm is drawn at, all blending done: x and y rotation in radians, how many
     * pixels to raise the shoulder, and how much the hand holds on (0 hanging free, 1 on the ledge).
     */
    public record Hold(float x, float y, float lift, float grip) {
        static final Hold NONE = new Hold(0, 0, 0, 0);
    }

    public record Holds(Hold right, Hold left) {
        public static final Holds NONE = new Holds(Hold.NONE, Hold.NONE);
    }

    /** How fast a hand lets go or takes hold, and the IK gives way to the fallback: time constant. */
    private static final float BLEND_SECONDS = 0.08f;

    /** Per player and arm: the eased weights, and the last y rotation to keep new ones next to. */
    private static final class ArmBlend {
        float grip = Float.NaN;
        float ik = Float.NaN;
        float yRot = Float.NaN;
    }

    private static final class HoldState {
        final ArmBlend right = new ArmBlend();
        final ArmBlend left = new ArmBlend();
        long lastNanos;
        long hangStart;
        Holds holds = Holds.NONE;
    }

    private static final Map<UUID, HoldState> HOLDS = new HashMap<>();

    /**
     * The arms of a hanging player as the pack draws them. ParCool lets a hand go when the player
     * looks away along the wall, and the IK can lose a ledge top; blending those as Euler angles
     * swings an arm sideways on its way, and flips it round when its y rotation wraps past half a
     * turn behind the back. So each arm's direction is blended instead - free-hanging, the fixed
     * fallback grip and the IK grip - over eased weights, and turned into angles once, with the y
     * rotation kept next to the last one.
     *
     * @param rightGrip how much ParCool holds on with the right hand right now, 0 to 1
     */
    public static Holds holds(AbstractClientPlayer player, @Nullable Vec3 wall, float rightGrip, float leftGrip) {
        Arms arms = arms(player, wall);
        UUID uuid = player.getUUID();
        HoldState state = HOLDS.computeIfAbsent(uuid, k -> new HoldState());
        long now = System.nanoTime();
        if (now - state.lastNanos < SOLVE_EVERY_NANOS) return state.holds;
        Hang hang = HANGS.get(uuid);
        boolean fresh = hang == null || hang.start != state.hangStart;
        float dt = fresh ? 0f : Math.min(0.1f, (now - state.lastNanos) / 1e9f);
        state.lastNanos = now;
        if (hang != null) state.hangStart = hang.start;

        state.holds = new Holds(
                hold(state.right, fresh, dt, RIGHT_SHOULDER, arms.rightValid(), arms.rightX(), arms.rightY(),
                        arms.rightLift(), rightGrip, -108f, 8f),
                hold(state.left, fresh, dt, LEFT_SHOULDER, arms.leftValid(), arms.leftX(), arms.leftY(),
                        arms.leftLift(), leftGrip, -114f, -8f));
        return state.holds;
    }

    private static Hold hold(ArmBlend blend, boolean fresh, float dt, Vector3f shoulder, boolean valid,
                             float ikX, float ikY, float ikLift, float gripTarget,
                             float fallbackXDeg, float fallbackYDeg) {
        float ikTarget = valid ? 1f : 0f;
        if (fresh || Float.isNaN(blend.grip)) {
            // A new hang takes hold at once: the hands reach the ledge first.
            blend.grip = gripTarget;
            blend.ik = ikTarget;
            blend.yRot = Float.NaN;
        } else {
            float k = 1f - (float) Math.exp(-dt / BLEND_SECONDS);
            blend.grip += (gripTarget - blend.grip) * k;
            blend.ik += (ikTarget - blend.ik) * k;
        }

        Vector3f onLedge = slerp(direction((float) Math.toRadians(fallbackXDeg), (float) Math.toRadians(fallbackYDeg)),
                direction(ikX, ikY), valid ? blend.ik : 0f);
        // Hanging loose: down, a little out to the side and forward.
        Vector3f loose = new Vector3f(Math.signum(shoulder.x) * 0.16f, 0.98f, -0.1f).normalize();
        Vector3f arm = slerp(loose, onLedge, blend.grip);

        float xRot = -(float) Math.acos(Math.max(-1f, Math.min(1f, arm.y)));
        float yRot;
        if (arm.x * arm.x + arm.z * arm.z < 1e-6f) {
            yRot = Float.isNaN(blend.yRot) ? 0f : blend.yRot;
        } else {
            yRot = (float) Math.atan2(-arm.x, -arm.z);
            if (!Float.isNaN(blend.yRot)) {
                while (yRot - blend.yRot > Math.PI) yRot -= (float) (2 * Math.PI);
                while (yRot - blend.yRot < -Math.PI) yRot += (float) (2 * Math.PI);
            }
        }
        blend.yRot = yRot;
        float lift = valid ? ikLift * blend.ik * blend.grip : 0f;
        return new Hold(xRot, yRot, lift, blend.grip);
    }

    /** Where an arm at these rotations points, in model space; see {@link #aim}. */
    private static Vector3f direction(float xRot, float yRot) {
        float sin = (float) Math.sin(xRot);
        return new Vector3f(sin * (float) Math.sin(yRot), (float) Math.cos(xRot), sin * (float) Math.cos(yRot));
    }

    private static Vector3f slerp(Vector3f from, Vector3f to, float t) {
        if (t <= 0f) return new Vector3f(from);
        if (t >= 1f) return new Vector3f(to);
        float dot = Math.max(-1f, Math.min(1f, from.dot(to)));
        if (dot > 0.9995f) return new Vector3f(from).lerp(to, t).normalize();
        double angle = Math.acos(dot);
        double sin = Math.sin(angle);
        float a = (float) (Math.sin((1 - t) * angle) / sin);
        float b = (float) (Math.sin(t * angle) / sin);
        return new Vector3f(from).mul(a).add(new Vector3f(to).mul(b)).normalize();
    }

    /** How high ParCool lifts the torso on the pose stack right now, in blocks, at its weight. */
    private static float torsoLift(AbstractClientPlayer player) {
        PlayerAnimator animator = PlayerAnimator.get(player);
        BlendingModelTransform body = ((ParCool4PackTransforms) (Object) ((ParCool4AnimatorAccessor) animator)
                .emfcompat$processor()).emfcompat$body(animator.getCurrentTransformation());
        if (body == null) return 0f;
        Transform torso = body.transformation().transforms().get(AnimatableModelPart.BODY);
        if (torso == null) return 0f;
        return torso.translation().y() * (body.isOverwriting() ? 1f : body.blendFactor());
    }

    /**
     * @param heldRaise a raise, in pixels, still being eased out: the body is raised at least this
     *                  much, so it does not drop the moment the arms stop needing it
     * @param exactly   raise the body by heldRaise only, even if the arms need more
     */
    private static Arms solve(AbstractClientPlayer player, @Nullable Vec3 wall, float catchOffset, float heldRaise,
                              boolean exactly) {
        Frame frame = FRAMES.get(player.getUUID());
        if (frame == null || wall == null) return new Arms(false, 0, 0, 0, 0, false, 0, 0, 0, 0, catchOffset, 0);
        Vec3 horizontal = new Vec3(wall.x, 0, wall.z);
        if (horizontal.lengthSqr() < 1e-4) return new Arms(false, 0, 0, 0, 0, false, 0, 0, 0, 0, catchOffset, 0);
        horizontal = horizontal.normalize();

        Matrix4f toWorld = frame.model();
        Matrix4f toModel = new Matrix4f(toWorld).invert();
        // The shoulders as the pack will draw them, moved with the body for the catch.
        float[] right = aim(player, frame, toModel, shoulder(RIGHT_SHOULDER, catchOffset), horizontal);
        float[] left = aim(player, frame, toModel, shoulder(LEFT_SHOULDER, catchOffset), horizontal);
        // ParCool carries the player past the hang and pulls it back up, further than an arm and a
        // raised shoulder reach: then the body goes up by the rest, so the hands stay on the ledge.
        float shortfall = Math.max(right == null ? 0f : right[4], left == null ? 0f : left[4]);
        float raise = exactly ? heldRaise : Math.max(shortfall, heldRaise);
        if (raise > 0f) {
            catchOffset -= raise;
            right = aim(player, frame, toModel, shoulder(RIGHT_SHOULDER, catchOffset), horizontal);
            left = aim(player, frame, toModel, shoulder(LEFT_SHOULDER, catchOffset), horizontal);
        }
        if (right == null) right = new float[5];
        if (left == null) left = new float[5];
        return new Arms(right[2] > 0, right[0], right[1], right[2], right[3],
                left[2] > 0, left[0], left[1], left[2], left[3], catchOffset, shortfall);
    }

    private static Vector3f shoulder(Vector3f pivot, float offset) {
        return new Vector3f(pivot).add(0, offset, 0);
    }

    /**
     * x rotation, y rotation, reach (distance over arm length, before any lift), lift (how far the
     * shoulder goes up, in pixels) and how many pixels the hand still falls short by, for one arm,
     * or null.
     */
    @Nullable
    private static float[] aim(AbstractClientPlayer player, Frame frame, Matrix4f toModel,
                               Vector3f shoulder, Vec3 wall) {
        Vector3f shoulderBlocks = frame.model().transformPosition(new Vector3f(shoulder).div(16f));
        Vec3 shoulderWorld = new Vec3(shoulderBlocks.x, shoulderBlocks.y, shoulderBlocks.z).add(frame.camera());

        // The wall face ahead of the shoulder; from a little lower too, for a shoulder above the edge.
        BlockHitResult face = clip(player, shoulderWorld, shoulderWorld.add(wall.scale(1.2)));
        if (face.getType() == HitResult.Type.MISS) {
            Vec3 lower = shoulderWorld.add(0, -0.4, 0);
            face = clip(player, lower, lower.add(wall.scale(1.2)));
        }
        double depth = face.getLocation().subtract(shoulderWorld).dot(wall);

        // Down onto the top of that block.
        Vec3 above = shoulderWorld.add(wall.scale(depth + ONTO_TOP)).add(0, 1.0, 0);
        BlockHitResult top = clip(player, above, above.add(0, -1.8, 0));
        Vec3 target = top.getLocation().add(0, HAND_CLEARANCE, 0).subtract(frame.camera());

        Vector3f inModel = toModel.transformPosition(new Vector3f((float) target.x, (float) target.y, (float) target.z))
                .mul(16f).sub(shoulder);
        float distance = inModel.length();
        if (distance < 1e-3f) return null;
        // Behind the shoulder (the model faces -z): slide the hand outward along the ledge, which with
        // the back to the wall runs along model x.
        float flat = (float) Math.sqrt(inModel.x * inModel.x + inModel.z * inModel.z);
        float behind = flat < 1e-3f ? 0f : Math.max(0f, inModel.z / flat);
        if (behind > 0f) {
            inModel.x += Math.signum(shoulder.x) * BACK_SPREAD * behind;
            distance = inModel.length();
        }
        float reach = distance / ARM_REACH;

        // Too far: raise the shoulder (model y is down) until the hand just gets there, a little at
        // most - the rest of the gap is the pack's to close, or to leave.
        float lift = 0f;
        float shortBy = 0f;
        float horizontal = (float) Math.sqrt(inModel.x * inModel.x + inModel.z * inModel.z);
        if (distance > ARM_REACH && inModel.y < 0f && horizontal < ARM_REACH) {
            float vertical = (float) Math.sqrt(ARM_REACH * ARM_REACH - horizontal * horizontal);
            float needed = -inModel.y - vertical;
            lift = Math.min(MAX_LIFT, needed);
            shortBy = needed - lift;
            inModel.y += lift;
            distance = inModel.length();
        }
        inModel.div(distance);

        // An arm at rest points along +y; rotationZYX with no z turns that into
        // (sin x sin y, cos x, sin x cos y). The raised solution has sin x < 0.
        float xRot = -(float) Math.acos(Math.max(-1f, Math.min(1f, inModel.y)));
        float yRot = (float) Math.atan2(-inModel.x, -inModel.z);
        return new float[]{xRot, yRot, reach, lift, shortBy};
    }

    private static BlockHitResult clip(AbstractClientPlayer player, Vec3 from, Vec3 to) {
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }
}

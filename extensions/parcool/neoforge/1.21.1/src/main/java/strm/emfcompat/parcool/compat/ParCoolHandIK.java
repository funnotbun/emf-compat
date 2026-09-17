package strm.emfcompat.parcool.compat;

import com.alrex.parcool.client.animation.system.AnimatableModelPart;
import com.alrex.parcool.client.animation.system.BlendingModelTransform;
import com.alrex.parcool.client.animation.system.PlayerAnimator;
import com.alrex.parcool.client.animation.system.data.Transform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
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
    /** How long a hang takes to settle at the lift ParCool gives it. */
    private static final float SETTLE_SECONDS = 0.3f;
    /** Model pixels in a block, for the player's 0.9375 model scale. */
    private static final float PIXELS_PER_BLOCK = 16f / 0.9375f;

    /** The fastest vertical movement of the last moments: blocks per tick, up positive, and when. */
    private static final class Fall {
        float speed;
        long at;
    }

    private static final Map<UUID, Hang> HANGS = new HashMap<>();

    /**
     * Climbing up, the arms lead: the shoulders draw up to the ledge a little before ParCool lifts the
     * player, and then the body trails its rise by a moment, as if pulled up by the arms.
     */
    private static final float CLIMB_PULL_PIXELS = 1.5f;
    private static final float CLIMB_LAG_SECONDS = 0.035f;
    private static final float CLIMB_LAG_MAX_PIXELS = 3f;

    /** Each player's climb progress this frame, set by the variable that reads it; render thread only. */
    private static final Map<UUID, Float> CLIMB = new HashMap<>();
    /** Each player's height, trailed: where the body is drawn while climbing. */
    private static final Map<UUID, double[]> TRAILED_Y = new HashMap<>();

    public static void climbProgress(UUID uuid, float progress) {
        CLIMB.put(uuid, progress);
    }

    private static float climbOffset(UUID uuid) {
        float p = CLIMB.getOrDefault(uuid, 0f);
        float pull = -CLIMB_PULL_PIXELS * (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, p / 0.3f)));
        double[] trailed = TRAILED_Y.get(uuid);
        float lag = trailed == null ? 0f : (float) ((trailed[1] - trailed[0]) * PIXELS_PER_BLOCK);
        // A soft cap, so a fast lift reads as a lag and not as the body parked lower
        return pull + CLIMB_LAG_MAX_PIXELS * (float) Math.tanh(Math.max(0f, lag) / CLIMB_LAG_MAX_PIXELS);
    }
    /** Where each hand holds the ledge, in the world, by player and side; kept for climbing up. */
    private static final Map<String, Vec3> GRIPS = new HashMap<>();

    /**
     * Hand over hand along the ledge. A hand stays where it grips while the body moves under it, and
     * once the shoulder has left it this far behind it reaches over to a new grip a little ahead,
     * one hand at a time - like feet stepping, so a shuffle reads as the hands doing it.
     */
    private static final double STEP_TRIGGER = 0.34;
    /** How far past the spot under the shoulder the reaching hand lands, in the direction of travel. */
    private static final double STEP_LEAD = 0.22;
    /** How high the hand lifts off the ledge mid-reach, in blocks. */
    private static final double STEP_LIFT = 0.09;
    private static final float STEP_SECONDS = 0.34f;
    /** A ledge top this much higher or lower is another ledge: the hand goes straight there. */
    private static final double SAME_LEDGE = 0.25;
    /** How far from the shoulder a grip can be kept when the ledge under the shoulder runs out. */
    private static final double HOLD_REACH = 1.0;
    /** Directions a ledge is looked for from the shoulder, in degrees off the wall ParCool holds. */
    private static final double[] SEARCH_TURNS = {0, 45, -45, 90, -90};

    private static final class Step {
        Vec3 planted;
        Vec3 from;
        Vec3 to;
        long start = -1;
        /** Last frame's reach progress, 0 while planted; handed to the pack. */
        float phase;
    }

    private static final Map<String, Step> STEPS = new HashMap<>();

    /**
     * Where the hand is this frame: planted, or on its way to a new grip. Works from the clock, so
     * solving the same frame twice gives the same answer.
     */
    /** How a limb steps: when it goes, how far ahead, how high and how far off the wall, how long. */
    private record Gait(double trigger, double lead, double lift, double away, float seconds) {
    }

    private static final Gait HAND_GAIT = new Gait(STEP_TRIGGER, STEP_LEAD, STEP_LIFT, 0.04, STEP_SECONDS);
    /**
     * Feet take long, slow strides - a foot stays put until the hip has left it well behind, then
     * swings well past it - and come off the wall on the way: with no knees, pulling the foot out is
     * what reads as the leg bending to take a step rather than the leg turning in place.
     */
    private static final Gait FOOT_GAIT = new Gait(0.3, 0.24, 0.08, 0.16, 0.7f);

    private static Vec3 stepped(String key, String otherKey, Vec3 desired, Vec3 wall, long now) {
        return stepped(key, otherKey, desired, wall, now, HAND_GAIT);
    }

    private static Vec3 stepped(String key, String otherKey, Vec3 desired, Vec3 wall, long now, Gait gait) {
        Step step = STEPS.computeIfAbsent(key, k -> new Step());
        if (step.planted == null || Math.abs(step.planted.y - desired.y) > SAME_LEDGE) {
            step.planted = desired;
            step.start = -1;
        }
        if (step.start >= 0) {
            float t = (now - step.start) / 1e9f / gait.seconds();
            if (t >= 1f) {
                step.planted = step.to;
                step.start = -1;
                step.phase = 0f;
            } else {
                // The reach follows the body: aim ahead of where the shoulder is now.
                Vec3 along = desired.subtract(step.from).multiply(1, 0, 1);
                if (along.lengthSqr() > 1e-6) step.to = desired.add(along.normalize().scale(gait.lead()));
                float ease = t * t * t * (t * (6f * t - 15f) + 10f);
                float arc = (float) Math.sin(Math.PI * t);
                step.phase = arc;
                return step.from.lerp(step.to, ease).add(0, gait.lift() * arc, 0).subtract(wall.scale(gait.away() * arc));
            }
        }
        Vec3 behind = desired.subtract(step.planted).multiply(1, 0, 1);
        Step other = STEPS.get(otherKey);
        boolean otherReaching = other != null && other.start >= 0
                && (now - other.start) / 1e9f < gait.seconds() * 0.6f;
        if (behind.length() > gait.trigger() && !otherReaching) {
            step.from = step.planted;
            step.to = desired.add(behind.normalize().scale(gait.lead()));
            step.start = now;
        }
        return step.planted;
    }

    /** How far through a reach each hand is, 0 planted to 1 mid-reach and back; for the pack. */
    public static float stepPhase(UUID uuid, boolean right) {
        Step step = STEPS.get(uuid.toString() + (right ? "R" : "L"));
        return step == null ? 0f : step.phase;
    }

    private static boolean reaching(String key) {
        Step step = STEPS.get(key);
        return step != null && step.start >= 0;
    }

    private static void resetSteps(UUID uuid) {
        STEPS.remove(uuid.toString() + "R");
        STEPS.remove(uuid.toString() + "L");
        STEPS.remove(uuid.toString() + "RF");
        STEPS.remove(uuid.toString() + "LF");
    }
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
        // [trailed y, current y, last update]
        double y = net.minecraft.util.Mth.lerp(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false),
                player.yo, player.getY());
        double[] trailed = TRAILED_Y.computeIfAbsent(player.getUUID(), k -> new double[]{y, y, now});
        double dt = Math.min(0.1, (now - trailed[2]) / 1e9);
        trailed[0] += (y - trailed[0]) * (1 - Math.exp(-dt / CLIMB_LAG_SECONDS));
        if (y < trailed[0]) trailed[0] = y;
        trailed[1] = y;
        trailed[2] = now;
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
        return arms(player, wall, false);
    }

    /**
     * @param climbing climbing up off the ledge: the hands keep to where they held it, and there is no
     *                 catch to spring or short arm to raise the body for
     */
    public static Arms arms(AbstractClientPlayer player, @Nullable Vec3 wall, boolean climbing) {
        UUID uuid = player.getUUID();
        // Every variable of one model evaluation reads within well under a millisecond.
        long now = System.nanoTime();
        Long solvedAt = SOLVED_AT.get(uuid);
        if (solvedAt != null && now - solvedAt < SOLVE_EVERY_NANOS) return SOLVED.get(uuid);
        if (climbing) {
            Arms arms = solve(player, wall, climbOffset(uuid), 0f, true, true);
            SOLVED.put(uuid, arms);
            SOLVED_AT.put(uuid, now);
            return arms;
        }

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
            resetSteps(uuid);
        }
        float dt = Math.min(0.1f, (now - hang.lastSeen) / 1e9f);
        hang.lastSeen = now;

        // ParCool lifts the torso for the hang and lowers it again, briefly, while blending its
        // look-around poses: that would bob the whole model, so it is taken back out.
        // Only dips below the lift the hang settled at count: shuffling, ParCool bobs the torso above it.
        float torsoLift = torsoLift(player);
        if ((now - hang.start) / 1e9f < SETTLE_SECONDS) hang.torsoLift = Math.max(hang.torsoLift, torsoLift);
        float offset = catchOffset(hang, now) - Math.max(0f, hang.torsoLift - torsoLift) * PIXELS_PER_BLOCK;

        float held = Math.max(hang.raise * (float) Math.exp(-dt / RAISE_SECONDS), hang.usualRaise);
        Arms arms = solve(player, wall, offset, held, false, false);
        float needed = arms.shortfall();
        if ((now - hang.start) / 1e9f > CATCH_SECONDS && needed > held) {
            float eased = held + (needed - held) * (1f - (float) Math.exp(-dt / RAISE_UP_SECONDS));
            arms = solve(player, wall, offset, eased, true, false);
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
        return holds(player, wall, rightGrip, leftGrip, false);
    }

    public static Holds holds(AbstractClientPlayer player, @Nullable Vec3 wall, float rightGrip, float leftGrip,
                              boolean climbing) {
        Arms arms = arms(player, wall, climbing);
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
                        arms.rightLift(), rightGrip, -108f, 8f, climbing),
                hold(state.left, fresh, dt, LEFT_SHOULDER, arms.leftValid(), arms.leftX(), arms.leftY(),
                        arms.leftLift(), leftGrip, -114f, -8f, climbing));
        return state.holds;
    }

    private static Hold hold(ArmBlend blend, boolean fresh, float dt, Vector3f shoulder, boolean valid,
                             float ikX, float ikY, float ikLift, float gripTarget,
                             float fallbackXDeg, float fallbackYDeg, boolean climbing) {
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
        // Let go on the hang: the body turns along the wall with the look, so an arm opened only to
        // the side would point at the wall or straight out of it, hidden behind the body from where
        // it is seen. It opens out and forward, the way the player looks. Let go on a climb: down, a
        // little out and forward.
        Vector3f loose = climbing
                ? new Vector3f(Math.signum(shoulder.x) * 0.36f, 0.92f, -0.12f).normalize()
                : new Vector3f(Math.signum(shoulder.x) * 0.5f, 0.62f, -0.6f).normalize();
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
                              boolean exactly, boolean remembered) {
        Frame frame = FRAMES.get(player.getUUID());
        if (frame == null || wall == null) return new Arms(false, 0, 0, 0, 0, false, 0, 0, 0, 0, catchOffset, 0);
        Vec3 horizontal = new Vec3(wall.x, 0, wall.z);
        if (horizontal.lengthSqr() < 1e-4) return new Arms(false, 0, 0, 0, 0, false, 0, 0, 0, 0, catchOffset, 0);
        horizontal = horizontal.normalize();

        Matrix4f toWorld = frame.model();
        Matrix4f toModel = new Matrix4f(toWorld).invert();
        // The shoulders as the pack will draw them, moved with the body for the catch.
        float[] right = aim(player, frame, toModel, shoulder(RIGHT_SHOULDER, catchOffset), horizontal, remembered);
        float[] left = aim(player, frame, toModel, shoulder(LEFT_SHOULDER, catchOffset), horizontal, remembered);
        // ParCool carries the player past the hang and pulls it back up, further than an arm and a
        // raised shoulder reach: then the body goes up by the rest, so the hands stay on the ledge.
        // Only a holding hand pulls the body up: a reaching one aims ahead, past where it can get to.
        String id = player.getUUID().toString();
        float shortfall = Math.max(right == null || reaching(id + "R") ? 0f : right[4],
                left == null || reaching(id + "L") ? 0f : left[4]);
        float raise = exactly ? heldRaise : Math.max(shortfall, heldRaise);
        if (raise > 0f) {
            catchOffset -= raise;
            right = aim(player, frame, toModel, shoulder(RIGHT_SHOULDER, catchOffset), horizontal, remembered);
            left = aim(player, frame, toModel, shoulder(LEFT_SHOULDER, catchOffset), horizontal, remembered);
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
                               Vector3f shoulder, Vec3 wall, boolean remembered) {
        String key = player.getUUID().toString() + (shoulder.x < 0 ? "R" : "L");
        Vec3 grip;
        if (remembered) {
            grip = GRIPS.get(key);
            if (grip == null) return null;
        } else {
            Vector3f shoulderBlocks = frame.model().transformPosition(new Vector3f(shoulder).div(16f));
            Vec3 shoulderWorld = new Vec3(shoulderBlocks.x, shoulderBlocks.y, shoulderBlocks.z).add(frame.camera());

            // The wall face ahead of the shoulder; from a little lower too, for a shoulder above the edge.
            // Rounding a corner the shoulder runs past the end of that face, so the faces to either
            // side are tried after it: the hand reaches round onto the next one.
            BlockHitResult top = null;
            for (double turn : SEARCH_TURNS) {
                Vec3 dir = wall.yRot((float) Math.toRadians(turn));
                BlockHitResult face = clip(player, shoulderWorld, shoulderWorld.add(dir.scale(1.2)));
                if (face.getType() == HitResult.Type.MISS) {
                    Vec3 lower = shoulderWorld.add(0, -0.4, 0);
                    face = clip(player, lower, lower.add(dir.scale(1.2)));
                }
                if (face.getType() == HitResult.Type.MISS) continue;
                double depth = face.getLocation().subtract(shoulderWorld).dot(dir);
                // Down onto the top of that block.
                Vec3 above = shoulderWorld.add(dir.scale(depth + ONTO_TOP)).add(0, 1.0, 0);
                BlockHitResult found = clip(player, above, above.add(0, -1.8, 0));
                if (found.getType() != HitResult.Type.MISS && found.getDirection() == Direction.UP) {
                    top = found;
                    break;
                }
            }
            if (top == null || top.getType() == HitResult.Type.MISS || top.getDirection() != Direction.UP) {
                // No ledge near this shoulder at all: the hand keeps its grip while it can still reach it,
                // rather than letting go.
                Step held = STEPS.get(key);
                grip = held == null ? null : held.start >= 0 ? held.to : held.planted;
                if (grip == null || grip.distanceTo(shoulderWorld) > HOLD_REACH) return null;
            } else {
                String otherKey = player.getUUID().toString() + (shoulder.x < 0 ? "L" : "R");
                grip = stepped(key, otherKey, top.getLocation().add(0, HAND_CLEARANCE, 0), wall, System.nanoTime());
            }
            GRIPS.put(key, grip);
        }
        return pointArm(frame, toModel, shoulder, grip);
    }

    /** {@link #aim}'s angles for an arm reaching from this shoulder to a hand position in the world. */
    @Nullable
    private static float[] pointArm(Frame frame, Matrix4f toModel, Vector3f shoulder, Vec3 grip) {
        Vec3 target = grip.subtract(frame.camera());

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
        if (distance > ARM_REACH && inModel.y < 0f) {
            // Past an arm's length sideways the shoulder can only go all the way up: no cut-off, which
            // would drop the lift from full to none as a reaching hand crossed it.
            float vertical = (float) Math.sqrt(Math.max(0f, ARM_REACH * ARM_REACH - horizontal * horizontal));
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

    /** A player hanging under a bar: each arm's angles and shoulder lift, as the pack draws them. */
    public record BarArms(boolean valid, float rightX, float rightY, float rightLift,
                          float leftX, float leftY, float leftLift) {
        public static final BarArms NONE = new BarArms(false, 0, 0, 0, 0, 0, 0);
    }

    private static final Map<UUID, BarArms> BARS = new HashMap<>();
    private static final Map<UUID, Long> BARS_AT = new HashMap<>();
    /**
     * Hands along a bar, as on monkey bars: each stays where it holds while the body moves along
     * under it, then lets go, swings down and round and takes the bar again well ahead.
     */
    private static final Gait BAR_GAIT = new Gait(0.45, 0.4, 0.0, 0.6, 0.42f);
    /** How far apart the hands hold a bar that runs front to back through the body, blocks. */
    private static final double BAR_HANDS_APART = 0.6;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /**
     * The arms of a player hanging under a bar (ParCool's hang down): each hand on the bar above
     * its shoulder. Across the chest the shoulders set how far apart they are; along the body one
     * hand holds ahead of the other.
     *
     * @param barPos the bar's block
     * @param axis   the way the bar runs, a unit vector
     */
    public static BarArms barArms(AbstractClientPlayer player, BlockPos barPos, Vec3 axis) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        Long at = BARS_AT.get(uuid);
        if (at != null && now - at < SOLVE_EVERY_NANOS) return BARS.get(uuid);
        BarArms arms = BarArms.NONE;
        Frame frame = FRAMES.get(uuid);
        if (frame != null) {
            Vec3 center = barCenter(player, barPos);
            Matrix4f toModel = new Matrix4f(frame.model()).invert();
            Vector3f sideways = frame.model().transformDirection(new Vector3f(1, 0, 0)).normalize();
            Vector3f forward = frame.model().transformDirection(new Vector3f(0, 0, -1)).normalize();
            double across = Math.abs(sideways.x * axis.x + sideways.z * axis.z);
            double ahead = Math.signum(forward.x * axis.x + forward.z * axis.z);
            float[] right = barHand(frame, toModel, RIGHT_SHOULDER, center, axis, ahead * (1 - across), uuid + "R", uuid + "L", now);
            float[] left = barHand(frame, toModel, LEFT_SHOULDER, center, axis, -ahead * (1 - across), uuid + "L", uuid + "R", now);
            if (right != null && left != null) {
                arms = new BarArms(true, right[0], right[1], right[3], left[0], left[1], left[3]);
            }
        }
        BARS.put(uuid, arms);
        BARS_AT.put(uuid, now);
        return arms;
    }

    @Nullable
    private static float[] barHand(Frame frame, Matrix4f toModel, Vector3f shoulder, Vec3 center, Vec3 axis,
                                   double ahead, String key, String otherKey, long now) {
        Vector3f shoulderBlocks = frame.model().transformPosition(new Vector3f(shoulder).div(16f));
        Vec3 shoulderWorld = new Vec3(shoulderBlocks.x, shoulderBlocks.y, shoulderBlocks.z).add(frame.camera());
        double along = shoulderWorld.subtract(center).dot(axis) + ahead * BAR_HANDS_APART / 2;
        Vec3 onBar = center.add(axis.scale(along));
        return pointArm(frame, toModel, shoulder, stepped(key, otherKey, onBar, UP, now, BAR_GAIT));
    }

    /** The middle of a bar block's collision box: a fence rail, a chain, an end rod. */
    private static Vec3 barCenter(AbstractClientPlayer player, BlockPos pos) {
        VoxelShape shape = player.level().getBlockState(pos).getCollisionShape(player.level(), pos);
        if (shape.isEmpty()) return Vec3.atCenterOf(pos);
        AABB box = shape.bounds();
        return new Vec3(pos.getX() + (box.minX + box.maxX) / 2, pos.getY() + (box.minY + box.maxY) / 2,
                pos.getZ() + (box.minZ + box.maxZ) / 2);
    }

    /** Model space hip pivots, pixels. */
    private static final Vector3f RIGHT_HIP = new Vector3f(-1.9f, 12f, 0f);
    private static final Vector3f LEFT_HIP = new Vector3f(1.9f, 12f, 0f);
    /** How far below the hip a foot is set against the wall, and how far off it the wall may be; blocks. */
    private static final double FOOT_DROP = 0.62;
    private static final double FOOT_WALL_REACH = 0.9;
    /** Toes on the face, not in it. */
    private static final double FOOT_CLEARANCE = 0.03;
    /** How far a leg may lean out from the hip, and in under the body; radians. */
    private static final float LEG_OUT = (float) Math.toRadians(40);
    private static final float LEG_IN = (float) Math.toRadians(24);

    /** The legs of a hanging player braced on the wall: swing and lean that set each foot on the face. */
    public record Legs(boolean rightValid, float rightX, float rightZ, boolean leftValid, float leftX, float leftZ) {
        public static final Legs NONE = new Legs(false, 0, 0, false, 0, 0);
    }

    private static final Map<UUID, Legs> LEGS = new HashMap<>();
    private static final Map<UUID, Long> LEGS_AT = new HashMap<>();

    /**
     * Feet against the wall below the ledge, where there is wall to stand on: like the hands, each
     * foot stays where it is set while the body moves along and steps over when left behind. Worked
     * out from the hips as the pack draws them, moved with the body for the catch.
     */
    public static Legs legs(AbstractClientPlayer player, @Nullable Vec3 wall) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        Long at = LEGS_AT.get(uuid);
        if (at != null && now - at < SOLVE_EVERY_NANOS) return LEGS.get(uuid);
        Legs legs = Legs.NONE;
        Frame frame = FRAMES.get(uuid);
        if (frame != null && wall != null && new Vec3(wall.x, 0, wall.z).lengthSqr() > 1e-4) {
            Vec3 horizontal = new Vec3(wall.x, 0, wall.z).normalize();
            float offset = arms(player, wall).catchOffset();
            Matrix4f toModel = new Matrix4f(frame.model()).invert();
            float[] right = foot(player, frame, toModel, shoulder(RIGHT_HIP, offset), horizontal, uuid + "RF", uuid + "LF", now);
            float[] left = foot(player, frame, toModel, shoulder(LEFT_HIP, offset), horizontal, uuid + "LF", uuid + "RF", now);
            if (right != null && left != null) {
                // Both feet trail the same way as the body moves along, so one leg leans in under it;
                // the legs may close up to parallel but not cross. Outward is +z on the right, -z on
                // the left.
                float spread = right[1] - left[1];
                if (spread < 0f) {
                    right[1] -= spread / 2f;
                    left[1] += spread / 2f;
                }
            }
            legs = new Legs(right != null, right == null ? 0 : right[0], right == null ? 0 : right[1],
                    left != null, left == null ? 0 : left[0], left == null ? 0 : left[1]);
        }
        LEGS.put(uuid, legs);
        LEGS_AT.put(uuid, now);
        return legs;
    }

    @Nullable
    private static float[] foot(AbstractClientPlayer player, Frame frame, Matrix4f toModel, Vector3f hip, Vec3 wall,
                                String key, String otherKey, long now) {
        Vector3f hipBlocks = frame.model().transformPosition(new Vector3f(hip).div(16f));
        Vec3 hipWorld = new Vec3(hipBlocks.x, hipBlocks.y, hipBlocks.z).add(frame.camera());
        Vec3 from = hipWorld.add(0, -FOOT_DROP, 0);
        BlockHitResult face = clip(player, from, from.add(wall.scale(FOOT_WALL_REACH)));
        if (face.getType() == HitResult.Type.MISS || face.getDirection().getAxis().isVertical()) {
            STEPS.remove(key);
            return null;
        }
        Vec3 set = stepped(key, otherKey, face.getLocation().subtract(wall.scale(FOOT_CLEARANCE)), wall, now, FOOT_GAIT);
        Vec3 target = set.subtract(frame.camera());
        Vector3f inModel = toModel.transformPosition(new Vector3f((float) target.x, (float) target.y, (float) target.z))
                .mul(16f).sub(hip);
        float distance = inModel.length();
        if (distance < 1e-3f) return null;
        inModel.div(distance);
        // A leg at rest points along +y. Solved as a swing forward (x) and a lean out (z) rather than
        // an arm's twist (y): a leg hanging almost straight down would turn right round on its own
        // axis for a foot a little to the side, where it should spread. rotationZYX with no y turns
        // +y into (-cos x sin z, cos x cos z, sin x).
        float xRot = (float) Math.asin(Math.max(-1f, Math.min(1f, inModel.z)));
        float zRot = (float) Math.atan2(-inModel.x, inModel.y);
        // Out as far as a stride goes, in less: both feet trail the same way as the body moves along.
        float out = Math.signum(hip.x) < 0 ? 1f : -1f;
        zRot = out * Math.max(-LEG_IN, Math.min(LEG_OUT, out * zRot));
        return new float[]{xRot, zRot};
    }

    private static BlockHitResult clip(AbstractClientPlayer player, Vec3 from, Vec3 to) {
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }
}

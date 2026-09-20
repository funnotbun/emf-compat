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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKMath;
import strm.emfcompat.core.ik.IKResult;
import strm.emfcompat.core.ik.OneBoneIK;
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

    /** How far from the shoulder a grip can be kept when the ledge under the shoulder runs out. */
    private static final double HOLD_REACH = 1.0;
    /** Directions a ledge is looked for from the shoulder, in degrees off the wall ParCool holds. */
    private static final double[] SEARCH_TURNS = {0, 45, -45, 90, -90};

    /** How far through a reach each hand is, 0 planted to 1 mid-reach and back; for the pack. */
    public static float stepPhase(UUID uuid, boolean right) {
        return ParCoolLimbGait.phase(uuid.toString() + (right ? "R" : "L"));
    }
    private static final Map<UUID, Fall> FALLS = new HashMap<>();

    private static final long SOLVE_EVERY_NANOS = 2_000_000L;

    /** Each player's model pose as last set up for drawing. Render thread only. */
    private static final Map<UUID, IKFrame> FRAMES = new HashMap<>();
    private static final Map<UUID, Arms> SOLVED = new HashMap<>();
    private static final Map<UUID, Long> SOLVED_AT = new HashMap<>();

    private ParCoolHandIK() {
    }

    /** Called with the pose stack as it stands right before the model is animated. */
    public static void modelPose(AbstractClientPlayer player, Matrix4f pose) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        FRAMES.put(player.getUUID(), IKFrame.capture(pose, camera));

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
            ParCoolLimbGait.reset(uuid);
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

        Vector3f onLedge = IKMath.slerp(
                IKMath.directionXY((float) Math.toRadians(fallbackXDeg), (float) Math.toRadians(fallbackYDeg)),
                IKMath.directionXY(ikX, ikY), valid ? blend.ik : 0f);
        // Let go on the hang: the body turns along the wall with the look, so an arm opened only to
        // the side would point at the wall or straight out of it, hidden behind the body from where
        // it is seen. It opens out and forward, the way the player looks. Let go on a climb: down, a
        // little out and forward.
        Vector3f loose = climbing
                ? new Vector3f(Math.signum(shoulder.x) * 0.36f, 0.92f, -0.12f).normalize()
                : new Vector3f(Math.signum(shoulder.x) * 0.5f, 0.62f, -0.6f).normalize();
        Vector3f arm = IKMath.slerp(loose, onLedge, blend.grip);

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

    /**
     * How far ParCool turned the torso about the vertical for this frame's drawing, degrees, at its
     * weight: hanging it faces the wall. Taken as it was put on the pose stack, not worked out again:
     * working it out again steps the torso's easing on a second time, and the head led it.
     */
    public static float torsoYaw(AbstractClientPlayer player) {
        return ((ParCool4PackTransforms) (Object) ((ParCool4AnimatorAccessor) PlayerAnimator.get(player))
                .emfcompat$processor()).emfcompat$shownTorsoYaw();
    }

    /** The largest turn of the head from the torso, and the largest tilt up or down, degrees. */
    private static final float NECK_YAW = 95f;
    private static final float NECK_PITCH = 70f;

    /**
     * The head's y and x rotations, degrees, that point it where the player looks, whatever ParCool
     * did to the torso this frame: the look is taken into model space through the pose the model is
     * drawn with, as the arms' aims are. Adding up angles instead - the look off the body less the
     * torso's turn - missed the model's mirroring and the torso's lean and roll, and the head stayed
     * on the wall or turned the wrong way.
     */
    public static float[] headAngles(AbstractClientPlayer player, float partial) {
        IKFrame frame = FRAMES.get(player.getUUID());
        if (frame == null) return new float[]{0f, 0f};
        Vec3 look = player.getViewVector(partial);
        Vector3f d = frame.worldToModel()
                .transformDirection(new Vector3f((float) look.x, (float) look.y, (float) look.z)).normalize();
        // Model space: forward is -z, down is +y. A head turned by y then x looks at
        // (-sin y cos x, sin x, -cos y cos x).
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, -d.z));
        float pitch = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, d.y))));
        return new float[]{Math.max(-NECK_YAW, Math.min(NECK_YAW, yaw)), Math.max(-NECK_PITCH, Math.min(NECK_PITCH, pitch))};
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
        IKFrame frame = FRAMES.get(player.getUUID());
        if (frame == null || wall == null) return new Arms(false, 0, 0, 0, 0, false, 0, 0, 0, 0, catchOffset, 0);
        Vec3 horizontal = new Vec3(wall.x, 0, wall.z);
        if (horizontal.lengthSqr() < 1e-4) return new Arms(false, 0, 0, 0, 0, false, 0, 0, 0, 0, catchOffset, 0);
        horizontal = horizontal.normalize();

        // The shoulders as the pack will draw them, moved with the body for the catch.
        IKResult right = aim(player, frame, shoulder(RIGHT_SHOULDER, catchOffset), horizontal, remembered);
        IKResult left = aim(player, frame, shoulder(LEFT_SHOULDER, catchOffset), horizontal, remembered);
        // ParCool carries the player past the hang and pulls it back up, further than an arm and a
        // raised shoulder reach: then the body goes up by the rest, so the hands stay on the ledge.
        // Only a holding hand pulls the body up: a reaching one aims ahead, past where it can get to.
        String id = player.getUUID().toString();
        float shortfall = Math.max(right == null || ParCoolLimbGait.reaching(id + "R") ? 0f : right.shortfall(),
                left == null || ParCoolLimbGait.reaching(id + "L") ? 0f : left.shortfall());
        float raise = exactly ? heldRaise : Math.max(shortfall, heldRaise);
        if (raise > 0f) {
            catchOffset -= raise;
            right = aim(player, frame, shoulder(RIGHT_SHOULDER, catchOffset), horizontal, remembered);
            left = aim(player, frame, shoulder(LEFT_SHOULDER, catchOffset), horizontal, remembered);
        }
        return new Arms(right != null, right == null ? 0 : right.x(), right == null ? 0 : right.y(),
                right == null ? 0 : right.reach(), right == null ? 0 : right.lift(),
                left != null, left == null ? 0 : left.x(), left == null ? 0 : left.y(),
                left == null ? 0 : left.reach(), left == null ? 0 : left.lift(), catchOffset, shortfall);
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
    private static IKResult aim(AbstractClientPlayer player, IKFrame frame,
                                Vector3f shoulder, Vec3 wall, boolean remembered) {
        String key = player.getUUID().toString() + (shoulder.x < 0 ? "R" : "L");
        Vec3 grip;
        if (remembered) {
            grip = GRIPS.get(key);
            if (grip == null) return null;
        } else {
            Vector3f shoulderBlocks = frame.modelToWorld().transformPosition(new Vector3f(shoulder).div(16f));
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
                grip = ParCoolLimbGait.heldGrip(key);
                if (grip == null || grip.distanceTo(shoulderWorld) > HOLD_REACH) return null;
            } else {
                String otherKey = player.getUUID().toString() + (shoulder.x < 0 ? "L" : "R");
                grip = ParCoolLimbGait.step(key, otherKey,
                        top.getLocation().add(0, HAND_CLEARANCE, 0), wall, System.nanoTime());
            }
            GRIPS.put(key, grip);
        }
        return OneBoneIK.solveXY(frame, shoulder, grip, ARM_REACH, MAX_LIFT, BACK_SPREAD);
    }

    /** A player hanging under a bar: each arm's angles and shoulder lift, as the pack draws them. */
    public record BarArms(boolean valid, float rightX, float rightY, float rightZ, float rightLift,
                          float leftX, float leftY, float leftZ, float leftLift, float raise) {
        public static final BarArms NONE = new BarArms(false, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** The arms of a player hanging under a bar (ParCool's hang down). */
    public static BarArms barArms(AbstractClientPlayer player, BlockPos barPos, Vec3 axis) {
        return ParCoolBarIK.solve(player, barPos, axis, FRAMES.get(player.getUUID()));
    }

    /** How far through a swing along a bar each hand is. */
    public static float barPhase(UUID uuid, boolean right) {
        return ParCoolBarIK.phase(uuid, right);
    }
    /** The legs of a hanging player braced on the wall: swing and lean that set each foot on the face. */
    public record Legs(boolean rightValid, float rightX, float rightZ, boolean leftValid, float leftX, float leftZ) {
        public static final Legs NONE = new Legs(false, 0, 0, false, 0, 0);
    }

    /**
     * Feet against the wall below the ledge, where there is wall to stand on: like the hands, each
     * foot stays where it is set while the body moves along and steps over when left behind. Worked
     * out from the hips as the pack draws them, moved with the body for the catch.
     */
    public static Legs legs(AbstractClientPlayer player, @Nullable Vec3 wall) {
        return ParCoolLegIK.solve(player, wall, FRAMES.get(player.getUUID()));
    }

    private static BlockHitResult clip(AbstractClientPlayer player, Vec3 from, Vec3 to) {
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }
}

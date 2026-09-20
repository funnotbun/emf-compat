package strm.emfcompat.parcool.compat;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKMath;
import strm.emfcompat.core.ik.IKResult;
import strm.emfcompat.core.ik.OneBoneIK;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Arms, grips and temporal state for ParCool's under-bar hang. */
final class ParCoolBarIK {
    private static final long SOLVE_EVERY_NANOS = 2_000_000L;
    private static final Vector3f RIGHT_SHOULDER = new Vector3f(-5f, 2f, 0f);
    private static final Vector3f LEFT_SHOULDER = new Vector3f(5f, 2f, 0f);
    private static final float BACK_SPREAD = 6f;
    private static final float BAR_MAX_RAISE = 6f;
    private static final double BAR_GRIP_ABOVE = 0.05;
    private static final double BAR_HANDS_WIDER = 0.2;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static final double RELEASE_AFTER = 0.45;
    private static final double SWING_OVER = 0.55;
    private static final double REGRIP_AHEAD = 0.4;
    private static final double BACK_TO_GRIP = 0.12;
    private static final float DROP_SECONDS = 0.25f;
    private static final float SWING_FOLLOW_SECONDS = 0.07f;
    private static final float ARM_FOLLOW_SECONDS = 0.1f;
    private static final float HANGING_X = -0.15f;

    private static final Map<UUID, ParCoolHandIK.BarArms> SOLVED = new HashMap<>();
    private static final Map<UUID, Long> SOLVED_AT = new HashMap<>();
    private static final Map<UUID, Float> BODY_RAISE = new HashMap<>();
    private static final Map<UUID, Long> BODY_RAISE_AT = new HashMap<>();
    private static final Map<String, Swing> SWINGS = new HashMap<>();
    private static final Map<String, ArmTrack> ARM_TRACKS = new HashMap<>();

    private ParCoolBarIK() {
    }

    static ParCoolHandIK.BarArms solve(AbstractClientPlayer player, BlockPos barPos, Vec3 axis,
                                       @Nullable IKFrame frame) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        Long at = SOLVED_AT.get(uuid);
        if (at != null && now - at < SOLVE_EVERY_NANOS) return SOLVED.get(uuid);

        ParCoolHandIK.BarArms arms = ParCoolHandIK.BarArms.NONE;
        if (frame != null) {
            Vec3 center = barCenter(player, barPos).add(0, BAR_GRIP_ABOVE, 0);
            float raised = BODY_RAISE.getOrDefault(uuid, 0f);
            Vector3f rightShoulder = shifted(RIGHT_SHOULDER, -raised);
            Vector3f leftShoulder = shifted(LEFT_SHOULDER, -raised);
            Vector3f sideways = frame.modelToWorld().transformDirection(new Vector3f(1, 0, 0)).normalize();
            double across = Math.abs(sideways.x * axis.x + sideways.z * axis.z);
            boolean brachiate = across < 0.5;
            String rightKey = uuid + "R";
            String leftKey = uuid + "L";
            float[] right = brachiate
                    ? brachiateHand(frame, rightShoulder, center, axis, rightKey, leftKey, now)
                    : barHand(frame, rightShoulder, center, axis, rightKey, leftKey, now);
            float[] left = brachiate
                    ? brachiateHand(frame, leftShoulder, center, axis, leftKey, rightKey, now)
                    : barHand(frame, leftShoulder, center, axis, leftKey, rightKey, now);
            if (!brachiate) {
                SWINGS.remove(rightKey);
                SWINGS.remove(leftKey);
            }
            if (right != null && left != null) {
                float shortBy = Math.max(right[4], left[4]);
                float raise = BODY_RAISE.getOrDefault(uuid, 0f);
                Long last = BODY_RAISE_AT.get(uuid);
                float dt = last == null ? 1f : Math.min(0.1f, (now - last) / 1e9f);
                raise += (Math.min(BAR_MAX_RAISE, raise + shortBy) - raise)
                        * (1f - (float) Math.exp(-dt / 0.06f));
                BODY_RAISE.put(uuid, raise);
                BODY_RAISE_AT.put(uuid, now);
                arms = new ParCoolHandIK.BarArms(true,
                        right[0], right[1], right[5], right[3],
                        left[0], left[1], left[5], left[3], raise);
            }
        }
        SOLVED.put(uuid, arms);
        SOLVED_AT.put(uuid, now);
        return arms;
    }

    static float phase(UUID uuid, boolean right) {
        Swing swing = SWINGS.get(uuid.toString() + (right ? "R" : "L"));
        return swing != null ? swing.phase
                : ParCoolLimbGait.phase(uuid.toString() + (right ? "R" : "L"));
    }

    @Nullable
    private static float[] barHand(IKFrame frame, Vector3f shoulder, Vec3 center, Vec3 axis,
                                   String key, String otherKey, long now) {
        Vector3f out = frame.modelToWorld()
                .transformDirection(new Vector3f(Math.signum(shoulder.x()), 0, 0)).normalize();
        double wider = (out.x * axis.x + out.z * axis.z) * BAR_HANDS_WIDER;
        Vec3 onBar = center.add(axis.scale(frame.jointWorld(shoulder).subtract(center).dot(axis) + wider));
        Vec3 grip = ParCoolLimbGait.step(key, otherKey, onBar, UP, now, ParCoolLimbGait.BAR_SIDE);
        float[] aimed = pointArm(frame, shoulder, grip);
        return aimed == null ? null : smoothArm(key, aimed, false, now);
    }

    @Nullable
    private static float[] brachiateHand(IKFrame frame, Vector3f shoulder, Vec3 center, Vec3 axis,
                                         String key, String otherKey, long now) {
        Vec3 shoulderAt = frame.jointWorld(shoulder);
        double base = center.dot(axis);
        double along = shoulderAt.dot(axis);
        Vec3 under = center.add(axis.scale(along - base));
        Swing swing = SWINGS.computeIfAbsent(key, ignored -> new Swing());
        if (swing.grip == null || Math.abs(swing.grip.y - under.y) > 0.25) {
            swing.grip = under;
            swing.released = false;
        }
        Swing other = SWINGS.get(otherKey);

        if (!swing.released) {
            double past = along - swing.grip.dot(axis);
            if (Math.abs(past) > RELEASE_AFTER && (other == null || !other.released)) {
                ArmTrack track = ARM_TRACKS.get(key);
                swing.released = true;
                swing.way = past > 0 ? 1 : -1;
                swing.releasedAt = along;
                swing.releasedNanos = now;
                swing.fromX = track == null ? HANGING_X : track.x;
                swing.fromZ = track == null ? 0f : track.z;
            }
        }
        if (swing.released) {
            double moved = (along - swing.releasedAt) * swing.way;
            if (moved < -BACK_TO_GRIP) {
                swing.released = false;
            } else if (moved >= SWING_OVER) {
                swing.grip = center.add(axis.scale(along - base + swing.way * REGRIP_AHEAD));
                swing.released = false;
            }
        }
        if (!swing.released) {
            swing.phase = 0f;
            float[] aimed = pointArm(frame, shoulder, swing.grip);
            return aimed == null ? null : smoothArm(key, aimed, true, now);
        }

        double moved = (along - swing.releasedAt) * swing.way;
        float forward = smoothStep((float) Math.max(0, Math.min(1, moved / SWING_OVER)));
        float drop = smoothStep(Math.min(1f, (now - swing.releasedNanos) / 1e9f / DROP_SECONDS));
        swing.phase = 0.3f + 0.7f * forward;

        Vec3 nextGrip = center.add(axis.scale(along - base + swing.way * REGRIP_AHEAD));
        float[] next = pointArm(frame, shoulder, nextGrip);
        if (next == null) return null;
        IKMath.Angles nextAngles = IKMath.anglesXZ(IKMath.directionXY(next[0], next[1]));
        float fromX = near(swing.fromX, HANGING_X);
        float toX = near(nextAngles.x(), HANGING_X);
        float hangX = fromX + (HANGING_X - fromX) * drop;
        float hangZ = swing.fromZ * (1f - drop);
        float x = hangX + (toX - hangX) * forward;
        float z = hangZ + (nextAngles.secondary() - hangZ) * forward;

        ArmTrack track = ARM_TRACKS.computeIfAbsent(key, ignored -> new ArmTrack());
        if (track.nanos == 0 || now - track.nanos > 250_000_000L) {
            track.x = x;
            track.z = z;
        } else {
            float k = 1f - (float) Math.exp(-(now - track.nanos) / 1e9f / SWING_FOLLOW_SECONDS);
            track.x += IKMath.wrap(x - track.x) * k;
            track.z += (z - track.z) * k;
        }
        track.direction = IKMath.directionXZ(track.x, track.z);
        track.nanos = now;
        return new float[]{track.x, 0f, next[2], 0f, 0f, track.z};
    }

    private static float smoothStep(float value) {
        return value * value * (3f - 2f * value);
    }

    private static float near(float angle, float reference) {
        while (angle - reference > Math.PI) angle -= (float) (2 * Math.PI);
        while (angle - reference < -Math.PI) angle += (float) (2 * Math.PI);
        return angle;
    }

    @Nullable
    private static float[] pointArm(IKFrame frame, Vector3f shoulder, Vec3 grip) {
        IKResult result = OneBoneIK.solveXY(frame, shoulder, grip,
                ParCoolHandIK.ARM_REACH, ParCoolHandIK.MAX_LIFT, BACK_SPREAD);
        return result == null ? null : new float[]{
                result.x(), result.y(), result.reach(), result.lift(), result.shortfall(), 0f};
    }

    private static float[] smoothArm(String key, float[] aimed, boolean frontToBack, long now) {
        Vector3f target = IKMath.directionXY(aimed[0], aimed[1]);
        ArmTrack track = ARM_TRACKS.get(key);
        if (track == null || track.direction == null || now - track.nanos > 250_000_000L) {
            track = new ArmTrack();
            ARM_TRACKS.put(key, track);
            track.direction = target;
        } else {
            float dt = (now - track.nanos) / 1e9f;
            track.direction = IKMath.slerp(track.direction, target,
                    1f - (float) Math.exp(-dt / ARM_FOLLOW_SECONDS));
        }
        Vector3f direction = track.direction;
        if (frontToBack) {
            IKMath.Angles angles = IKMath.anglesXZ(direction);
            track.x = track.nanos == 0 ? angles.x()
                    : track.x + IKMath.wrap(angles.x() - track.x);
            track.y = 0f;
            track.z = angles.secondary();
        } else {
            float x = -(float) Math.acos(Math.max(-1f, Math.min(1f, direction.y)));
            float y = Math.abs(Math.sin(x)) < 1e-3f
                    ? track.y : (float) Math.atan2(-direction.x, -direction.z);
            track.x = track.nanos == 0 ? x : track.x + IKMath.wrap(x - track.x);
            track.y = track.nanos == 0 ? y : track.y + IKMath.wrap(y - track.y);
        }
        track.nanos = now;
        return new float[]{track.x, track.y, aimed[2], aimed[3], aimed[4],
                frontToBack ? track.z : 0f};
    }

    private static Vector3f shifted(Vector3f pivot, float y) {
        return new Vector3f(pivot).add(0, y, 0);
    }

    private static Vec3 barCenter(AbstractClientPlayer player, BlockPos pos) {
        VoxelShape shape = player.level().getBlockState(pos).getCollisionShape(player.level(), pos);
        if (shape.isEmpty()) return Vec3.atCenterOf(pos);
        AABB box = shape.bounds();
        return new Vec3(pos.getX() + (box.minX + box.maxX) / 2,
                pos.getY() + (box.minY + box.maxY) / 2,
                pos.getZ() + (box.minZ + box.maxZ) / 2);
    }

    private static final class Swing {
        Vec3 grip;
        boolean released;
        int way;
        double releasedAt;
        long releasedNanos;
        float fromX;
        float fromZ;
        float phase;
    }

    private static final class ArmTrack {
        Vector3f direction;
        float x;
        float y;
        float z;
        long nanos;
    }
}

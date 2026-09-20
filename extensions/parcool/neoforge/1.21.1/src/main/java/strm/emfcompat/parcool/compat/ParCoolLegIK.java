package strm.emfcompat.parcool.compat;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKMath;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Places a hanging player's feet against a wall and alternates them while shuffling. */
final class ParCoolLegIK {
    private static final long SOLVE_EVERY_NANOS = 2_000_000L;
    private static final Vector3f RIGHT_HIP = new Vector3f(-1.9f, 12f, 0f);
    private static final Vector3f LEFT_HIP = new Vector3f(1.9f, 12f, 0f);
    private static final double FOOT_DROP = 0.62;
    private static final double FOOT_WALL_REACH = 0.9;
    private static final double FOOT_CLEARANCE = 0.03;
    private static final float LEG_OUT = (float) Math.toRadians(40);
    private static final float LEG_IN = (float) Math.toRadians(24);

    private static final Map<UUID, ParCoolHandIK.Legs> SOLVED = new HashMap<>();
    private static final Map<UUID, Long> SOLVED_AT = new HashMap<>();

    private ParCoolLegIK() {
    }

    static ParCoolHandIK.Legs solve(AbstractClientPlayer player, @Nullable Vec3 wall,
                                    @Nullable IKFrame frame) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        Long at = SOLVED_AT.get(uuid);
        if (at != null && now - at < SOLVE_EVERY_NANOS) return SOLVED.get(uuid);

        ParCoolHandIK.Legs legs = ParCoolHandIK.Legs.NONE;
        if (frame != null && wall != null && new Vec3(wall.x, 0, wall.z).lengthSqr() > 1e-4) {
            Vec3 horizontal = new Vec3(wall.x, 0, wall.z).normalize();
            float offset = ParCoolHandIK.arms(player, wall).catchOffset();
            float[] right = foot(player, frame, shifted(RIGHT_HIP, offset), horizontal,
                    uuid + "RF", uuid + "LF", now);
            float[] left = foot(player, frame, shifted(LEFT_HIP, offset), horizontal,
                    uuid + "LF", uuid + "RF", now);
            if (right != null && left != null) {
                float spread = right[1] - left[1];
                if (spread < 0f) {
                    right[1] -= spread / 2f;
                    left[1] += spread / 2f;
                }
            }
            legs = new ParCoolHandIK.Legs(
                    right != null, right == null ? 0 : right[0], right == null ? 0 : right[1],
                    left != null, left == null ? 0 : left[0], left == null ? 0 : left[1]);
        }
        SOLVED.put(uuid, legs);
        SOLVED_AT.put(uuid, now);
        return legs;
    }

    private static Vector3f shifted(Vector3f pivot, float y) {
        return new Vector3f(pivot).add(0, y, 0);
    }

    @Nullable
    private static float[] foot(AbstractClientPlayer player, IKFrame frame, Vector3f hip, Vec3 wall,
                                String key, String otherKey, long now) {
        Vec3 from = frame.jointWorld(hip).add(0, -FOOT_DROP, 0);
        BlockHitResult face = clip(player, from, from.add(wall.scale(FOOT_WALL_REACH)));
        if (face.getType() == HitResult.Type.MISS || face.getDirection().getAxis().isVertical()) {
            ParCoolLimbGait.remove(key);
            return null;
        }
        Vec3 set = ParCoolLimbGait.step(key, otherKey,
                face.getLocation().subtract(wall.scale(FOOT_CLEARANCE)), wall, now, ParCoolLimbGait.FOOT);
        Vector3f direction = frame.relativeToJoint(set, hip);
        if (direction.lengthSquared() < 1e-6f) return null;
        IKMath.Angles angles = IKMath.anglesXZDownward(direction.normalize());
        float out = Math.signum(hip.x) < 0 ? 1f : -1f;
        float z = out * Math.max(-LEG_IN, Math.min(LEG_OUT, out * angles.secondary()));
        return new float[]{angles.x(), z};
    }

    private static BlockHitResult clip(AbstractClientPlayer player, Vec3 from, Vec3 to) {
        return player.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }
}

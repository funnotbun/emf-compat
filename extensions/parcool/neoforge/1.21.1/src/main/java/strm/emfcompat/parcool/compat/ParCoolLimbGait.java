package strm.emfcompat.parcool.compat;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Alternates planted limbs while a player travels along a ledge or bar. */
final class ParCoolLimbGait {
    static final Gait HAND = new Gait(0.34, 0.22, 0.09, 0.04, 0.34f);
    static final Gait FOOT = new Gait(0.3, 0.24, 0.08, 0.16, 0.7f);
    static final Gait BAR_SIDE = new Gait(0.3, 0.2, 0.1, 0.0, 0.36f);

    /** A ledge top this much higher or lower starts a new planted sequence. */
    private static final double SAME_LEDGE = 0.25;
    private static final Map<String, Step> STEPS = new HashMap<>();

    private ParCoolLimbGait() {
    }

    record Gait(double trigger, double lead, double lift, double away, float seconds) {
    }

    private static final class Step {
        Vec3 planted;
        Vec3 from;
        Vec3 to;
        long start = -1;
        float phase;
        long ended;
    }

    static Vec3 step(String key, String otherKey, Vec3 desired, Vec3 wall, long now) {
        return step(key, otherKey, desired, wall, now, HAND);
    }

    /**
     * Keeps a limb planted until it trails its desired position, then moves it ahead while the
     * paired limb remains planted. The clock makes repeated solves in one frame deterministic.
     */
    static Vec3 step(String key, String otherKey, Vec3 desired, Vec3 wall, long now, Gait gait) {
        Step step = STEPS.computeIfAbsent(key, ignored -> new Step());
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
                step.ended = now;
            } else {
                Vec3 along = desired.subtract(step.from).multiply(1, 0, 1);
                if (along.lengthSqr() > 1e-6) {
                    step.to = desired.add(along.normalize().scale(gait.lead()));
                }
                float ease = t * t * t * (t * (6f * t - 15f) + 10f);
                float arc = (float) Math.sin(Math.PI * t);
                step.phase = arc;
                return step.from.lerp(step.to, ease)
                        .add(0, gait.lift() * arc, 0)
                        .subtract(wall.scale(gait.away() * arc));
            }
        }

        Vec3 behind = desired.subtract(step.planted).multiply(1, 0, 1);
        Step other = STEPS.get(otherKey);
        boolean otherReaching = other != null && other.start >= 0
                && (now - other.start) / 1e9f < gait.seconds() * 0.6f;
        boolean myTurn = other == null || other.ended >= step.ended
                || behind.length() > 2 * gait.trigger();
        if (behind.length() > gait.trigger() && !otherReaching && myTurn) {
            step.from = step.planted;
            step.to = desired.add(behind.normalize().scale(gait.lead()));
            step.start = now;
        }
        return step.planted;
    }

    static float phase(String key) {
        Step step = STEPS.get(key);
        return step == null ? 0f : step.phase;
    }

    static boolean reaching(String key) {
        Step step = STEPS.get(key);
        return step != null && step.start >= 0;
    }

    @Nullable
    static Vec3 heldGrip(String key) {
        Step step = STEPS.get(key);
        return step == null ? null : step.start >= 0 ? step.to : step.planted;
    }

    static void remove(String key) {
        STEPS.remove(key);
    }

    static void reset(UUID uuid) {
        String id = uuid.toString();
        STEPS.remove(id + "R");
        STEPS.remove(id + "L");
        STEPS.remove(id + "RF");
        STEPS.remove(id + "LF");
    }
}

package strm.emfcompat.carryon;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Tracks whether a {@code PlayerModel.setupAnim} invocation reached
 * {@code HumanoidModel.setupAnim}.
 *
 * <p>ParCool's overwriting animations deliberately skip the superclass call. Carry On applies
 * its arm pose from a mixin on that superclass method, so the compatibility layer needs to know
 * when the native pose producer was bypassed. Kept as a small render-thread stack so a nested
 * model setup cannot corrupt the outer invocation.</p>
 */
public final class CarryOnPoseCapture {

    private static final ThreadLocal<Deque<Frame>> FRAMES =
            ThreadLocal.withInitial(ArrayDeque::new);

    private CarryOnPoseCapture() {
    }

    public static void begin(UUID uuid) {
        FRAMES.get().push(new Frame(uuid));
    }

    public static void markHumanoidSetup(UUID uuid) {
        for (Frame frame : FRAMES.get()) {
            if (frame.uuid.equals(uuid)) {
                frame.humanoidSetupRan = true;
                return;
            }
        }
    }

    public static boolean humanoidSetupRan(UUID uuid) {
        Deque<Frame> frames = FRAMES.get();
        return !frames.isEmpty()
                && frames.peek().uuid.equals(uuid)
                && frames.peek().humanoidSetupRan;
    }

    public static void end(UUID uuid) {
        Deque<Frame> frames = FRAMES.get();
        if (!frames.isEmpty() && frames.peek().uuid.equals(uuid)) {
            frames.pop();
        }
        if (frames.isEmpty()) {
            FRAMES.remove();
        }
    }

    private static final class Frame {
        private final UUID uuid;
        private boolean humanoidSetupRan;

        private Frame(UUID uuid) {
            this.uuid = uuid;
        }
    }
}

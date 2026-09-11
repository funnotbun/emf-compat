package strm.emfcompat.core;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Takes out the height animation libraries add to a crouching player on top of the crouch itself.
 *
 * <p>Vanilla drops a crouching player 2/16 of a block ({@code PlayerRenderer#getRenderOffset}), and
 * an EMF pack that animates the crouch lifts the legs by the same two pixels to keep the feet on the
 * ground — Fresh Animations' player pack does it with {@code -2*var.sneak2}. That pair balances.
 * Animation libraries then move the whole render again from their {@code body} bone inside
 * {@code setupRotations}, and nothing balances that: Hackers 'n Slashers' sneak poses set it to
 * -4, a quarter of a block, so the player sat three times deeper than vanilla. Measured in game:</p>
 *
 * <pre>
 *   standing, stance     library shift  0.000   total  0.000
 *   crouched, stance     library shift -0.249   total -0.374
 *   crouched, attacking  library shift -0.028   total -0.153
 * </pre>
 *
 * <p>The last two lines are also the jump: an attack replaces the sneak pose with an animation that
 * does not use the body bone, so the player rose a quarter block for the length of every swing and
 * dropped back after it.</p>
 *
 * <p>So while a player is crouching, the vertical part of what {@code setupRotations} added is
 * removed. Its rotation, scale and horizontal movement stay — a lunge still lunges. The removal
 * lingers briefly after standing up, because the libraries fade their sneak pose out over a few
 * ticks (0.17–0.24 s measured) while vanilla's own offset is gone at once; without the linger that
 * fade reads as the player sinking just after standing.</p>
 */
public final class CrouchNormalizer {

    private CrouchNormalizer() {
    }

    /**
     * Config key for the whole crouch fix — this height correction and the crouch-pose repair in
     * {@link EMFCompatAnimationHook}. They mend two layers of the same thing and neither is any use
     * without the other, so there is one switch for both.
     */
    public static final String KEY_ENABLED = "core.crouchFix";

    /** Longer than the libraries' sneak fade-out, with room to spare. */
    private static final long LINGER_NANOS = 350_000_000L;

    private static final Map<UUID, Long> LAST_CROUCH = new HashMap<>();

    public static boolean isEnabled() {
        return EMFCompatCore.isCompatEnabled() && EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    /** What {@link #appliesTo} decided for this render. */
    public enum Mode {
        /** Leave the render alone. */
        NONE,
        /** Crouching: any vertical shift from the libraries goes. */
        ALL,
        /** Just stood up: only a leftover downward shift goes, never an upward one. */
        DOWNWARD_ONLY
    }

    /** Decides whether, and how, this render's vertical body shift should be taken out. */
    public static Mode appliesTo(LivingEntity entity) {
        if (!(entity instanceof Player player) || !isEnabled()) {
            return Mode.NONE;
        }
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        if (player.isCrouching()) {
            LAST_CROUCH.put(uuid, now);
            return Mode.ALL;
        }
        Long last = LAST_CROUCH.get(uuid);
        if (last == null) {
            return Mode.NONE;
        }
        if (now - last > LINGER_NANOS) {
            LAST_CROUCH.remove(uuid);
            return Mode.NONE;
        }
        return Mode.DOWNWARD_ONLY;
    }

    /**
     * Removes the vertical translation that was added on top of {@code before}, leaving everything
     * else {@code pose} carries. Returns how much was removed, in blocks.
     *
     * <p>Worked in the frame the entity was placed in: {@code before⁻¹ · pose} is exactly what
     * {@code setupRotations} added, and zeroing its Y translation before multiplying it back on is
     * the same as moving the result back down (or up) along world Y — whatever rotation the libraries
     * applied in between.</p>
     */
    public static float normalise(Matrix4f before, Matrix4f pose, Mode mode) {
        Matrix4f delta = new Matrix4f(before).invert().mul(pose);
        float dy = delta.m31();
        if (dy == 0f || (mode == Mode.DOWNWARD_ONLY && dy > 0f)) {
            return 0f;
        }
        delta.m31(0f);
        pose.set(before).mul(delta);
        return dy;
    }

    /** Drops state for players no longer in the level. Called by the core cleanup. */
    public static void retainOnly(Collection<UUID> activeUUIDs) {
        LAST_CROUCH.keySet().retainAll(activeUUIDs);
    }
}

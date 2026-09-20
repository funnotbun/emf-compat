package strm.emfcompat.parcool.compat;

import com.alrex.parcool.common.action.ParCoolActions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.parcool.EMFCompatParCoolMod;
import strm.emfcompat.parcool.mixin.ParCool4AnimatorAccessor;
import strm.emfcompat.parcool.mixin.ParCool4ProcessorAccessor;
import strm.emfcompat.parcool.mixin.ParCool4WorkingEntryAccessor;
import com.alrex.parcool.client.animation.system.PlayerAnimator;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.animation.state.EMFState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * ParCool 4 state handed to resource packs as EMF animation variables, so a pack can animate
 * parkour moves itself, in its own procedural style, instead of the addon replaying ParCool's
 * keyframed poses over it.
 *
 * <ul>
 *     <li>{@code parcool_fast_run} - 1 while ParCool's fast run is playing, else 0</li>
 *     <li>{@code parcool_bar} - 1 while hanging under a bar; {@code parcool_bar_swing}/{@code _swing_speed}
 *     the swing ParCool turns the body by, {@code parcool_bar_across} facing across it, and
 *     {@code parcool_rarm_bar_rx}/{@code _ry}/{@code _lift} (and the left ones) the arms on the bar</li>
 *     <li>{@code parcool_crawl}, {@code parcool_fast_swim} - 1 while that move is playing; a pack
 *     that reads them plays its own crawl and swim for ParCool's</li>
 *     <li>{@code parcool_charge} - how far a charge jump is charged, 0 to 1</li>
 *     <li>{@code parcool_charge_jump} - 1 while the jump out of a charge is playing, else 0</li>
 *     <li>{@code parcool_vault} - progress through a vault, 0 to 1; 0 when not vaulting</li>
 *     <li>{@code parcool_vault_side} - -1 vaulting to the left, 1 to the right, 0 straight over</li>
 *     <li>{@code parcool_hang} - 1 while hanging from a ledge</li>
 *     <li>{@code parcool_hang_wall} - 1 while hanging with the feet against the wall</li>
 *     <li>{@code parcool_hang_left_to_wall}, {@code parcool_hang_right_to_wall},
 *     {@code parcool_hang_back_to_wall} - how far a player hanging with the feet on the wall has
 *     turned away from it, 0 to 1, as ParCool blends its look-around poses</li>
 *     <li>{@code parcool_rarm_grip}/{@code parcool_larm_grip} - 1 while that hand holds the ledge, 0
 *     while it hangs free: turned side-on, ParCool holds on with the hand nearer the wall. Eased.</li>
 *     <li>{@code parcool_rarm_hang_rx}/{@code _ry}/{@code _lift} (and the left ones) - the arm as a
 *     pack would draw it hanging, everything below blended: on the ledge by IK, loose, or between</li>
 *     <li>{@code parcool_hang_catch} - how far to move the body down (pixels, negative up) as the
 *     ledge is caught: a short spring, stronger after a longer fall; the IK arm angles below are
 *     worked out from shoulders moved by it, so the hands stay on the ledge</li>
 *     <li>{@code parcool_rarm_reach}/{@code parcool_larm_reach} - shuffling along a ledge, the hands go
 *     hand over hand: 0 while a hand holds, up to 1 mid-reach to its next grip</li>
 *     <li>{@code parcool_rleg_ik}/{@code parcool_lleg_ik} - 1 while hanging with wall below for that
 *     foot; then {@code parcool_rleg_ik_rx}/{@code _rz} (and the left ones) set it on the wall, stepping
 *     along like the hands</li>
 *     <li>{@code parcool_rarm_ik}/{@code parcool_larm_ik} - 1 while hanging with a ledge top found
 *     for that hand; then {@code parcool_rarm_ik_rx}/{@code _ry} (and the left ones) are the arm
 *     rotations, in radians, that put the hand on the ledge, {@code parcool_rarm_ik_reach} the
 *     shoulder-to-ledge distance over the arm's length, and {@code parcool_rarm_ik_lift} how many
 *     pixels to raise the shoulder for those rotations to land the hand. They carry on through a
 *     climb up, the hands pushing on the ledge until they let go halfway.</li>
 *     <li>{@code parcool_body_held} - how much the torso shows ParCool's pose rather than the
 *     pack's, 0 to 1, faded like the core fades it: a cape or anything else hung off the pack's
 *     torso variables should follow the torso part by that much</li>
 *     <li>{@code parcool_climb} - progress climbing up from a ledge, 0 to 1; 0 when not climbing</li>
 * </ul>
 *
 * <p>A pack opts in by reading the variables: EMF only evaluates what an animation uses, so a read
 * is proof that this player's model animates that move itself. Each move is opted into on its own
 * - a pack that only animates the fast run keeps ParCool's vault - and only while the reads keep
 * coming does the addon stop capturing ParCool's pose and its torso lean. Without such a pack
 * nothing changes.</p>
 */
public final class ParCoolPackVariables {

    private static final Logger LOGGER = LoggerFactory.getLogger("emf_compat");

    /** The move each ParCool animation belongs to; a pack reading that move's variables takes it. */
    private static final Map<String, String> MOVE_OF_ANIMATION = Map.ofEntries(
            Map.entry("parcool:fast_run", "fast_run"),
            Map.entry("parcool:jump_charging", "charge"),
            Map.entry("parcool:charge_jump", "charge"),
            Map.entry("parcool:vault_forward", "vault"),
            Map.entry("parcool:vault_side", "vault"),
            Map.entry("parcool:hang_on", "hang"),
            Map.entry("parcool:climb_up", "climb"),
            Map.entry("parcool:climb_up_jump", "climb"),
            Map.entry("parcool:crawl", "crawl"),
            Map.entry("parcool:fast_swim", "fast_swim"),
            Map.entry("parcool:hang_down", "bar"),
            Map.entry("parcool:pole_climb", "pole"));

    /**
     * Moves whose torso transform is only a lean, which a pack animating the move replaces with its
     * own. Elsewhere ParCool's torso transform also turns the body - a hang faces the wall whichever
     * way the player looks - so it stays even when the pack animates the limbs.
     */
    private static final Set<String> LEAN_ONLY_MOVES = Set.of("fast_run", "charge", "crawl", "fast_swim");

    /** A pack read older than this no longer counts: the pack was switched off or reloaded. */
    private static final long PACK_READ_TIMEOUT_NANOS = 500_000_000L;

    /**
     * Set around {@code PlayerRenderer.setupRotations}, so ParCool's renderer hook gets the torso
     * transform without pack-played leans. Render thread only.
     */
    public static boolean settingUpRotations;

    /** When each player's model last read each move's variables. Render thread only. */
    private static final Map<UUID, Map<String, Long>> LAST_READ = new HashMap<>();

    private ParCoolPackVariables() {
    }

    @FunctionalInterface
    private interface Value {
        float of(AbstractClientPlayer player) throws Exception;
    }

    public static void register() {
        register("parcool_fast_run", "fast_run", "1 while ParCool's fast run is playing",
                player -> isRunning(player, "parcool:fast_run") ? 1f : 0f);
        register("parcool_crawl", "crawl", "1 while ParCool's crawl is playing; reading it hands the crawl to the pack's own",
                player -> isRunning(player, "parcool:crawl") ? 1f : 0f);
        register("parcool_fast_swim", "fast_swim", "1 while ParCool's fast swim is playing; reading it hands the swim to the pack's own",
                player -> isRunning(player, "parcool:fast_swim") ? 1f : 0f);
        register("parcool_pole_climb", "pole", "1 while climbing a pole or chain; reading it hands the climb to the pack (FA+Player's ladder climb)",
                player -> doing(action(player, "POLE_CLIMB")) ? 1f : 0f);
        register("parcool_charge", "charge", "How far a ParCool charge jump is charged, 0 to 1",
                player -> (Float) invoke(action(player, "CHARGE_JUMP"), "getChargeProgress", partialTick()));
        register("parcool_charge_jump", "charge", "1 while the jump out of a ParCool charge is playing",
                player -> isRunning(player, "parcool:charge_jump") ? 1f : 0f);
        register("parcool_vault", "vault", "Progress through a ParCool vault, 0 to 1",
                player -> {
                    Object vault = action(player, "VAULT");
                    Byte duration = (Byte) property(vault, "propertyDuration");
                    return doing(vault) && duration != null && duration > 0
                            ? Math.min(1f, (doingTick(vault) + partialTick()) / duration) : 0f;
                });
        register("parcool_vault_side", "vault", "-1 vaulting to the left, 1 to the right, 0 straight over",
                player -> {
                    Object type = property(action(player, "VAULT"), "propertyVaultType");
                    String name = type == null ? "FORWARD" : ((Enum<?>) type).name();
                    return name.equals("LEFT") ? -1f : name.equals("RIGHT") ? 1f : 0f;
                });
        register("parcool_hang", "hang", "1 while hanging from a ledge",
                player -> doing(action(player, "HANG_ON")) ? 1f : 0f);
        register("parcool_hang_wall", "hang", "1 while hanging with the feet against the wall",
                player -> {
                    Object hang = action(player, "HANG_ON");
                    return doing(hang) && Boolean.TRUE.equals(property(hang, "propertyFullWall")) ? 1f : 0f;
                });
        register("parcool_hang_left_to_wall", "hang", "How far a hanging player has turned the left side to the wall, 0 to 1; ParCool then holds on with the right hand",
                player -> hangFactor(player, "getBlendFactorLeftToWall"));
        register("parcool_hang_right_to_wall", "hang", "How far a hanging player has turned the right side to the wall, 0 to 1; ParCool then holds on with the left hand",
                player -> hangFactor(player, "getBlendFactorRightToWall"));
        register("parcool_hang_back_to_wall", "hang", "How far a hanging player has turned the back to the wall, 0 to 1",
                player -> hangFactor(player, "getBlendFactorBackToWall"));
        register("parcool_torso_yaw", "hang", "Degrees ParCool turns the torso about the vertical (hanging it faces the wall); a head that should look where the player looks turns back by it",
                ParCoolHandIK::torsoYaw);
        register("parcool_head_yaw", "hang", "Head y rotation, degrees, that looks where the player looks from the torso as ParCool poses it, within a neck's turn",
                player -> ParCoolHandIK.headAngles(player, partialTick())[0]);
        register("parcool_head_pitch", "hang", "Head x rotation, degrees (down positive), that looks where the player looks from the torso as ParCool poses it",
                player -> ParCoolHandIK.headAngles(player, partialTick())[1]);
        register("parcool_rarm_grip", "hang", "1 while the right hand holds the ledge, 0 while it hangs free (looking away along the wall); eased",
                player -> holds(player).right().grip());
        register("parcool_larm_grip", "hang", "1 while the left hand holds the ledge, 0 while it hangs free (looking away along the wall); eased",
                player -> holds(player).left().grip());
        register("parcool_rarm_hang_rx", "hang", "Right arm x rotation while hanging, radians: on the ledge, hanging free or in between",
                player -> holds(player).right().x());
        register("parcool_rarm_hang_ry", "hang", "Right arm y rotation while hanging, radians; continuous, it does not wrap",
                player -> holds(player).right().y());
        register("parcool_rarm_hang_lift", "hang", "Pixels to raise the right shoulder while hanging",
                player -> holds(player).right().lift());
        register("parcool_larm_hang_rx", "hang", "Left arm x rotation while hanging, radians: on the ledge, hanging free or in between",
                player -> holds(player).left().x());
        register("parcool_larm_hang_ry", "hang", "Left arm y rotation while hanging, radians; continuous, it does not wrap",
                player -> holds(player).left().y());
        register("parcool_larm_hang_lift", "hang", "Pixels to raise the left shoulder while hanging",
                player -> holds(player).left().lift());
        register("parcool_hang_catch", "hang", "Body offset while catching a ledge or climbing up from it, pixels, down positive; the ik arm angles already allow for it",
                player -> hands(player).catchOffset());
        register("parcool_rarm_reach", "hang", "Shuffling along a ledge: 0 while the right hand holds, rising to 1 mid-reach to its next grip",
                player -> doing(action(player, "HANG_ON")) ? ParCoolHandIK.stepPhase(player.getUUID(), true) : 0f);
        register("parcool_larm_reach", "hang", "Shuffling along a ledge: 0 while the left hand holds, rising to 1 mid-reach to its next grip",
                player -> doing(action(player, "HANG_ON")) ? ParCoolHandIK.stepPhase(player.getUUID(), false) : 0f);
        register("parcool_bar", "bar", "1 while hanging under a bar (ParCool's hang down); ParCool still swings and turns the body",
                player -> doing(action(player, "HANG_DOWN")) ? 1f : 0f);
        register("parcool_bar_swing", "bar", "How far the body has swung under the bar, radians; ParCool turns the body by it",
                player -> barFloat(player, "getRotationAngle"));
        register("parcool_bar_swing_speed", "bar", "How fast the body swings under the bar, radians per tick",
                player -> barFloat(player, "getAngularSpeed"));
        register("parcool_bar_across", "bar", "1 facing across the bar (the body can swing), 0 facing along it",
                player -> barFloat(player, "getBlendFactorOrthogonalToBar"));
        register("parcool_bar_ik", "bar", "1 while the hands are on the bar, else 0", player -> bar(player).valid() ? 1f : 0f);
        register("parcool_rarm_bar_rx", "bar", "Right arm x rotation that puts the hand on the bar, radians", player -> bar(player).rightX());
        register("parcool_rarm_bar_ry", "bar", "Right arm y rotation that puts the hand on the bar, radians", player -> bar(player).rightY());
        register("parcool_bar_raise", "bar", "Pixels to raise the body under the bar so the hands reach it", player -> bar(player).raise());
        register("parcool_rarm_bar_rz", "bar", "Right arm z rotation on the bar, radians; along the bar the arms swing by x and lean by z, with no y", player -> bar(player).rightZ());
        register("parcool_larm_bar_rz", "bar", "Left arm z rotation on the bar, radians", player -> bar(player).leftZ());
        register("parcool_rarm_bar_lift", "bar", "Pixels to raise the right shoulder for the hand to reach the bar", player -> bar(player).rightLift());
        register("parcool_larm_bar_rx", "bar", "Left arm x rotation that puts the hand on the bar, radians", player -> bar(player).leftX());
        register("parcool_larm_bar_ry", "bar", "Left arm y rotation that puts the hand on the bar, radians", player -> bar(player).leftY());
        register("parcool_larm_bar_lift", "bar", "Pixels to raise the left shoulder for the hand to reach the bar", player -> bar(player).leftLift());
        register("parcool_rarm_bar_reach", "bar", "Moving along the bar: 0 while the right hand holds, 0.3 let go and hanging, 1 at its next grip",
                player -> doing(action(player, "HANG_DOWN")) ? ParCoolHandIK.barPhase(player.getUUID(), true) : 0f);
        register("parcool_larm_bar_reach", "bar", "Moving along the bar: 0 while the left hand holds, 0.3 let go and hanging, 1 at its next grip",
                player -> doing(action(player, "HANG_DOWN")) ? ParCoolHandIK.barPhase(player.getUUID(), false) : 0f);
        register("parcool_rleg_ik", "hang", "1 while hanging with wall below for the right foot to stand on, else 0",
                player -> legs(player).rightValid() ? 1f : 0f);
        register("parcool_rleg_ik_rx", "hang", "Right leg x rotation that sets the foot on the wall, radians", player -> legs(player).rightX());
        register("parcool_rleg_ik_rz", "hang", "Right leg z rotation (outward positive) that sets the foot on the wall, radians", player -> legs(player).rightZ());
        register("parcool_lleg_ik", "hang", "1 while hanging with wall below for the left foot to stand on, else 0",
                player -> legs(player).leftValid() ? 1f : 0f);
        register("parcool_lleg_ik_rx", "hang", "Left leg x rotation that sets the foot on the wall, radians", player -> legs(player).leftX());
        register("parcool_lleg_ik_rz", "hang", "Left leg z rotation (outward negative) that sets the foot on the wall, radians", player -> legs(player).leftZ());
        register("parcool_rarm_ik", "hang", "1 while hanging with a ledge top found for the right hand, else 0",
                player -> hands(player).rightValid() ? 1f : 0f);
        register("parcool_larm_ik", "hang", "1 while hanging with a ledge top found for the left hand, else 0",
                player -> hands(player).leftValid() ? 1f : 0f);
        register("parcool_rarm_ik_rx", "hang", "Right arm x rotation that puts the hand on the ledge, radians",
                player -> hands(player).rightX());
        register("parcool_rarm_ik_ry", "hang", "Right arm y rotation that puts the hand on the ledge, radians",
                player -> hands(player).rightY());
        register("parcool_rarm_ik_reach", "hang", "Shoulder to ledge over the arm's length; above 1 the hand falls short",
                player -> hands(player).rightReach());
        register("parcool_rarm_ik_lift", "hang", "How far to raise the right shoulder for the hand to reach, pixels",
                player -> hands(player).rightLift());
        register("parcool_larm_ik_rx", "hang", "Left arm x rotation that puts the hand on the ledge, radians",
                player -> hands(player).leftX());
        register("parcool_larm_ik_ry", "hang", "Left arm y rotation that puts the hand on the ledge, radians",
                player -> hands(player).leftY());
        register("parcool_larm_ik_reach", "hang", "Shoulder to ledge over the arm's length; above 1 the hand falls short",
                player -> hands(player).leftReach());
        register("parcool_larm_ik_lift", "hang", "How far to raise the left shoulder for the hand to reach, pixels",
                player -> hands(player).leftLift());
        register("parcool_body_held", "body", "How much the torso shows ParCool's pose instead of the pack's, 0 to 1; a cape follows the pack's torso variables, so it can ease them out by this",
                player -> ParCoolPose.bodyHeld(player.getUUID()));
        register("parcool_climb", "climb", "Progress climbing up from a ledge, 0 to 1",
                ParCoolPackVariables::climbProgress);
    }

    /** How a resource pack plays one of ParCool's running animations; see {@link #packPlays}. */
    public enum PackPlay {
        /** ParCool plays it. */
        NO,
        /** The pack plays it, and ParCool's torso transform for it is only a lean the pack replaces. */
        LEAN,
        /** The pack plays the limbs, but ParCool's torso transform also turns the body, and stays. */
        TURN
    }

    /** The move a running ParCool animation (an {@code AnimationProcessor} entry) belongs to, or null. */
    @Nullable
    public static String moveOf(Object entry) {
        return MOVE_OF_ANIMATION.get(((ParCool4WorkingEntryAccessor) entry).emfcompat$registration().location().toString());
    }

    /** Whether a pack is animating this move for this player right now: it has read its variables lately. */
    public static boolean packAnimates(Player player, String move) {
        if (!EMFCompatParCoolMod.isEnabled()) return false;
        Map<String, Long> reads = LAST_READ.get(player.getUUID());
        Long read = reads == null ? null : reads.get(move);
        return read != null && System.nanoTime() - read <= PACK_READ_TIMEOUT_NANOS;
    }

    /** Whether a pack plays this running ParCool animation (an {@code AnimationProcessor} entry). */
    public static PackPlay packPlays(AbstractClientPlayer player, Object entry) {
        if (!EMFCompatParCoolMod.isEnabled()) return PackPlay.NO;
        Map<String, Long> reads = LAST_READ.get(player.getUUID());
        if (reads == null) return PackPlay.NO;
        String id = ((ParCool4WorkingEntryAccessor) entry).emfcompat$registration().location().toString();
        String move = MOVE_OF_ANIMATION.get(id);
        Long read = move == null ? null : reads.get(move);
        if (read == null || System.nanoTime() - read > PACK_READ_TIMEOUT_NANOS) return PackPlay.NO;
        return LEAN_ONLY_MOVES.contains(move) ? PackPlay.LEAN : PackPlay.TURN;
    }

    private static float hangFactor(AbstractClientPlayer player, String getter) throws ReflectiveOperationException {
        Object hang = action(player, "HANG_ON");
        if (!doing(hang)) return 0f;
        String key = "HangOn#" + getter;
        Method method = METHODS.get(key);
        if (method == null) {
            method = hang.getClass().getMethod(getter);
            METHODS.put(key, method);
        }
        return (Float) method.invoke(hang);
    }

    /**
     * How much a hand holds on, given the side-to-wall factor that frees it. ParCool sets its
     * back-to-wall pose over the side ones, and looking straight back both sides read 1, so the back
     * factor takes that much of the side's weight away again.
     */
    private static float grip(AbstractClientPlayer player, String freedBy) throws ReflectiveOperationException {
        if (!doing(action(player, "HANG_ON"))) return 0f;
        return 1f - hangFactor(player, freedBy) * (1f - hangFactor(player, "getBlendFactorBackToWall"));
    }

    /** The wall each player last hung from, so the hands keep to that ledge while climbing up. */
    private static final Map<UUID, Vec3> LAST_WALL = new HashMap<>();
    /** When each player was last seen climbing up; the loose arms carry on a moment past the end. */
    private static final Map<UUID, Long> LAST_CLIMB = new HashMap<>();
    private static final long CLIMB_TAIL_NANOS = 1_000_000_000L;

    /** Climb-up progress past which the hands let go of the ledge, from start to end of letting go. */
    private static final float CLIMB_RELEASE_FROM = 0.45f;
    private static final float CLIMB_RELEASE_TO = 0.75f;

    private static ParCoolHandIK.Holds holds(AbstractClientPlayer player) throws ReflectiveOperationException {
        Object hang = action(player, "HANG_ON");
        if (doing(hang)) {
            Vec3 wall = (Vec3) invoke(hang, "getWallVec", partialTick());
            if (wall != null) LAST_WALL.put(player.getUUID(), wall);
            return ParCoolHandIK.holds(player, wall,
                    grip(player, "getBlendFactorRightToWall"), grip(player, "getBlendFactorLeftToWall"));
        }
        // Climbing up, the hands stay on the ledge and push the body up past it, then let go.
        float climb = climbProgress(player);
        ParCoolHandIK.climbProgress(player.getUUID(), climb);
        Vec3 wall = LAST_WALL.get(player.getUUID());
        long now = System.nanoTime();
        if (climb > 0f) {
            LAST_CLIMB.put(player.getUUID(), now);
        } else {
            // Past the end the pack is still easing out of the climb: keep the arms loose, not zeroed.
            Long last = LAST_CLIMB.get(player.getUUID());
            if (last != null && now - last < CLIMB_TAIL_NANOS && wall != null) {
                return ParCoolHandIK.holds(player, wall, 0f, 0f, true);
            }
        }
        if (climb > 0f && wall != null) {
            float t = Math.max(0f, Math.min(1f, (climb - CLIMB_RELEASE_FROM) / (CLIMB_RELEASE_TO - CLIMB_RELEASE_FROM)));
            float grip = 1f - t * t * (3f - 2f * t);
            return ParCoolHandIK.holds(player, wall, grip, grip, true);
        }
        return ParCoolHandIK.Holds.NONE;
    }

    private static float climbProgress(AbstractClientPlayer player) throws ReflectiveOperationException {
        Object climb = action(player, "CLIMB_UP");
        int duration = (Integer) field(climb, "duration");
        return doing(climb) && duration > 0
                ? Math.min(1f, (doingTick(climb) + partialTick()) / duration) : 0f;
    }

    private static float barFloat(AbstractClientPlayer player, String getter) throws ReflectiveOperationException {
        Object bar = action(player, "HANG_DOWN");
        return doing(bar) ? (Float) invoke(bar, getter, partialTick()) : 0f;
    }

    /** Whether ParCool is hanging this player under a bar right now. Swallows ParCool's reflection. */
    public static boolean underBar(AbstractClientPlayer player) {
        if (!EMFCompatParCoolMod.isEnabled()) return false;
        try {
            return doing(action(player, "HANG_DOWN"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Whether ParCool is hanging this player from a ledge right now. */
    public static boolean onLedge(AbstractClientPlayer player) {
        if (!EMFCompatParCoolMod.isEnabled()) return false;
        try {
            return doing(action(player, "HANG_ON"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static ParCoolHandIK.BarArms bar(AbstractClientPlayer player) throws ReflectiveOperationException {
        Object bar = action(player, "HANG_DOWN");
        if (!doing(bar)) return ParCoolHandIK.BarArms.NONE;
        BlockPos pos = (BlockPos) field(bar, "hangingPos");
        Object axis = property(bar, "propertyHangingBarAxis");
        if (pos == null || axis == null) return ParCoolHandIK.BarArms.NONE;
        Method positive = METHODS.get("BarAxis#getPositiveVec");
        if (positive == null) {
            positive = axis.getClass().getMethod("getPositiveVec");
            positive.setAccessible(true);
            METHODS.put("BarAxis#getPositiveVec", positive);
        }
        return ParCoolHandIK.barArms(player, pos, (Vec3) positive.invoke(axis));
    }

    private static ParCoolHandIK.Legs legs(AbstractClientPlayer player) throws ReflectiveOperationException {
        Object hang = action(player, "HANG_ON");
        if (!doing(hang)) return ParCoolHandIK.Legs.NONE;
        return ParCoolHandIK.legs(player, (Vec3) invoke(hang, "getWallVec", partialTick()));
    }

    private static ParCoolHandIK.Arms hands(AbstractClientPlayer player) throws ReflectiveOperationException {
        Object hang = action(player, "HANG_ON");
        if (doing(hang)) return ParCoolHandIK.arms(player, (Vec3) invoke(hang, "getWallVec", partialTick()));
        Vec3 wall = LAST_WALL.get(player.getUUID());
        float climb = climbProgress(player);
        ParCoolHandIK.climbProgress(player.getUUID(), climb);
        Long last = LAST_CLIMB.get(player.getUUID());
        boolean tail = last != null && System.nanoTime() - last < CLIMB_TAIL_NANOS;
        if ((climb > 0f || tail) && wall != null) return ParCoolHandIK.arms(player, wall, true);
        return ParCoolHandIK.Arms.NONE;
    }

    private static void register(String name, String move, String explanation, Value value) {
        try {
            EMFAnimationApi.registerSingletonAnimationVariable(EMFCompatParCoolMod.MOD_ID, name, explanation,
                    () -> read(move, value));
        } catch (Throwable t) {
            LOGGER.warn("[EMF Compat] could not register the EMF variable {}", name, t);
        }
    }

    private static float read(String move, Value value) {
        try {
            AbstractClientPlayer player = current();
            if (player == null) return 0f;
            // The master switch has to reach the pack, not only our own replay: a pack reads these
            // variables every frame and animates the move from them, so unless they go to 0 turning
            // the addon off leaves the module animating every move as before. At 0 the module's own
            // weights fade it out (see animations.py) and nothing snaps.
            if (!EMFCompatParCoolMod.isEnabled()) return 0f;
            LAST_READ.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(move, System.nanoTime());
            return value.of(player);
        } catch (Throwable t) {
            // A throw out of an animation variable makes EMF disable the whole model's animation.
            return 0f;
        }
    }

    @Nullable
    private static AbstractClientPlayer current() {
        EMFEntityRenderState state = EMFState.state();
        if (state == null || state.uuid() == null || Minecraft.getInstance().level == null) return null;
        Player player = Minecraft.getInstance().level.getPlayerByUUID(state.uuid());
        return player instanceof AbstractClientPlayer client ? client : null;
    }

    private static float partialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    // ParCool's actions, by reflection: ParCool 3 and 4 both ship a Parkourability and this module
    // compiles against both, so the compiler cannot be told which one to link. Lookups are cached.

    private static final Map<String, Method> METHODS = new HashMap<>();
    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static Method parkourabilityOf;
    private static Method actionOf;

    private static Object action(AbstractClientPlayer player, String entry) throws ReflectiveOperationException {
        if (parkourabilityOf == null) {
            Class<?> parkourability = Class.forName("com.alrex.parcool.common.Parkourability");
            parkourabilityOf = parkourability.getMethod("get", Player.class);
            actionOf = parkourability.getMethod("get", Class.forName("com.alrex.parcool.api.action.ActionEntry"));
        }
        Object parkourability = parkourabilityOf.invoke(null, player);
        if (parkourability == null) throw new IllegalStateException("no parkourability");
        return actionOf.invoke(parkourability, ParCoolActions.class.getField(entry).get(null));
    }

    private static Object invoke(Object target, String name, float argument) throws ReflectiveOperationException {
        Method method = METHODS.get(target.getClass().getName() + "#" + name);
        if (method == null) {
            method = target.getClass().getMethod(name, float.class);
            METHODS.put(target.getClass().getName() + "#" + name, method);
        }
        return method.invoke(target, argument);
    }

    private static boolean doing(Object action) throws ReflectiveOperationException {
        Method method = METHODS.get("isDoing");
        if (method == null) {
            method = Class.forName("com.alrex.parcool.api.action.ContinuableAction").getMethod("isDoing");
            METHODS.put("isDoing", method);
        }
        return (Boolean) method.invoke(action);
    }

    private static int doingTick(Object action) throws ReflectiveOperationException {
        Method method = METHODS.get("getDoingTick");
        if (method == null) {
            method = Class.forName("com.alrex.parcool.api.action.ContinuableAction").getMethod("getDoingTick");
            METHODS.put("getDoingTick", method);
        }
        return (Integer) method.invoke(action);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        String key = target.getClass().getName() + "#" + name;
        Field field = FIELDS.get(key);
        if (field == null) {
            field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            FIELDS.put(key, field);
        }
        return field.get(target);
    }

    /** The value of one of an action's synchronized properties. */
    @Nullable
    private static Object property(Object action, String name) throws ReflectiveOperationException {
        Object property = field(action, name);
        if (property == null) return null;
        Method get = METHODS.get("SynchronizedProperty#get");
        if (get == null) {
            get = property.getClass().getMethod("get");
            METHODS.put("SynchronizedProperty#get", get);
        }
        return get.invoke(property);
    }

    /** Whether one of ParCool's animations is running on this player, by its id. */
    public static boolean isRunning(AbstractClientPlayer player, String id) {
        for (Object entry : running(player)) {
            if (((ParCool4WorkingEntryAccessor) entry).emfcompat$registration().location().toString().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static List<?> running(AbstractClientPlayer player) {
        return ((ParCool4ProcessorAccessor) ((ParCool4AnimatorAccessor) PlayerAnimator.get(player))
                .emfcompat$processor()).emfcompat$animators();
    }
}

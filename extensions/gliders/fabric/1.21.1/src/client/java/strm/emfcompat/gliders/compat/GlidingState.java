package strm.emfcompat.gliders.compat;

import net.minecraft.world.entity.player.Player;
import net.fabricmc.loader.api.FabricLoader;
import strm.emfcompat.gliders.GlidersEMFCompatClient;
import traben.entity_model_features.models.animation.state.EMFState;

/**
 * Central dispatcher for gliding detection across the supported glider mods.
 * Every supported mod is a soft dependency: its compat classes are only touched
 * when the mod is actually loaded, so any subset of them may be installed.
 */
public final class GlidingState {

    public static final String SOURCE = "gliders";

    // Tictim's Paragliders has no Fabric build for 1.21.1, so that branch is absent here rather
    // than gated off — ParagliderCompat is not part of this module at all.
    private static final boolean VC_GLIDERS_LOADED = FabricLoader.getInstance().isModLoaded("vc_gliders");
    private static final boolean RELIABLE_GLIDERS_LOADED = FabricLoader.getInstance().isModLoaded("reliable_gliders");

    private GlidingState() {}

    public static boolean isVcGlidersLoaded() {
        return VC_GLIDERS_LOADED;
    }

    public static boolean isReliableGlidersLoaded() {
        return RELIABLE_GLIDERS_LOADED;
    }

    public static boolean anyGliderModLoaded() {
        return VC_GLIDERS_LOADED || RELIABLE_GLIDERS_LOADED;
    }

    /**
     * Whether the player is gliding with a VC glider. False when the mod is not
     * installed.
     */
    public static boolean isVcGliding(Player player) {
        return VC_GLIDERS_LOADED && GlidersEMFCompatClient.isEnabled() && GlidersEMFCompatClient.isVcGlidersEnabled()
                && VCGlidersCompat.isGliding(player);
    }

    /**
     * Whether the player is gliding with a Reliable Glider. False when the mod is
     * not installed.
     */
    public static boolean isReliableGliding(Player player) {
        return RELIABLE_GLIDERS_LOADED && GlidersEMFCompatClient.isEnabled() && GlidersEMFCompatClient.isReliableGlidersEnabled()
                && ReliableGlidersCompat.isGliding(player);
    }

    /**
     * Whether the player is gliding with any supported glider mod.
     */
    public static boolean isGliding(Player player) {
        return isVcGliding(player) || isReliableGliding(player);
    }

    /**
     * Whether the glider is still opening — the swinging deploy animation is playing and the
     * player should not be treated as in flight yet.
     *
     * <p>Only VC Gliders has a deploy animation: it plays a full-body Player Animator clip that
     * starts with the glider snapping open. Reliable Gliders just poses the arms in
     * {@code setupAnim} with nothing to wait for, so it is never "deploying".</p>
     */
    public static boolean isDeploying(Player player) {
        return VC_GLIDERS_LOADED && GlidersEMFCompatClient.isEnabled() && GlidersEMFCompatClient.isVcGlidersEnabled()
                && VCGlidersCompat.isDeploying(player);
    }

    /**
     * Whether the player should be shown in the flight pose — gliding, and past any deploy
     * animation. This is what drives the {@code abilities.flying} spoof, so the pack's flight
     * animation only takes over once the glider has finished opening.
     */
    public static boolean isInFlightPose(Player player) {
        return isGliding(player) && !isDeploying(player);
    }

    /**
     * Resolves the entity currently being animated by EMF and checks it for
     * gliding with any supported mod. Never throws: EMF disables ALL animations
     * of a model if an exception escapes animation evaluation.
     */
    public static boolean isCurrentEmfEntityGliding() {
        try {
            var state = EMFState.state();
            return state != null && state.emfEntity() instanceof Player player && isGliding(player);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * {@link #isInFlightPose(Player)} for the entity currently being animated by EMF. Never
     * throws, for the same reason as {@link #isCurrentEmfEntityGliding()}.
     */
    public static boolean isCurrentEmfEntityInFlightPose() {
        try {
            var state = EMFState.state();
            return state != null && state.emfEntity() instanceof Player player && isInFlightPose(player);
        } catch (Throwable t) {
            return false;
        }
    }
}

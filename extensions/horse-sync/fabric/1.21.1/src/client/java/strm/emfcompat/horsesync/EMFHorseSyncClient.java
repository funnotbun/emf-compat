package strm.emfcompat.horsesync;

import net.fabricmc.api.ClientModInitializer;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.horsesync.compat.EMFCompat;

public class EMFHorseSyncClient implements ClientModInitializer {

    public static final String MOD_ID = "emf_compat_horse_sync";
    public static final String KEY_ENABLED = "horsesync.enabled";
    public static final String KEY_RIDING_ANIMATION = "horsesync.ridingAnimation";

    /** Pose source name for the riding animation. */
    public static final String RIDING_SOURCE = "horse_riding";

    @Override
    public void onInitializeClient() {
        // The riding seat is a low-priority base: action poses (guns, attacks) take the arms while
        // the seat keeps the legs/body.
        PoseManager.setSourcePriority(RIDING_SOURCE, -10);

        ConfigRegistry.section(MOD_ID, "Horse Sync")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Sync the ridden-horse animation onto the EMF player model.",
                        "Off", "Disable horse-sync EMF compatibility.")
                .addBoolean(KEY_RIDING_ANIMATION, "Riding animation", true,
                        "On", "Play a proper riding pose (legs straddling, hands on the reins) while on a horse.",
                        "Off", "Leave the mounted pose to the vanilla / resource-pack animation.");
        EMFCompat.init();
        // EMF calls this back once per rendered entity, right after the pack animation.
        HorseSyncAnimationHook.register();
        HorseOffsetCleanup.register();
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isRidingAnimation() {
        return EMFCompatConfig.getBoolean(KEY_RIDING_ANIMATION, true);
    }

}

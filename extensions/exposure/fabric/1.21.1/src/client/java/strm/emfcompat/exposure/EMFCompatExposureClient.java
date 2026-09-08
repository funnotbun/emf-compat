package strm.emfcompat.exposure;

import net.fabricmc.api.ClientModInitializer;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

public class EMFCompatExposureClient implements ClientModInitializer {
    public static final String MOD_ID = "emf_compat_exposure";

    public static final String KEY_ENABLED = "exposure.enabled";
    public static final String KEY_BODY_FOLLOW_ARMS = "exposure.bodyFollowArms";

    @Override
    public void onInitializeClient() {
        ConfigRegistry.section(MOD_ID, "Exposure")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to Exposure camera poses.",
                        "Off", "Disable all EMF compatibility for Exposure.")
                .addBoolean(KEY_BODY_FOLLOW_ARMS, "Arm sync", true,
                        "Body-follow (new)", "Camera arm poses keep their shape and follow the moving torso.",
                        "Rotation-only (legacy)", "Camera arm poses keep only their rotation.");
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isBodyFollow() {
        return EMFCompatConfig.getBoolean(KEY_BODY_FOLLOW_ARMS, true);
    }
}

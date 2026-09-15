package strm.emfcompat.immersivemelodies;

import net.fabricmc.api.ClientModInitializer;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.immersivemelodies.compat.IllagerArms;

public class ImmersiveMelodiesEMFCompatClient implements ClientModInitializer {

    public static final String MOD_ID = "emf_compat_immersive_melodies";
    public static final String KEY_ENABLED = "immersivemelodies.enabled";
    public static final String KEY_MOBS = "immersivemelodies.mobs";

    @Override
    public void onInitializeClient() {
        ConfigRegistry.section(MOD_ID, "Immersive Melodies")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to Immersive Melodies instrument poses.",
                        "Off", "Disable all EMF compatibility for Immersive Melodies.")
                .addBoolean(KEY_MOBS, "Mobs with instruments", true,
                        "On", "Zombies, skeletons, illagers and other mobs keep their instrument pose too.",
                        "Off", "Only players keep the instrument pose.");
        IllagerArms.register();
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isMobsEnabled() {
        return EMFCompatConfig.getBoolean(KEY_MOBS, true);
    }
}

package strm.emfcompat.immersivemelodies;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.immersivemelodies.compat.IllagerArms;

@Mod(ImmersiveMelodiesEMFCompat.MOD_ID)
public class ImmersiveMelodiesEMFCompat {

    public static final String MOD_ID = "emf_compat_immersive_melodies";
    public static final String KEY_ENABLED = "immersivemelodies.enabled";
    public static final String KEY_MOBS = "immersivemelodies.mobs";

    public ImmersiveMelodiesEMFCompat() {
        ConfigRegistry.section(MOD_ID, "Immersive Melodies")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to Immersive Melodies instrument poses.",
                        "Off", "Disable all EMF compatibility for Immersive Melodies.")
                .addBoolean(KEY_MOBS, "Mobs with instruments", true,
                        "On", "Zombies, skeletons, illagers and other mobs keep their instrument pose too.",
                        "Off", "Only players keep the instrument pose.");
        if (FMLEnvironment.dist == Dist.CLIENT) {
            IllagerArms.register();
        }
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isMobsEnabled() {
        return EMFCompatConfig.getBoolean(KEY_MOBS, true);
    }
}

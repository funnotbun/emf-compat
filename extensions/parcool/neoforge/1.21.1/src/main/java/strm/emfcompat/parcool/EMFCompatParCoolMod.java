package strm.emfcompat.parcool;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.parcool.compat.ParCoolPackVariables;
import strm.emfcompat.parcool.compat.ParCoolPose;

/**
 * ParCool extension entry point. All rendering work is done by the mixins: one capture path
 * per ParCool animation system (3.4.x and 4.x, picked by {@code EMFCompatParCoolMixinPlugin}),
 * both writing into the core {@code PoseManager}, which restores them onto the EMF model.
 */
@Mod(EMFCompatParCoolMod.MOD_ID)
public class EMFCompatParCoolMod {

    public static final String MOD_ID = "emf_compat_parcool";

    /** Master switch: apply EMF compatibility to ParCool at all. */
    public static final String KEY_ENABLED = "parcool.enabled";
    /** Whether the head and torso are held too, or left playing the resource pack's animation. */
    public static final String KEY_WHOLE_POSE = "parcool.wholePose";
    /** Let a resource pack that animates ParCool moves itself take them over from ParCool's poses. */
    public static final String KEY_PACK_ANIMATIONS = "parcool.packAnimations";

    public EMFCompatParCoolMod(IEventBus modEventBus) {
        ConfigRegistry.section(MOD_ID, "ParCool")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to ParCool (vaults, wall runs, rolls, climbing).",
                        "Off", "Disable all EMF compatibility for ParCool (plain ParCool behaviour).")
                .addBoolean(KEY_WHOLE_POSE, "Pose scope", true,
                        "Whole pose",
                        "Hold every part ParCool animates, head and torso included.",
                        "Limbs only",
                        "Hold arms and legs only - the head and torso keep the resource pack's animation.")
                .addBoolean(KEY_PACK_ANIMATIONS, "Resource pack animations", true,
                        "On", "A resource pack that animates ParCool moves itself plays them instead of ParCool's poses.",
                        "Off", "Always use ParCool's own poses.");

        PoseManager.setSourcePriority(ParCoolPose.SOURCE, ParCoolPose.SOURCE_PRIORITY);

        // The pack variables read ParCool 4's animation system, which ParCool 3 does not have.
        if (FMLEnvironment.dist == Dist.CLIENT && isParCool4()) {
            ParCoolPackVariables.register();
        }
    }

    private static boolean isParCool4() {
        return EMFCompatParCoolMod.class.getClassLoader()
                .getResource("com/alrex/parcool/client/animation/system/IPlayerAnimatorHolder.class") != null;
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isPackAnimations() {
        return EMFCompatConfig.getBoolean(KEY_PACK_ANIMATIONS, true);
    }

    public static boolean isWholePose() {
        return EMFCompatConfig.getBoolean(KEY_WHOLE_POSE, true);
    }
}

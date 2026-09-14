package strm.emfcompat.create;

import net.minecraftforge.fml.common.Mod;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

/**
 * Forge 1.20.1 entry point for the Create addon.
 *
 * <p>Only the layers whose upstream mod exists on 1.20.1 are here: Create's own skyhook, Create
 * Stuff 'N Additions, and the Not Enough Animations item-swap fix. Aeronautics, Grappling Hooks,
 * Cosmonautics and Sable Ragdolls ship no 1.20.1 build at all, so those layers are absent rather
 * than switched off.</p>
 */
@Mod(EMFCompatCreateMod.MOD_ID)
public class EMFCompatCreateMod {

    public static final String MOD_ID = "emf_compat_create";

    public static final String KEY_ENABLED = "create.enabled";
    public static final String KEY_BODY_FOLLOW_ARMS = "create.bodyFollowArms";
    public static final String KEY_SKYHOOK = "create.skyhook";
    public static final String KEY_CREATE_SA = "create.createSa";
    public static final String KEY_NEA_ITEMSWAP = "create.neaItemSwap";
    public static final String KEY_HATS = "create.hats";

    public EMFCompatCreateMod() {
        ConfigRegistry.Section section = ConfigRegistry.section(MOD_ID, "Create")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to Create poses.",
                        "Off", "Disable all EMF compatibility for Create.")
                .addBoolean(KEY_BODY_FOLLOW_ARMS, "Arm sync", true,
                        "Body-follow (new)", "Held arm poses keep their shape and follow the moving torso.",
                        "Rotation-only (legacy)", "Held arm poses keep only their rotation.")
                .addBoolean(KEY_SKYHOOK, "Skyhook", true,
                        "On", "Keep the skyhook hang pose while the pack keeps animating.",
                        "Off", "Leave the skyhook pose to EMF.")
                .addBoolean(KEY_HATS, "Hats on mobs", true,
                        "On", "Place the engineer's and logistics hat on top of the head under a resource pack's model.",
                        "Off", "Leave the hat where Create puts it (it sinks to the neck under Fresh Animations).");

        if (CreateMods.CREATE_SA) {
            section.addBoolean(KEY_CREATE_SA, "Stuff 'N Additions", true,
                    "On", "Keep the Grappling Whisk and Block Picker arm poses.",
                    "Off", "Leave those poses to EMF.");
        }

        if (CreateMods.NEA) {
            section.addBoolean(KEY_NEA_ITEMSWAP, "NEA item-swap fix", true,
                    "On", "Suppress Not Enough Animations' item-swap animation while skyhooking.",
                    "Off", "Let NEA play its item-swap animation.");
        }
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isSkyhook() {
        return EMFCompatConfig.getBoolean(KEY_SKYHOOK, true);
    }

    public static boolean isBodyFollow() {
        return EMFCompatConfig.getBoolean(KEY_BODY_FOLLOW_ARMS, true);
    }

    public static boolean isCreateSa() {
        return EMFCompatConfig.getBoolean(KEY_CREATE_SA, true);
    }

    public static boolean isNeaItemSwap() {
        return EMFCompatConfig.getBoolean(KEY_NEA_ITEMSWAP, true);
    }

    public static boolean isHats() {
        return EMFCompatConfig.getBoolean(KEY_HATS, true);
    }
}

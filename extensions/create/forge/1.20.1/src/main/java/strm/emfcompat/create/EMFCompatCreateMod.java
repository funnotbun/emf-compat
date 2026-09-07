package strm.emfcompat.create;

import net.minecraftforge.fml.common.Mod;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

/**
 * Forge 1.20.1 entry point for the Create addon.
 *
 * <p>Only the layers whose upstream mod exists on 1.20.1 are here: Create's own skyhook, and the
 * Not Enough Animations item-swap fix. Aeronautics, Grappling Hooks, Cosmonautics and Sable
 * Ragdolls ship no 1.20.1 build, and Stuff 'N Additions is left out because its detection reads
 * item components that did not exist before 1.20.5 — see the module README.</p>
 */
@Mod(EMFCompatCreateMod.MOD_ID)
public class EMFCompatCreateMod {

    public static final String MOD_ID = "emf_compat_create";

    public static final String KEY_ENABLED = "create.enabled";
    public static final String KEY_SKYHOOK = "create.skyhook";
    public static final String KEY_NEA_ITEMSWAP = "create.neaItemSwap";

    public EMFCompatCreateMod() {
        ConfigRegistry.Section section = ConfigRegistry.section(MOD_ID, "Create")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to Create poses.",
                        "Off", "Disable all EMF compatibility for Create.")
                .addBoolean(KEY_SKYHOOK, "Skyhook", true,
                        "On", "Keep the skyhook hang pose while the pack keeps animating.",
                        "Off", "Leave the skyhook pose to EMF.");

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

    public static boolean isNeaItemSwap() {
        return EMFCompatConfig.getBoolean(KEY_NEA_ITEMSWAP, true);
    }
}

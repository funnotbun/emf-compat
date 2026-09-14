package strm;

import net.fabricmc.api.ClientModInitializer;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

public class CreateFlyEmfCompatClient implements ClientModInitializer {

    public static final String MOD_ID = "emf_compat_create";

    /** Master switch: apply EMF compatibility to Create Fly at all. */
    public static final String KEY_ENABLED = "create.enabled";
    /** Suppress NotEnoughAnimations' item-swap animation while skyhooking. */
    public static final String KEY_NEA_ITEMSWAP = "create.neaItemSwap";
    /** Put Create's hats back on top of the head under a resource pack's model. */
    public static final String KEY_HATS = "create.hats";

    @Override
    public void onInitializeClient() {
        // The tab is named after the mod this build actually targets: the Fabric port is
        // Create Fly, not Create itself.
        ConfigRegistry.section(MOD_ID, "Create Fly")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to Create Fly (skyhook poses).",
                        "Off", "Disable all EMF compatibility for Create Fly.")
                .addBoolean(KEY_NEA_ITEMSWAP, "NEA item-swap fix", true,
                        "On", "Suppress NotEnoughAnimations' item-swap animation while skyhooking.",
                        "Off", "Let NEA play its item-swap animation.")
                .addBoolean(KEY_HATS, "Hats on mobs", true,
                        "On", "Place the engineer's and logistics hat on top of the head under a resource pack's model.",
                        "Off", "Leave the hat where Create puts it (it sinks to the neck under Fresh Animations).");
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isNeaItemSwapFix() {
        return EMFCompatConfig.getBoolean(KEY_NEA_ITEMSWAP, true);
    }

    public static boolean isHats() {
        return EMFCompatConfig.getBoolean(KEY_HATS, true);
    }
}

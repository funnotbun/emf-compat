package strm.neaemfcompat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.neaemfcompat.compat.EMFCompat;

/**
 * Fabric entry point. Mirrors the NeoForge {@code @Mod} class of the same target Minecraft
 * version; the addon's own logic is shared with it verbatim, because 1.21.1 renders the player
 * the same way on both loaders.
 */
public class NEAEMFCompatClient implements ClientModInitializer {

    public static final String MOD_ID = "emf_compat_not_enough_animations";
    public static final String KEY_ENABLED = "nea.enabled";

    @Override
    public void onInitializeClient() {
        ConfigRegistry.section(MOD_ID, "Not Enough Animations")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to NotEnoughAnimations poses.",
                        "Off", "Disable all EMF compatibility for NotEnoughAnimations.");

        if (FabricLoader.getInstance().isModLoaded("entity_model_features")) {
            EMFCompat.init();
        }
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }
}

package strm.emfcompat.create;

import net.minecraftforge.fml.ModList;

/**
 * Presence flags for the optional mods this addon layers onto.
 *
 * <p>Shorter than the NeoForge 1.21.1 list on purpose: Create Aeronautics, Create Grappling
 * Hooks, Cosmonautics and Sable Ragdolls have no 1.20.1 build at all, so their layers are not
 * ported here rather than being gated off at runtime.</p>
 */
public final class CreateMods {

    public static final boolean CREATE_SA = ModList.get().isLoaded("create_sa");
    public static final boolean NEA =
            has("dev/tr7zw/notenoughanimations/animations/hands/ItemSwapAnimation.class");

    private CreateMods() {
    }

    private static boolean has(String resourcePath) {
        return CreateMods.class.getClassLoader().getResource(resourcePath) != null;
    }
}

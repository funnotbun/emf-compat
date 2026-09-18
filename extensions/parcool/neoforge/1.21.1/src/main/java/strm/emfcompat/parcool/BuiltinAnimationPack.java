package strm.emfcompat.parcool;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * The ParCool animation module, shipped inside the jar as a resource pack and offered in the
 * resource pack list like any other.
 *
 * <p>It is not turned on by itself. The module is an edit of Fresh Animations: Player Extension
 * (FreshLX's, shipped with their permission): it needs FA+Player under it and has to sit above it,
 * so the player chooses it, the same way they chose FA+Player.
 */
@EventBusSubscriber(modid = EMFCompatParCoolMod.MOD_ID, value = Dist.CLIENT)
public final class BuiltinAnimationPack {

    /** Where the pack sits in the jar. */
    private static final String PATH = "resourcepacks/parcool_animations";

    private BuiltinAnimationPack() {
    }

    @SubscribeEvent
    static void addPacks(AddPackFindersEvent event) {
        event.addPackFinders(ResourceLocation.fromNamespaceAndPath(EMFCompatParCoolMod.MOD_ID, PATH),
                PackType.CLIENT_RESOURCES, Component.literal("EMF Compat: ParCool Animations"),
                PackSource.BUILT_IN, false, Pack.Position.TOP);
    }
}

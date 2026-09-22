package strm.emfcompat.blahaj;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.PoseManager;

@Mod(EMFCompatBlahajMod.MOD_ID)
public class EMFCompatBlahajMod {

    public static final String MOD_ID = "emf_compat_blahaj";
    public static final String SOURCE = "blahaj";
    public static final String KEY_ENABLED = "blahaj.enabled";

    private static final TagKey<Item> PLUSHIES = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("blahaj", "plushies"));

    public EMFCompatBlahajMod(IEventBus modEventBus) {
        // Cuddle is a passive item-hold pose. Active attack and aim sources should own the arms.
        PoseManager.setSourcePriority(SOURCE, -10);

        ConfigRegistry.section(MOD_ID, "Blåhaj")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Keep Blåhaj cuddle arm poses visible over EMF player animations.",
                        "Off", "Disable EMF compatibility for Blåhaj.");
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isHoldingPlushie(Player player) {
        return isPlushie(player.getMainHandItem()) || isPlushie(player.getOffhandItem());
    }

    private static boolean isPlushie(ItemStack stack) {
        return stack.is(PLUSHIES);
    }
}

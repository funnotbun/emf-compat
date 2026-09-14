package strm.emfcompat.create;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Detects Create Stuff 'N Additions state — a hooked Grappling Whisk, a carrying Block Picker —
 * for the animated player.
 *
 * <p>Fully data-driven: item ids and synced NBT, no create_sa classes, so it is a harmless no-op
 * when the mod is absent (the ids resolve to nothing). Checked against create_sa 2.1.2 for
 * 1.20.1: the tag is still {@code create_sa:jetpack} and the keys are still {@code tagHooked}
 * and {@code tagAction}, the same names the 1.21.1 build uses.</p>
 *
 * <p>The one real difference from the NeoForge 1.21.1 copy is how that NBT is reached. Item
 * components only arrived in 1.20.5, so where the newer module reads
 * {@code DataComponents.CUSTOM_DATA}, this one reads the stack's own tag — which is what
 * create_sa itself does here, through {@code ItemStack.getOrCreateTag()}.</p>
 *
 * <p>Jetpack flight detection is deliberately absent: it only feeds the pack-flight spoofing
 * layer, and that layer is not ported to 1.20.1 because the mods it exists for — Cosmonautics
 * and Create Aeronautics — have no 1.20.1 release.</p>
 */
public final class CreateSaCompat {

    private static final ResourceLocation WHISK_ID = new ResourceLocation("create_sa", "grapplin_whisk");
    private static final ResourceLocation PICKER_ID = new ResourceLocation("create_sa", "block_picker");

    private CreateSaCompat() {
    }

    /**
     * Returns the hand holding a Grappling Whisk that is currently hooked, or {@code null}.
     * Main hand only — SA items do not function off-hand, and capturing there froze the other
     * arm in a half-vanilla pose.
     */
    public static InteractionHand getHookedWhiskHand(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (emfcompat$isItem(stack, WHISK_ID) && emfcompat$getBool(stack, "tagHooked")) {
            return InteractionHand.MAIN_HAND;
        }
        return null;
    }

    /** Returns {@code true} if the player holds a Block Picker carrying a block (main hand only). */
    public static boolean isCarryingBlock(Player player) {
        ItemStack stack = player.getMainHandItem();
        return emfcompat$isItem(stack, PICKER_ID) && emfcompat$getBool(stack, "tagAction");
    }

    private static boolean emfcompat$isItem(ItemStack stack, ResourceLocation id) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }

    /** Reads a boolean off the stack's NBT without creating a tag on a stack that has none. */
    private static boolean emfcompat$getBool(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(key);
    }
}

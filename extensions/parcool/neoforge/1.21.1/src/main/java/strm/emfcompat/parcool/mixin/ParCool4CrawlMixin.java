package strm.emfcompat.parcool.mixin;

import com.alrex.parcool.common.action.impl.Crawl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.parcool.EMFCompatParCoolMod;
import strm.emfcompat.parcool.compat.ParCoolHandsFull;

/**
 * Prevents ParCool's swimming-based crawl while both hands carry an external load.
 *
 * <p>Carry On intentionally has no arm pose in Minecraft's swimming state, but still renders its
 * carried object there. Letting Crawl start therefore puts the object on the player's back/head
 * with no hands holding it. There is no physically coherent pose to merge, so the action is
 * unavailable until the load is put down.</p>
 */
@Mixin(Crawl.class)
public abstract class ParCool4CrawlMixin {

    @Inject(method = "canStart", at = @At("HEAD"), cancellable = true)
    private void emfcompat$requireFreeHandsToCrawl(CallbackInfoReturnable<Boolean> cir) {
        ParCool4ActionAccessor action = (ParCool4ActionAccessor) this;
        if (EMFCompatParCoolMod.isEnabled()
                && ParCoolHandsFull.handsFull(action.emfcompat$getParkourability().player())) {
            cir.setReturnValue(false);
        }
    }
}

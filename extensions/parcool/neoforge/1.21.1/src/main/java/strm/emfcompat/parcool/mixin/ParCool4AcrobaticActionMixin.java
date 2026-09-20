package strm.emfcompat.parcool.mixin;

import com.alrex.parcool.common.action.impl.Dodge;
import com.alrex.parcool.common.action.impl.TrickJump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.parcool.EMFCompatParCoolMod;
import strm.emfcompat.parcool.compat.ParCoolHandsFull;

/** Prevents dodges/dashes and trick-jump flips while both hands carry an external load. */
@Mixin({Dodge.class, TrickJump.class})
public abstract class ParCool4AcrobaticActionMixin {

    @Inject(method = "canStart", at = @At("HEAD"), cancellable = true)
    private void emfcompat$requireFreeHands(CallbackInfoReturnable<Boolean> cir) {
        ParCool4ActionAccessor action = (ParCool4ActionAccessor) this;
        if (EMFCompatParCoolMod.isEnabled()
                && ParCoolHandsFull.handsFull(action.emfcompat$getParkourability().player())) {
            cir.setReturnValue(false);
        }
    }
}

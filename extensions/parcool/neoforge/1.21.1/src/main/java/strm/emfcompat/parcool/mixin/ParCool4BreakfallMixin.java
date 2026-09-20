package strm.emfcompat.parcool.mixin;

import com.alrex.parcool.api.action.SynchronizedProperty;
import com.alrex.parcool.common.action.impl.Breakfall;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.parcool.EMFCompatParCoolMod;
import strm.emfcompat.parcool.compat.ParCoolHandsFull;

/** Prevents a requested landing roll while both hands carry an external load. */
@Mixin(Breakfall.class)
public abstract class ParCool4BreakfallMixin {

    @Shadow @Final
    private SynchronizedProperty<Breakfall.BreakfallType> propertyInputBreakfallType;

    @Inject(method = "onLand", at = @At("HEAD"), cancellable = true)
    private void emfcompat$requireFreeHandsForRoll(LivingFallEvent event, CallbackInfo ci) {
        ParCool4ActionAccessor action = (ParCool4ActionAccessor) this;
        if (EMFCompatParCoolMod.isEnabled()
                && propertyInputBreakfallType.get() == Breakfall.BreakfallType.ROLL
                && ParCoolHandsFull.handsFull(action.emfcompat$getParkourability().player())) {
            ci.cancel();
        }
    }
}

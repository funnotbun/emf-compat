package strm.emfcompat.parcool.mixin;

import com.alrex.parcool.client.animation.system.BlendingModelTransform;
import com.alrex.parcool.client.animation.system.PlayerAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import strm.emfcompat.parcool.compat.ParCool4PackTransforms;
import strm.emfcompat.parcool.compat.ParCoolPackVariables;

/** See {@link ParCool4BodyLeanMixin}: the torso transform without pack-played leans, for that one call. */
@Mixin(value = PlayerAnimator.class, remap = false)
public class ParCool4TransformMixin {

    @Inject(method = "getCurrentTransformation", at = @At("RETURN"), cancellable = true)
    private void emfcompat$bodyWithoutPackLean(CallbackInfoReturnable<BlendingModelTransform> cir) {
        if (!ParCoolPackVariables.settingUpRotations || cir.getReturnValue() == null) return;
        ParCool4PackTransforms processor = (ParCool4PackTransforms)
                (Object) ((ParCool4AnimatorAccessor) (Object) this).emfcompat$processor();
        cir.setReturnValue(processor.emfcompat$body(cir.getReturnValue()));
    }
}

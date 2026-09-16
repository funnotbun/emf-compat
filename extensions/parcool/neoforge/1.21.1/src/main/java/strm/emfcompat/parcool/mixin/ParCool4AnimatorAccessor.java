package strm.emfcompat.parcool.mixin;

import com.alrex.parcool.client.animation.system.AnimationProcessor;
import com.alrex.parcool.client.animation.system.PlayerAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** ParCool 4: the processor that holds a player's running animations. */
@Mixin(value = PlayerAnimator.class, remap = false)
public interface ParCool4AnimatorAccessor {

    @Accessor("animationProcessor")
    AnimationProcessor emfcompat$processor();
}

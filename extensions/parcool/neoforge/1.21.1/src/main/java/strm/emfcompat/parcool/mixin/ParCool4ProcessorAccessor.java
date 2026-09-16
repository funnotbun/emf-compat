package strm.emfcompat.parcool.mixin;

import com.alrex.parcool.client.animation.system.AnimationProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.ArrayList;

/** ParCool 4: the running animations, oldest first. */
@Mixin(value = AnimationProcessor.class, remap = false)
public interface ParCool4ProcessorAccessor {

    @Accessor("animators")
    ArrayList<?> emfcompat$animators();
}

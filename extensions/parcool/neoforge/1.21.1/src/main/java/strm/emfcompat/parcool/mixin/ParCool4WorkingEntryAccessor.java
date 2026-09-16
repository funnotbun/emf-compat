package strm.emfcompat.parcool.mixin;

import com.alrex.parcool.client.animation.system.WorkingAnimationSet;
import com.alrex.parcool.client.animation.system.registration.AnimationSets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** ParCool 4: which registered animation a running entry is, and its state. The record itself is private. */
@Mixin(targets = "com.alrex.parcool.client.animation.system.AnimationProcessor$WorkingAnimationEntry", remap = false)
public interface ParCool4WorkingEntryAccessor {

    @Accessor("registration")
    AnimationSets.Entry emfcompat$registration();

    @Accessor("animator")
    WorkingAnimationSet emfcompat$animator();
}

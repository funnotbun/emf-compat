package strm.emfcompat.parcool.mixin;

import com.alrex.parcool.api.action.Action;
import com.alrex.parcool.common.Parkourability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Internal access to the player owning a ParCool 4 action. */
@Mixin(Action.class)
public interface ParCool4ActionAccessor {

    @Accessor("parkourability")
    Parkourability emfcompat$getParkourability();
}

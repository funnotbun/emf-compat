package strm.emfcompat.parcool.mixin;

import com.alrex.parcool.common.action.impl.HangDown;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import strm.emfcompat.parcool.compat.ParCoolPackVariables;

/**
 * Moving along a bar, ParCool carries a hanging player at walking speed - two blocks a second. A
 * pack that animates the hang draws each hand letting go and swinging round to the next grip, and
 * at that speed the arms have to flail through a swing in a handful of ticks while the body and
 * legs sway at their own pace. Only while a pack animates the bar, the player moves along it at
 * {@link #EMFCOMPAT$SPEED} of that; without one ParCool is left as it is.
 */
@Mixin(value = HangDown.class, remap = false)
public class ParCool4BarSpeedMixin {

    @Unique
    private static final float EMFCOMPAT$SPEED = 0.55f;

    @Redirect(method = "lambda$onStartInLocalClient$0",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getSpeed()F", remap = false))
    private float emfcompat$slowerAlongBar(LocalPlayer player) {
        float speed = player.getSpeed();
        return ParCoolPackVariables.packAnimates(player, "bar") ? speed * EMFCOMPAT$SPEED : speed;
    }
}

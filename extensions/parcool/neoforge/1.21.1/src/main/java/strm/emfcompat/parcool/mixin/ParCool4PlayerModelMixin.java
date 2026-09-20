package strm.emfcompat.parcool.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.parcool.compat.ParCool4Poses;
import strm.emfcompat.parcool.compat.ParCoolHangHold;

/**
 * Capture path for ParCool 4, whose animation system poses the model from inside
 * {@code PlayerModel.setupAnim}: it redirects the {@code HumanoidModel.setupAnim} call and
 * applies its own transforms in place, so {@code setupAnim} still returns normally and this
 * RETURN injection at priority 2500 sees the finished ParCool pose.
 *
 * <p>ParCool 4 reports exactly which parts an animation drives and how far it has blended in,
 * so only those parts are captured, and they fade over the resource pack's animation at
 * ParCool's own pace.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
public class ParCool4PlayerModelMixin {

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$captureParCoolPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                              float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) return;
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        ParCool4Poses.capture(player, model);
        // A hang is ParCool's alone; this only claims the player so nothing else poses them.
        ParCoolHangHold.capture(player);
    }
}

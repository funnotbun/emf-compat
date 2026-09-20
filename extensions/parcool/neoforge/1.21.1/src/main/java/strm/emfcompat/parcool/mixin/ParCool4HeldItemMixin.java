package strm.emfcompat.parcool.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.parcool.compat.ParCoolHangHold;

/**
 * Takes whatever is in the hands out of sight for the length of a hang.
 *
 * <p>Muting the addons' poses gives the hang its arms back, but the item itself is not a pose: it
 * is drawn by this layer wherever the hands happen to be, so a map ends up as a sheet across the
 * player's face while they hang off a bar. Two hands on a bar are holding the bar, so nothing
 * should be in them.</p>
 *
 * <p>Only the render is cancelled - the item is still held, still selected, still usable the moment
 * the hang ends.</p>
 */
@Mixin(ItemInHandLayer.class)
public class ParCool4HeldItemMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void emfcompat$hideWhileHanging(PoseStack poseStack, MultiBufferSource buffer, int light,
                                            LivingEntity entity, float limbSwing, float limbSwingAmount,
                                            float partialTick, float ageInTicks, float netHeadYaw,
                                            float headPitch, CallbackInfo ci) {
        if (entity instanceof Player player && ParCoolHangHold.hidesHeldItems(player)) {
            ci.cancel();
        }
    }
}

package strm.emfcompat.parcool.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.parcool.compat.ParCoolHandIK;
import strm.emfcompat.parcool.compat.ParCoolPackVariables;

/**
 * Right before the model is animated: the rotations are set up, so this ends the window
 * {@link ParCool4BodyLeanMixin} opened - ParCool cancels {@code setupRotations}, so its TAIL never
 * runs - and the pose stack is exactly the model's space, which the hand IK needs.
 */
@Mixin(LivingEntityRenderer.class)
public class ParCool4ModelPoseMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void emfcompat$beforeAnimating(LivingEntity entity, float yaw, float partialTick, PoseStack stack,
                                           MultiBufferSource buffers, int light, CallbackInfo ci) {
        ParCoolPackVariables.settingUpRotations = false;
        if (entity instanceof AbstractClientPlayer player) {
            ParCoolHandIK.modelPose(player, stack.last().pose());
        }
    }
}

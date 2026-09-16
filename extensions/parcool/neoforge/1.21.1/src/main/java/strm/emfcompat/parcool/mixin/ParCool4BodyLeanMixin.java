package strm.emfcompat.parcool.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.parcool.compat.ParCoolPackVariables;

/**
 * ParCool leans the torso by rotating the whole pose stack in {@code setupRotations}, not through
 * the model, so a pack that animates a run or a charge itself would get ParCool's lean on top of
 * its own. Only there: for a hang the same transform turns the body to face the wall, and that
 * stays (see {@link ParCoolPackVariables.PackPlay}).
 * This marks the call; {@link ParCool4TransformMixin} then hands ParCool's renderer hook the torso
 * transform without the pack-played leans, eased the same way the limbs are. ParCool cancels the
 * method, so {@link ParCool4ModelPoseMixin} closes the window instead of a TAIL callback.
 *
 * <p>Priority below the default so this HEAD callback runs before ParCool's.</p>
 */
@Mixin(value = PlayerRenderer.class, priority = 900)
public class ParCool4BodyLeanMixin {

    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"))
    private void emfcompat$mark(AbstractClientPlayer player, PoseStack stack, float bob, float yRot,
                                float partialTick, float scale, CallbackInfo ci) {
        ParCoolPackVariables.settingUpRotations = true;
    }
}

package strm.emfcompat.immersivemelodies.mixin;

import net.minecraft.world.entity.monster.Vindicator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import strm.emfcompat.immersivemelodies.compat.IllagerArms;

/** The Vindicator's held-item layer draws only while it is aggressive; an instrument being played counts too. */
@Mixin(targets = "net.minecraft.client.renderer.entity.VindicatorRenderer$1")
public class VindicatorItemLayerMixin {

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/monster/Vindicator;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Vindicator;isAggressive()Z"))
    private boolean emfcompat$drawTheInstrument(Vindicator illager) {
        return illager.isAggressive() || IllagerArms.isPlaying(illager.getUUID());
    }
}

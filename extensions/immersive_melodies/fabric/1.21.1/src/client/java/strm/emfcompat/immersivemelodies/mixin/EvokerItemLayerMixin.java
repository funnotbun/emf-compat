package strm.emfcompat.immersivemelodies.mixin;

import net.minecraft.world.entity.monster.SpellcasterIllager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import strm.emfcompat.immersivemelodies.compat.IllagerArms;

/** The Evoker's held-item layer draws only while it casts a spell; an instrument being played counts too. */
@Mixin(targets = "net.minecraft.client.renderer.entity.EvokerRenderer$1")
public class EvokerItemLayerMixin {

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/monster/SpellcasterIllager;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/SpellcasterIllager;isCastingSpell()Z"))
    private boolean emfcompat$drawTheInstrument(SpellcasterIllager illager) {
        return illager.isCastingSpell() || IllagerArms.isPlaying(illager.getUUID());
    }
}

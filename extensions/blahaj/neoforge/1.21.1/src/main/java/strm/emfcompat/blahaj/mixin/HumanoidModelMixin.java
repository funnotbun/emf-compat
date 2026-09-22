package strm.emfcompat.blahaj.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.blahaj.EMFCompatBlahajMod;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;

/**
 * Captures the final Blåhaj cuddle pose after vanilla and Blåhaj have finished setting up the
 * humanoid model. Core restores the arms after EMF applies the resource-pack animation.
 */
@Mixin(value = HumanoidModel.class, priority = 2500)
public class HumanoidModelMixin {

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$captureBlahajPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                              float ageInTicks, float netHeadYaw, float headPitch,
                                              CallbackInfo ci) {
        if (!(entity instanceof Player player)) {
            return;
        }

        if (!EMFCompatBlahajMod.isEnabled()
                || EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())
                || !EMFCompatBlahajMod.isHoldingPlushie(player)) {
            PoseManager.clearPoses(player.getUUID(), EMFCompatBlahajMod.SOURCE);
            return;
        }

        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        PoseManager.savePoses(
                player.getUUID(),
                EMFCompatBlahajMod.SOURCE,
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm));
    }
}

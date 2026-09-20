package strm.emfcompat.carryon.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.carryon.CarryOnPoseCapture;
import strm.emfcompat.carryon.EMFCarryOnMod;
import strm.emfcompat.carryon.compat.CarryOnCompat;
import strm.emfcompat.core.PoseSnapshot;

/**
 * Recovers Carry On's native arm pose when another PlayerModel animation bypasses
 * {@link HumanoidModel#setupAnim}.
 *
 * <p>ParCool's overwriting path resets the model and intentionally does not call the superclass.
 * Carry On's own arm-pose mixin therefore never runs. Rather than duplicating Carry On's pose
 * formula, this bridge snapshots the completed PlayerModel, calls the real superclass method once
 * (which runs Carry On's own mixin and our normal capture), then restores the completed model.
 * The only lasting result is the exact {@code carry_on} source saved in the core.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2400)
public abstract class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<T> {

    protected PlayerModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim", at = @At("HEAD"))
    private void emfcompat$beginCarryOnCapture(T entity, float limbSwing, float limbSwingAmount,
                                               float ageInTicks, float netHeadYaw, float headPitch,
                                               CallbackInfo ci) {
        if (entity instanceof Player player) {
            CarryOnPoseCapture.begin(player.getUUID());
        }
    }

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$recoverSkippedCarryOnPose(T entity, float limbSwing, float limbSwingAmount,
                                                     float ageInTicks, float netHeadYaw, float headPitch,
                                                     CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;

        try {
            if (CarryOnPoseCapture.humanoidSetupRan(player.getUUID())
                    || !EMFCarryOnMod.isEnabled()
                    || !CarryOnCompat.shouldRenderCarryPose(player)) {
                return;
            }

            PlayerModel<?> model = (PlayerModel<?>) (Object) this;
            ModelPart[] parts = {
                    model.head, model.hat, model.body,
                    model.leftArm, model.rightArm, model.leftLeg, model.rightLeg,
                    model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants,
                    model.jacket
            };
            PoseSnapshot[] completedPose = new PoseSnapshot[parts.length];
            for (int i = 0; i < parts.length; i++) {
                completedPose[i] = new PoseSnapshot(parts[i]);
            }
            boolean completedCrouching = crouching;
            float completedSwimAmount = swimAmount;

            try {
                // Invokes HumanoidModel directly (invokespecial), not PlayerModel again. Carry
                // On's native mixin and our HumanoidModel capture both run on this call.
                super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            } finally {
                // The replay is capture-only. Never let it alter the model ParCool completed,
                // even if another mixin on HumanoidModel throws while the synthetic call runs.
                for (int i = 0; i < parts.length; i++) {
                    completedPose[i].apply(parts[i]);
                }
                crouching = completedCrouching;
                swimAmount = completedSwimAmount;
            }
        } finally {
            CarryOnPoseCapture.end(player.getUUID());
        }
    }
}

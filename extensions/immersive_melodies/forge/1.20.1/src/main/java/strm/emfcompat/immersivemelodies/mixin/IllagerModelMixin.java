package strm.emfcompat.immersivemelodies.mixin;

import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.monster.AbstractIllager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.immersivemelodies.ImmersiveMelodiesEMFCompat;
import strm.emfcompat.immersivemelodies.compat.IllagerArms;
import strm.emfcompat.immersivemelodies.compat.ImmersiveMelodiesCompat;

/** Unfolds an illager's arms while it plays, so the instrument is held. See {@link IllagerArms}. */
@Mixin(IllagerModel.class)
public class IllagerModelMixin<T extends AbstractIllager> {

    @Shadow @Final private ModelPart arms;
    @Shadow @Final private ModelPart leftArm;
    @Shadow @Final private ModelPart rightArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/monster/AbstractIllager;FFFFF)V", at = @At("TAIL"))
    private void emfcompat$uncrossWhilePlaying(T illager, float limbSwing, float limbSwingAmount,
                                               float ageInTicks, float netHeadYaw, float headPitch,
                                               CallbackInfo ci) {
        boolean playing = ImmersiveMelodiesEMFCompat.isEnabled() && ImmersiveMelodiesEMFCompat.isMobsEnabled()
                && ImmersiveMelodiesCompat.hasInstrument(illager);
        IllagerArms.setPlaying(illager.getUUID(), playing);
        if (playing) {
            IllagerArms.uncross(arms, leftArm, rightArm);
        }
    }
}

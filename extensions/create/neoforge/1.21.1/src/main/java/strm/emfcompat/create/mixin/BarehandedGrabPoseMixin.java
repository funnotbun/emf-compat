package strm.emfcompat.create.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.create.BarehandedCompat;
import strm.emfcompat.create.EMFCompatCreateMod;

import java.util.UUID;

/**
 * Captures the arm pose Barehanded puts on a player holding a structure in their bare hands, so
 * the resource pack's animation does not wipe it.
 *
 * <p>Barehanded applies the pose from its own mixin on {@code PlayerModel.setupAnim} at TAIL with
 * priority 2000, so this runs at 2500 to capture what it left behind.</p>
 *
 * <p>Third person only. Barehanded draws the first-person arms itself, through its own
 * {@code ItemInHandRenderer} mixin and render state, and that path never goes through the model
 * this captures from — so there is nothing here to restore in first person, and reaching into it
 * would only risk fighting Barehanded's own hand rendering.</p>
 *
 * <p>Rotation only, with no body-follow base: Barehanded writes nothing but xRot/yRot/zRot on the
 * arms and sleeves, so restoring rotation is exactly as much as it set. Sleeves follow their
 * parent arms in the core restore.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
public class BarehandedGrabPoseMixin {

    @Unique
    private static final String SOURCE = "barehanded_grab";

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$captureBarehandedGrabPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                     float ageInTicks, float netHeadYaw, float headPitch,
                                                     CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;

        UUID uuid = player.getUUID();
        if (EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) {
            PoseManager.clearPoses(uuid, SOURCE);
            return;
        }

        if (!EMFCompatCreateMod.isEnabled()
                || !EMFCompatCreateMod.isBarehanded()
                || !BarehandedCompat.isGrabbing(player)) {
            PoseManager.clearPoses(uuid, SOURCE);
            return;
        }

        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        PoseManager.savePoses(uuid, SOURCE,
                new PoseSnapshot(model.leftArm, true),
                new PoseSnapshot(model.rightArm, true));
    }
}

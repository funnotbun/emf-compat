package strm.emfcompat.immersivemelodies.mixin;

import immersive_melodies.client.animation.accessors.ModelAccessor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.immersivemelodies.ImmersiveMelodiesEMFCompat;
import strm.emfcompat.immersivemelodies.compat.ImmersiveMelodiesCompat;

import java.util.Optional;
import java.util.UUID;

/**
 * Captures the arm poses set by Immersive Melodies after it has animated a model.
 * The Core later restores these poses after EMF applies its resource-pack animation,
 * so instrument-playing arm poses are not overwritten by Entity Model Features.
 *
 * <p>Immersive Melodies poses mobs through the same call — its own mixins on the humanoid,
 * zombie, illager and piglin models end in {@code setAngles} — so a zombie or a pillager playing
 * an instrument is captured exactly like a player. The Core restores by UUID and does not care
 * what kind of entity it is.</p>
 */
@Mixin(immersive_melodies.client.animation.EntityModelAnimator.class)
public class EntityModelAnimatorMixin {

    private static final String SOURCE = "immersive_melodies";

    @Inject(method = "setAngles", at = @At("RETURN"), remap = false)
    private static <T extends Entity> void emfcompat$onSetAnglesReturn(ModelAccessor<T> accessor, CallbackInfo ci) {
        if (!(accessor.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        UUID uuid = entity.getUUID();

        if (!ImmersiveMelodiesEMFCompat.isEnabled()
                || (!(entity instanceof Player) && !ImmersiveMelodiesEMFCompat.isMobsEnabled())) {
            PoseManager.clearPoses(uuid, SOURCE);
            return;
        }

        if (EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) {
            PoseManager.clearPoses(uuid, SOURCE);
            return;
        }

        if (!ImmersiveMelodiesCompat.hasInstrument(entity)) {
            PoseManager.clearPoses(uuid, SOURCE);
            return;
        }

        Optional<ModelPart> leftArm = accessor.getLeftArm();
        Optional<ModelPart> rightArm = accessor.getRightArm();
        if (leftArm.isEmpty() && rightArm.isEmpty()) {
            PoseManager.clearPoses(uuid, SOURCE);
            return;
        }

        PoseManager.savePoses(
                uuid,
                SOURCE,
                leftArm.map(PoseSnapshot::new).orElse(null),
                rightArm.map(PoseSnapshot::new).orElse(null)
        );
    }
}

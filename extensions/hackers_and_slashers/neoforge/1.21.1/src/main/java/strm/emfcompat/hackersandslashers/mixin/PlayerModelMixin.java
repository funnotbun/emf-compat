package strm.emfcompat.hackersandslashers.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.hackersandslashers.EMFCompatHnSMod;
import strm.emfcompat.hackersandslashers.compat.HnSCompat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Captures the pose Hackers 'n Slashers has put on the arms, so the core can restore it after EMF
 * has animated the model from the resource pack.
 *
 * <p>Note which {@code setupAnim} this targets. {@code PlayerModel} has two: the real
 * {@code setupAnim(LivingEntity, ...)} it inherits from {@code HumanoidModel}, and the synthetic
 * bridge {@code setupAnim(Entity, ...)} that {@code EntityModel} declares and the renderer
 * actually calls. zigythebird's animation library — the one Hackers 'n Slashers drives — applies
 * its result at RETURN of the <em>bridge</em>, which runs after the real method has already
 * returned. Capturing at the real method's RETURN, the way the Better Combat addon does, therefore
 * reads the model before any of this mod's animation has been applied, and pins whatever the pose
 * was beforehand. Better Combat gets away with it because kosmx's library, which it uses, injects
 * into the real method instead.</p>
 *
 * <p>So this targets the bridge, at priority 2500 against the library's 2001 so it runs after it
 * at the same instruction. The descriptor is spelled out rather than left as a bare name, so the
 * selector can only mean the bridge and never the real method.</p>
 *
 * <p>The pose capture remains third-person only. First person is handled separately by the EMF
 * vanilla-model condition registered by {@link EMFCompatHnSMod}: it exposes H&amp;S' own
 * first-person animation and item transforms instead of copying the third-person snapshot.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
@SuppressWarnings("unchecked")
public class PlayerModelMixin {

    @Unique
    private static final String SOURCE = EMFCompatHnSMod.SOURCE;

    @Unique
    private static final String POSE_SOURCE = EMFCompatHnSMod.POSE_SOURCE;

    /** Below this limb-swing amount the player counts as stationary, and the legs may be held. */
    @Unique
    private static final float LEG_MOVE_THRESHOLD = 0.15f;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", at = @At("RETURN"))
    private void emfcompat$captureHnSPose(Entity entity, float limbSwing, float limbSwingAmount,
                                          float ageInTicks, float netHeadYaw, float headPitch,
                                          CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        UUID uuid = player.getUUID();

        if (!EMFCompatHnSMod.isEnabled()
                || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)
                || (EMFCompatHnSMod.isLocalPlayerInFirstPerson(player)
                    && HnSCompat.isFirstPersonAnimationActive(player))) {
            PoseManager.clearPoses(uuid, SOURCE);
            PoseManager.clearPoses(uuid, POSE_SOURCE);
            return;
        }

        PlayerModel<AbstractClientPlayer> model = (PlayerModel<AbstractClientPlayer>) (Object) this;

        // Body-follow: arm poses keep their shape and follow the torso (bodyBase = where the body
        // was at capture). Rotation-only (legacy): no bodyBase, so the arms keep only rotation.
        Vector3f bodyBase = EMFCompatHnSMod.isBodyFollow()
                ? new Vector3f(model.body.x, model.body.y, model.body.z)
                : null;

        emfcompat$captureStance(model, uuid, bodyBase, player);

        if (!HnSCompat.isActionActive(player)) {
            PoseManager.clearPoses(uuid, SOURCE);
            return;
        }

        // Hold the legs as well, so a lunge or a roll keeps its stance instead of walking through
        // it. Two safeguards, both borrowed from the Better Combat addon: rotation-only, which
        // keeps the legs pivoted at the hip rather than detaching them, and only while roughly
        // stationary, so a moving player keeps EMF's walk cycle.
        Map<String, PoseSnapshot> parts = null;
        if (EMFCompatHnSMod.isActionLegs() && limbSwingAmount < LEG_MOVE_THRESHOLD) {
            parts = new HashMap<>();
            parts.put("left_leg", new PoseSnapshot(model.leftLeg, true));
            parts.put("right_leg", new PoseSnapshot(model.rightLeg, true));
        }

        PoseManager.savePoses(
                uuid, SOURCE,
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm),
                parts,
                bodyBase
        );
    }

    @Unique
    private static void emfcompat$captureStance(PlayerModel<AbstractClientPlayer> model, UUID uuid,
                                                Vector3f bodyBase, AbstractClientPlayer player) {
        if (!EMFCompatHnSMod.isStances() || !HnSCompat.isStanceActive(player)) {
            PoseManager.clearPoses(uuid, POSE_SOURCE);
            return;
        }

        // Arms only: the player walks around in this pose, so the legs have to keep EMF's cycle.
        PoseManager.savePoses(
                uuid, POSE_SOURCE,
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm),
                null,
                bodyBase
        );
    }
}

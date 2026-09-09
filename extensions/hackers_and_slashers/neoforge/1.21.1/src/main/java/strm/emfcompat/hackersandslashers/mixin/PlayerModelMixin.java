package strm.emfcompat.hackersandslashers.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
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
 * <p>The animation library applies its own result from a mixin on {@code PlayerModel.setupAnim} at
 * priority 2001, so this runs at 2500 to read what it left behind — the same ordering the Better
 * Combat addon uses.</p>
 *
 * <p>Third person only. Hackers 'n Slashers keeps a separate first-person pose layer and its own
 * item-in-hand renderer mixins, so the first-person arms never come from the model captured here;
 * reaching into them would only fight the mod's own rendering.</p>
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

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$captureHnSPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                          float ageInTicks, float netHeadYaw, float headPitch,
                                          CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        UUID uuid = player.getUUID();

        if (!EMFCompatHnSMod.isEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) {
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

package strm.createFlyEmfCompat.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.CreateFlyEmfCompatClient;
import strm.createFlyEmfCompat.compat.SkyhookHelper;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Captures Create Fly's skyhook hang pose so EMF can keep animating the player.
 *
 * <p>This replaces the old approach of pausing EMF and forcing the vanilla model for anyone on a
 * skyhook. A pause stops the pack animation outright — including everything driving the face — so
 * a hanging player had a frozen expression, and forcing the vanilla model threw away the pack's
 * model entirely. Capturing the pose and letting the core put it back after EMF runs keeps both:
 * the hang pose is exact, and the pack animates everything it otherwise would.</p>
 *
 * <p>Create Fly poses the model from its own mixin on {@code PlayerModel.setupAnim} at TAIL with
 * default priority, so this runs at 2500 to capture what it left behind. Injecting into Create
 * Fly's {@code afterSetupAnim} directly is not an option here: it is pulled in unremapped
 * ({@code compileOnly}, see the build script), and its signature carries Minecraft types that
 * would not line up.</p>
 *
 * <p>The whole body is captured, head included, because {@code setHangingPose} poses all of it.
 * Hat, sleeves and jacket follow their parent parts in the core restore.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
public class PlayerModelSkyhookMixin {

    @Unique
    private static final String CREATE_EMF_COMPAT$SOURCE = "create_skyhook";

    @Inject(
            method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("RETURN")
    )
    private void createEmfCompat$captureSkyhookPose(AvatarRenderState state, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(state.id);
        if (!(entity instanceof AbstractClientPlayer player)) return;

        UUID uuid = player.getUUID();
        if (!CreateFlyEmfCompatClient.isEnabled() || !SkyhookHelper.isSkyhooking(uuid)) {
            PoseManager.clearPoses(uuid, CREATE_EMF_COMPAT$SOURCE);
            return;
        }

        PlayerModel model = (PlayerModel) (Object) this;

        Map<String, PoseSnapshot> parts = new HashMap<>();
        parts.put("head", new PoseSnapshot(model.head));
        parts.put("body", new PoseSnapshot(model.body));
        parts.put("left_arm", new PoseSnapshot(model.leftArm));
        parts.put("right_arm", new PoseSnapshot(model.rightArm));
        parts.put("left_leg", new PoseSnapshot(model.leftLeg));
        parts.put("right_leg", new PoseSnapshot(model.rightLeg));

        PoseManager.savePoses(uuid, CREATE_EMF_COMPAT$SOURCE, null, null, parts);
    }
}

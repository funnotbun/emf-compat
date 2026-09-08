package strm.emfcompat.horsesync.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.horsesync.EMFHorseSyncClient;
import strm.emfcompat.horsesync.compat.EMFCompat;

/**
 * Lifts a mounted player by however far the pack animation moved the horse's body down.
 *
 * <p>The NeoForge module gets this from {@code RenderPlayerEvent.Pre} and {@code .Post}, which
 * Fabric has no equivalent of, so the same translate is bracketed around
 * {@code PlayerRenderer.render} here instead — HEAD for the push, RETURN for the matching undo.
 * The renderer's own pushPose/popPose sit inside that range, so the transform has to be taken
 * back explicitly, exactly as the Post event does.</p>
 */
@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    private static float horsesync$offsetFor(AbstractClientPlayer player) {
        if (!EMFHorseSyncClient.isEnabled()) return 0.0f;
        if (!(player.getVehicle() instanceof AbstractHorse horse)) return 0.0f;

        Float offset = EMFCompat.horseBodyOffsets.get(horse.getUUID());
        if (offset == null) return 0.0f;

        // Invert and clamp: move the player up when the horse body goes down in model space
        // (which translates to the body going up in world space after scale(-1, -1, 1)).
        float applied = -offset;
        return applied > 0.0f ? applied : 0.0f;
    }

    @Inject(
            method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD")
    )
    private void horsesync$raiseMountedPlayer(AbstractClientPlayer player, float entityYaw, float partialTicks,
                                              PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                              CallbackInfo ci) {
        float applied = horsesync$offsetFor(player);
        if (applied > 0.0f) poseStack.translate(0.0, applied, 0.0);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN")
    )
    private void horsesync$undoMountedPlayerRaise(AbstractClientPlayer player, float entityYaw, float partialTicks,
                                                  PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                  CallbackInfo ci) {
        float applied = horsesync$offsetFor(player);
        if (applied > 0.0f) poseStack.translate(0.0, -applied, 0.0);
    }
}

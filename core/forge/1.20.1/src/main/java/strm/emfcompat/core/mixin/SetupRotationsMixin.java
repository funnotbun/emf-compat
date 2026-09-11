package strm.emfcompat.core.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.CrouchNormalizer;

/**
 * Corrects how far {@code setupRotations} moves the whole render of a crouching player. The
 * correction itself lives in {@link CrouchNormalizer}.
 *
 * <p>Animation libraries do not only pose the model's parts. kosmx's — the one this version has —
 * also translates, rotates and scales the entire {@link PoseStack} from its {@code body} bone, in
 * {@code PlayerRendererMixin.applyBodyTransforms}, inside {@code setupRotations}. Hackers 'n Slashers' sneak poses move that bone by -4, which drops the
 * whole player a quarter of a block on top of vanilla's own crouch offset. None of it shows up on
 * the model's parts, which is why a part-level probe saw nothing move.</p>
 *
 * <p>Taken around the call rather than inside the method, so that everything injected into it is
 * counted whichever order the libraries' mixins were applied in. The delta is
 * {@code before⁻¹ · after}: the translation that remains is what the method added, in the frame
 * the entity was placed in, whatever rotation the camera had already put on the stack.</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class SetupRotationsMixin {

    @Unique
    private static final Matrix4f EMFCOMPAT$BEFORE = new Matrix4f();

    @Unique
    private static CrouchNormalizer.Mode emfcompat$normalise = CrouchNormalizer.Mode.NONE;

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V"
            )
    )
    private void emfcompat$beforeSetupRotations(LivingEntity entity, float yaw, float partialTicks,
                                                PoseStack poseStack, MultiBufferSource buffers,
                                                int light, CallbackInfo ci) {
        emfcompat$normalise = CrouchNormalizer.appliesTo(entity);
        if (emfcompat$normalise != CrouchNormalizer.Mode.NONE) {
            EMFCOMPAT$BEFORE.set(poseStack.last().pose());
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void emfcompat$afterSetupRotations(LivingEntity entity, float yaw, float partialTicks,
                                               PoseStack poseStack, MultiBufferSource buffers,
                                               int light, CallbackInfo ci) {
        if (emfcompat$normalise == CrouchNormalizer.Mode.NONE) {
            return;
        }
        CrouchNormalizer.Mode mode = emfcompat$normalise;
        emfcompat$normalise = CrouchNormalizer.Mode.NONE;
        CrouchNormalizer.normalise(EMFCOMPAT$BEFORE, poseStack.last().pose(), mode);
    }
}

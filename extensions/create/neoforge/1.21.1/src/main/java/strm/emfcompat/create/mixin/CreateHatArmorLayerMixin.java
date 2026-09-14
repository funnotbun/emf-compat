package strm.emfcompat.create.mixin;

import com.simibubi.create.content.equipment.hats.CreateHatArmorLayer;
import com.simibubi.create.content.trains.schedule.hat.TrainHatInfo;
import net.minecraft.client.model.geom.ModelPart;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import strm.emfcompat.create.HatGeometry;

import java.util.List;

/**
 * Puts Create's hat back on top of the head under a resource pack's model.
 *
 * <p>The layer decides where the hat goes from the head part's box, and skips the whole placement
 * when the part has no cubes — which is exactly what EMF leaves behind for a part a pack replaced.
 * Both reads are sent to {@link HatGeometry#measured} instead, so the layer measures the vanilla
 * geometry and the hat lands where it does without the pack. Nothing else changes: the chain of
 * {@code translateAndRotate} calls that got us to the head is untouched, so the hat still follows
 * the pack's animation.</p>
 */
@Mixin(value = CreateHatArmorLayer.class, remap = false)
public class CreateHatArmorLayerMixin {

    /**
     * The chain of parts the layer walks down to the head. A pack may leave that part unused and draw
     * the head somewhere else entirely — Fresh Animations hangs an animal's head off {@code body} —
     * and then the hat has to follow it there instead of hanging at a pivot nothing uses any more.
     */
    @Redirect(method = "render", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/trains/schedule/hat/TrainHatInfo;getAdjustedPart"))
    private static List<ModelPart> emfcompatCreate$followTheMovedPart(
            TrainHatInfo info, ModelPart root, String defaultPart) {
        return HatGeometry.chain(info.part(), root, defaultPart);
    }

    @Redirect(method = "render", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;isEmpty()Z", remap = true))
    private boolean emfcompatCreate$measureTheReplacedPart(ModelPart part) {
        return HatGeometry.measured(part).isEmpty();
    }

    @Redirect(method = "render", remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/client/model/geom/ModelPart;cubes:Ljava/util/List;",
                    remap = true))
    private List<ModelPart.Cube> emfcompatCreate$cubesOfTheReplacedPart(ModelPart part) {
        return HatGeometry.cubes(HatGeometry.measured(part));
    }
}

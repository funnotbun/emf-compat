package strm.emfcompat.hackersandslashers.mixin;

import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.hackersandslashers.EMFCompatHnSMod;

/**
 * Keeps Hackers 'n Slashers in charge of its first-person combat render when First Person Model
 * is installed.
 *
 * <p>H&amp;S normally changes attacks and blocks to {@link FirstPersonMode#DISABLED} merely because
 * the First Person Model mod is present, expecting that mod's body render to show the animation.
 * The addon instead suppresses that body through FPM's activation API to avoid duplicate arms, so
 * the matching H&amp;S layers must retain the same mode they use when FPM is absent.</p>
 *
 * <p>The four arguments are H&amp;S' action, parry, charge and parkour classifications. This is the
 * mod's own no-FPM decision table: ordinary attacks, parries and charges get the full model;
 * parkour moves and non-charge actions such as bash keep their deliberately disabled view.</p>
 */
@Mixin(targets = "net.dndats.api.animations.PlayerAnimator", remap = false)
public class PlayerAnimatorMixin {

    @Inject(method = "determineFirstPersonMode", at = @At("RETURN"), cancellable = true)
    private static void emfcompat$keepHnSFirstPersonModel(boolean action, boolean parry,
                                                          boolean charge, boolean parkour,
                                                          CallbackInfoReturnable<FirstPersonMode> cir) {
        if (!EMFCompatHnSMod.isEnabled() || parkour) {
            return;
        }
        if (!action || charge) {
            cir.setReturnValue(FirstPersonMode.THIRD_PERSON_MODEL);
        }
    }
}

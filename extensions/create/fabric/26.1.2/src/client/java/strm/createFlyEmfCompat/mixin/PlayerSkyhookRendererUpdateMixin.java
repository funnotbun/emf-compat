package strm.createFlyEmfCompat.mixin;

import com.zurrtum.create.client.foundation.render.PlayerSkyhookRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;

import java.util.Collection;
import java.util.UUID;

/**
 * Drops captured skyhook poses for players who just let go, without waiting for the core's
 * periodic sweep. Mirrors the NeoForge Create addon.
 *
 * <p>Safe to inject into even though Create Fly is an unremapped {@code compileOnly} dependency:
 * this method's signature is plain Java, with no Minecraft types that would need mapping.</p>
 */
@Mixin(value = PlayerSkyhookRenderer.class, remap = false)
public class PlayerSkyhookRendererUpdateMixin {

    @Inject(method = "updatePlayerList", at = @At("RETURN"), remap = false)
    private static void createEmfCompat$dropPosesForPlayersNoLongerHanging(
            Collection<UUID> uuids, CallbackInfo ci) {
        PoseManager.retainOnly(uuids, "create_skyhook");
    }
}

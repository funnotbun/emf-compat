package strm.mctest;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("mctest_driver")
public final class MctestDriverMod {

    public MctestDriverMod() {
        if (!Driver.enabled() || FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                Driver.tick();
            }
        });
    }
}

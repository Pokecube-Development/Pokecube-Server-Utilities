package pokecube.server;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import pokecube.server.compat.Compat;

@Mod("pokecubeserverutils")
public class PokecubeServerUtils
{
    public PokecubeServerUtils(IEventBus modBus)
    {
        modBus.addListener(this::onSetup);
    }

    public void onSetup(FMLCommonSetupEvent event)
    {
        Compat.init();
    }
}

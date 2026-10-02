package pokecube.server.compat;

import pokecube.legends.handlers.ForgeEventHandlers;
import pokecube.server.compat.bluemap.BlueMapCompat;
import pokecube.server.compat.thutessentials.ThutEssentialsCompat;
import pokecube.server.compat.xaeros.XaerosCompat;

public class Compat
{
    public static void init()
    {
        // Sub modules
        XaerosCompat.init();
        BlueMapCompat.init();
        ThutEssentialsCompat.init();

        // Main Pokecube Set
        arceusPokecentersGyms();
    }

    private static void arceusPokecentersGyms()
    {
        // TODO make this use a configurable list.
        ForgeEventHandlers.ARCEUS_PROTECTED.add(check -> {
            for (var p : check.volume().getParts())
            {
                if (p.getBounds().isInside(check.pos()))
                {
                    var name = p.getName();
                    if (name.contains("pokecenter::")) return true;
                    if (name.contains("gym::")) return true;
                    if (name.contains("/gym/")) return true;
                    if (name.contains("/pokecenter/")) return true;
                    if (name.contains("pokecube_legends:temples/surface/elite_four"))
                        return true;
                    if (name.contains("pokecube:scattered/pokecenter_start/building"))
                        return true;
                }
            }
            return false;
        });
    }
}

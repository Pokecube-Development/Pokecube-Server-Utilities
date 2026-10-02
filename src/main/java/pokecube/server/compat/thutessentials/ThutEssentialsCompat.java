package pokecube.server.compat.thutessentials;

import net.neoforged.fml.ModList;

public class ThutEssentialsCompat
{
    public static void init()
    {
        if (ModList.get().isLoaded("thutessentials")) Compat.init();
    }
}

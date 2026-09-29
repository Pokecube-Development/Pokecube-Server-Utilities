package pokecube.server.compat;

import pokecube.server.compat.bluemap.BlueMapCompat;
import pokecube.server.compat.thutessentials.ThutEssentialsCompat;
import pokecube.server.compat.xaeros.XaerosCompat;

public class Compat
{
    public static void init()
    {
        XaerosCompat.init();
        BlueMapCompat.init();
        ThutEssentialsCompat.init();
    }
}

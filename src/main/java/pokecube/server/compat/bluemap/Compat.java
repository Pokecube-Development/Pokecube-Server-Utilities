package pokecube.server.compat.bluemap;

import de.bluecolored.bluemap.api.markers.MarkerSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Compat
{
    static Map<ResourceKey<Level>, MarkerSet> SETS = new ConcurrentHashMap<>();

    public static void init()
    {
    }
}

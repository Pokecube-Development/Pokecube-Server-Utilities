package pokecube.server.compat.thutessentials;

import pokecube.adventures.entity.trainer.TrainerNpc;
import pokecube.core.entity.npc.NpcMob;
import thut.essentials.land.LandEventsHandler;

public class Compat
{
    public static void init()
    {
        // Auto-protect all non trainer pokecube npcs (merchants/healers) and gym leaders
        LandEventsHandler.EntityEventHandler.IS_PROTECTED.add(
                (in, land_owner) -> (in instanceof NpcMob && !(in instanceof TrainerNpc)));
    }
}

package pokecube.serverutils.ai.pokemob;

import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import pokecube.core.ai.thread.aiRunnables.combat.AIAttack;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.capabilities.CapabilityPokemob;
import thut.api.entity.ai.IAIRunnable;

public class AITurnAttack extends AIAttack
{
    public boolean hasOrders       = false;
    public boolean executingOrders = false;

    public AITurnAttack(IPokemob par1MobEntity)
    {
        super(par1MobEntity);
    }

    @Override
    public void doMainThreadTick(World world)
    {
        LivingEntity target = attacker.getAttackTarget();
        AITurnAttack task = null;
        IPokemob targetMob = CapabilityPokemob.getPokemobFor(target);
        task:
        if (targetMob != null && targetMob.getAI() != null)
        {
            for (IAIRunnable ai : targetMob.getAI().aiTasks)
            {
                if (ai instanceof AITurnAttack)
                {
                    task = (AITurnAttack) ai;
                    break;
                }
            }
            if (task == null) break task;
            if (!pokemob.isPlayerOwned()) hasOrders = task.hasOrders;
            boolean bothOrder = hasOrders && task.hasOrders;

            if (!bothOrder && !executingOrders)
            {
                pokemob.setAttackCooldown(20);
            }
            else if (hasOrders && task.hasOrders)
            {
                executingOrders = true;
                hasOrders = false;
                pokemob.setAttackCooldown(0);
                task.executingOrders = true;
                task.hasOrders = false;
            }
            if (executingOrders)
            {
                pokemob.setAttackCooldown(0);
            }
            else
            {
                pokemob.setAttackCooldown(20);
            }
        }
        super.doMainThreadTick(world);
    }

}

package pokecube.serverutils;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pokecube.core.PokecubeCore;
import pokecube.core.ai.thread.aiRunnables.combat.AIAttack;
import pokecube.core.events.CommandAttackEvent;
import pokecube.core.events.InitAIEvent;
import pokecube.core.events.MoveUse;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.capabilities.CapabilityPokemob;
import pokecube.core.interfaces.pokemob.ai.CombatStates;
import pokecube.serverutils.ai.pokemob.AITurnAttack;
import thut.api.entity.ai.IAIRunnable;

public class TurnBasedManager
{

    public TurnBasedManager()
    {
    }

    public void enable()
    {
        MinecraftForge.EVENT_BUS.register(this);
        PokecubeCore.MOVE_BUS.register(this);
    }

    public void disable()
    {
        MinecraftForge.EVENT_BUS.unregister(this);
        PokecubeCore.MOVE_BUS.unregister(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightclick(PlayerInteractEvent.RightClickItem event)
    {

    }

    @SubscribeEvent
    public void onAttackCommand(CommandAttackEvent event)
    {
        if (!PokeServerUtils.config.turnbased) return;
        boolean angry = event.getPokemob().getCombatState(CombatStates.ANGRY)
                || event.getPokemob().getEntity().getAttackTarget() != null;
        if (!angry) return;
        for (IAIRunnable ai : event.getPokemob().getAI().aiTasks)
        {
            if (ai instanceof AITurnAttack)
            {
                AITurnAttack task = (AITurnAttack) ai;
                if (!task.executingOrders) task.hasOrders = true;
                return;
            }
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onAIInit(InitAIEvent event)
    {
        if (!PokeServerUtils.config.turnbased || event.getPokemob().getAI() == null) return;
        AITurnAttack attack = new AITurnAttack(event.getPokemob());
        for (IAIRunnable ai : event.getPokemob().getAI().aiTasks)
        {
            if (ai instanceof AIAttack)
            {
                AIAttack old = (AIAttack) ai;
                event.getPokemob().getAI().aiTasks.remove(old);
                attack.setMutex(old.getMutex());
                attack.setPriority(old.getPriority());
                event.getPokemob().getAI().aiTasks.add(0, attack);
                break;
            }
        }
    }

    @SubscribeEvent
    public void onAttackUse(MoveUse.ActualMoveUse.Init event)
    {
        IPokemob target = CapabilityPokemob.getPokemobFor(event.getTarget());
        if (!PokeServerUtils.config.turnbased || target == null) return;
        if (event.getUser().getCombatState(CombatStates.NOITEMUSE))
        {
            event.setCanceled(true);
            event.getUser().setCombatState(CombatStates.NOITEMUSE, false);
        }
        for (IAIRunnable ai : event.getUser().getAI().aiTasks)
        {
            if (ai instanceof AITurnAttack)
            {
                AITurnAttack task = (AITurnAttack) ai;
                task.hasOrders = false;
                task.executingOrders = false;
                break;
            }
        }
    }

}

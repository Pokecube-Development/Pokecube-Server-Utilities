package pokecube.serverutils;

import java.util.List;

import com.google.common.collect.Lists;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pokecube.core.ai.thread.aiRunnables.combat.AIAttack;
import pokecube.core.events.OngoingTickEvent;
import pokecube.core.events.pokemob.InitAIEvent;
import pokecube.core.events.pokemob.combat.CommandAttackEvent;
import pokecube.core.events.pokemob.combat.MoveUse;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.PokecubeMod;
import pokecube.core.interfaces.capabilities.CapabilityAffected;
import pokecube.core.interfaces.capabilities.CapabilityPokemob;
import pokecube.core.interfaces.entity.IOngoingAffected;
import pokecube.core.interfaces.entity.IOngoingAffected.IOngoingEffect;
import pokecube.core.interfaces.pokemob.ai.CombatStates;
import pokecube.serverutils.ai.pokemob.AITurnAttack;
import thut.api.entity.ai.IAIRunnable;

public class TurnBasedManager
{
    private List<IOngoingEffect> processing = Lists.newArrayList();

    public TurnBasedManager()
    {
    }

    public void enable()
    {
        MinecraftForge.EVENT_BUS.register(this);
        PokecubeMod.MOVE_BUS.register(this);
    }

    public void disable()
    {
        MinecraftForge.EVENT_BUS.unregister(this);
        PokecubeMod.MOVE_BUS.unregister(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightclick(PlayerInteractEvent.RightClickItem event)
    {

    }

    @SubscribeEvent
    public void onStatusEffect(OngoingTickEvent event)
    {
        if (!PokeServerUtils.config.turnbased) return;
        // We deal with ticking these ourself in the turn based combat stuff.
        if (!processing.contains(event.effect))
        {
            event.setCanceled(true);
        }
        else processing.remove(event.effect);
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
        // Search for existing AIAttack
        for (IAIRunnable ai : event.getPokemob().getAI().aiTasks)
        {
            if (ai instanceof AIAttack)
            {
                // Replace the old attack AI with the turn based variant.
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
        if (!PokeServerUtils.config.turnbased) return;

        IPokemob target = CapabilityPokemob.getPokemobFor(event.getTarget());
        // Only apply this if the target is also a pokemob.
        if (target == null) return;
        // Clear the no item use
        if (event.getUser().getCombatState(CombatStates.NOITEMUSE))
        {
            event.setCanceled(true);
            event.getUser().setCombatState(CombatStates.NOITEMUSE, false);
        }
        // Reset task's orders
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
        // Tick status effects
        IOngoingAffected affected = CapabilityAffected.getAffected(event.getTarget());
        if (affected != null)
        {
            processing.addAll(affected.getEffects());
            affected.tick();
            processing.clear();
        }
    }
}

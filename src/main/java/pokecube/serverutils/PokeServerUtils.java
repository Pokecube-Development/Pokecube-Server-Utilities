package pokecube.serverutils;

import java.util.HashMap;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pokecube.core.PokecubeCore;
import pokecube.core.database.Database;
import pokecube.core.database.PokedexEntry;
import pokecube.core.events.CaptureEvent;
import pokecube.core.events.PostPostInit;
import pokecube.core.events.SpawnEvent.SendOut;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.PokecubeMod;
import pokecube.core.interfaces.capabilities.CapabilityPokemob;
import pokecube.core.items.pokecubes.EntityPokecube;
import thut.core.common.commands.CommandConfig;

@Mod(modid = PokeServerUtils.MODID, name = "Pokecube Server Utils", version = PokeServerUtils.VERSION, dependencies = "required-after:pokecube", acceptableRemoteVersions = "*", acceptedMinecraftVersions = PokeServerUtils.MCVERSIONS)
public class PokeServerUtils
{
    public static final String             MODID            = Reference.MODID;
    public static final String             VERSION          = Reference.VERSION;
    public final static String             MCVERSIONS       = "*";

    @Instance(value = MODID)
    public static PokeServerUtils          instance;
    public static Config                   config;
    public static TurnBasedManager         turnbasedManager = new TurnBasedManager();

    private HashMap<PokedexEntry, Integer> overrides        = Maps.newHashMap();
    Set<Integer>                           dimensionList    = Sets.newHashSet();

    public PokeServerUtils()
    {
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent e)
    {
        config = new Config(PokecubeCore.core.getPokecubeConfig(e).getConfigFile());
        MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    public void serverLoad(FMLServerStartingEvent event)
    {
        event.registerServerCommand(new CommandConfig("pokeutilssettings", config));
    }

    @SubscribeEvent
    public void canCapture(CaptureEvent.Pre evt)
    {
        int level = evt.caught.getLevel();
        boolean legendary = evt.caught.getPokedexEntry().legendary;
        int max = overrides.containsKey(evt.caught.getPokedexEntry()) ? overrides.get(evt.caught.getPokedexEntry())
                : legendary ? config.maxCaptureLevelLegendary : config.maxCaptureLevelNormal;
        if (level > max)
        {
            evt.setCanceled(true);
            evt.setResult(Result.DENY);
            Entity catcher = ((EntityPokecube) evt.pokecube).shootingEntity;
            if (catcher instanceof EntityPlayer)
            {
                ((EntityPlayer) catcher).sendMessage(new TextComponentTranslation("pokecube.denied"));
            }
            evt.pokecube.entityDropItem(((EntityPokecube) evt.pokecube).getItem(), (float) 0.5);
            evt.pokecube.setDead();
        }
    }

    @SubscribeEvent
    public void postpostInit(PostPostInit evt)
    {
        for (String s : config.maxCaptureLevelOverrides)
        {
            String[] args = s.split(":");
            try
            {
                int level = Integer.parseInt(args[1]);
                PokedexEntry entry = Database.getEntry(args[0]);
                if (entry == null)
                {
                    PokecubeMod.log(args[0] + " not found in database");
                }
                else
                {
                    overrides.put(entry, level);
                }
            }
            catch (Exception e)
            {
                PokecubeMod.log("Error with " + s);
                e.printStackTrace();
            }
        }
    }

    @SubscribeEvent
    public void mobTickEvent(LivingUpdateEvent event)
    {
        IPokemob pokemob = CapabilityPokemob.getPokemobFor(event.getEntityLiving());
        if (config.pokemobBlacklistenabled && pokemob != null)
        {
            PokedexEntry entry = pokemob.getPokedexEntry();
            for (String s : config.pokemobBlacklist)
            {
                if (entry == Database.getEntry(s))
                {
                    pokemob.returnToPokecube();
                    if (pokemob.getPokemonOwner() != null)
                    {
                        pokemob.getPokemonOwner().sendMessage(
                                new TextComponentString(TextFormatting.RED + "You are not allowed to use that."));
                    }
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public void onSendOut(SendOut.Pre evt)
    {
        if (config.pokemobBlacklistenabled)
        {
            PokedexEntry entry = evt.pokemob.getPokedexEntry();
            for (String s : config.pokemobBlacklist)
            {
                if (entry == Database.getEntry(s))
                {
                    evt.setCanceled(true);
                    if (evt.pokemob.getPokemonOwner() != null)
                    {
                        evt.pokemob.getPokemonOwner().sendMessage(
                                new TextComponentString(TextFormatting.RED + "You are not allowed to use that."));
                    }
                    break;
                }
            }
        }

        if (!config.dimsEnabled) return;
        int dim = evt.world.provider.getDimension();
        boolean inList = dimensionList.contains(dim);
        if (config.whitelist)
        {
            if (!inList)
            {
                evt.setCanceled(true);
            }
        }
        else if (config.blacklist)
        {
            if (inList)
            {
                evt.setCanceled(true);
            }
        }
    }
}

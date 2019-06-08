package pokecube.serverutils;

import java.util.HashMap;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import pokecube.core.database.Database;
import pokecube.core.database.PokedexEntry;
import pokecube.core.events.CaptureEvent;
import pokecube.core.events.PostPostInit;
import pokecube.core.events.SpawnEvent.SendOut;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.PokecubeMod;
import pokecube.core.interfaces.capabilities.CapabilityPokemob;
import pokecube.core.items.pokecubes.EntityPokecube;
import pokecube.serverutils.starters.LegacyStarterManager;
import pokecube.serverutils.starters.LegacyStarterManager.LegacyOptIn;
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
    public void preInit(FMLCommonSetupEvent e)
    {
        config = new Config(PokecubeMod.core.getPokecubeConfig(e).getConfigFile());
        MinecraftForge.EVENT_BUS.register(this);
        if (LegacyStarterManager.legacyStarterCount > 0) MinecraftForge.EVENT_BUS.register(new LegacyStarterManager());
    }

    @EventHandler
    public void serverLoad(FMLServerStartingEvent event)
    {
        event.registerServerCommand(new CommandConfig("pokeutilssettings", config));
        if (LegacyStarterManager.legacyStarterCount > 0) event.registerServerCommand(new LegacyOptIn());
    }

    @EventHandler
    public void serverLoad(FMLServerStartedEvent event)
    {
        if (config.cleanLegacies)
        {
            config.cleanLegacies = false;
            config.save();
            LegacyStarterManager.cleanLegacyFolder();
        }
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
            if (catcher instanceof PlayerEntity)
            {
                ((PlayerEntity) catcher).sendMessage(new TranslationTextComponent("pokecube.denied"));
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
        IPokemob pokemob = CapabilityPokemob.getPokemobFor(event.getMobEntity());
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
                                new StringTextComponent(TextFormatting.RED + "You are not allowed to use that."));
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
                                new StringTextComponent(TextFormatting.RED + "You are not allowed to use that."));
                    }
                    break;
                }
            }
        }

        if (!config.dimsEnabled) return;
        int dim = evt.world.dimension.getDimension();
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

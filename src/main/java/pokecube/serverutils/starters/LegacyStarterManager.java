package pokecube.serverutils.starters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Level;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.ClickEvent.Action;
import net.minecraft.world.World;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import pokecube.core.PokecubeItems;
import pokecube.core.database.PokedexEntry;
import pokecube.core.entity.pokemobs.genetics.GeneticsManager;
import pokecube.core.handlers.playerdata.PlayerPokemobCache;
import pokecube.core.interfaces.IPokecube;
import pokecube.core.interfaces.IPokecube.PokecubeBehavior;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.PokecubeMod;
import pokecube.core.interfaces.capabilities.CapabilityPokemob;
import pokecube.core.items.pokecubes.PokecubeManager;
import pokecube.core.utils.TagNames;
import pokecube.core.utils.Tools;
import thut.api.entity.genetics.IMobGenetics;

public class LegacyStarterManager
{
    private static Map<UUID, PlayerPokemobCache> cache = Maps.newHashMap();

    public static class LegacyOptIn extends CommandBase
    {

        @Override
        public String getName()
        {
            return "legacy_options";
        }

        @Override
        public int getRequiredPermissionLevel()
        {
            return 0;
        }

        @Override
        public String getUsage(ICommandSender sender)
        {
            return "/legacy_options pick <optional|page>";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
        {
            EntityPlayer player = getCommandSenderAsPlayer(sender);
            if (!cache.containsKey(player.getUniqueID())) throw new CommandException("You may not use this command.");
            if (args.length == 0) throw new CommandException(getUsage(sender));
            PlayerPokemobCache pokemobs = cache.get(player.getUniqueID());
            int legacies = player.getEntityData().getInteger("_legacy_starters_");
            if (legacies <= 0) throw new CommandException("You may not use this command.");
            switch (args[0])
            {
            case "pick":
                ITextComponent message = new TextComponentString("Pokemobs: ");
                sender.sendMessage(message);
                message = new TextComponentString("");

                List<ItemStack> choices = Lists.newArrayList();

                for (Entry<Integer, ItemStack> entry : pokemobs.cache.entrySet())
                {
                    Integer id = entry.getKey();
                    boolean wasDeleted = pokemobs.genesDeleted.contains(id);
                    if (wasDeleted) continue;
                    ItemStack stack = entry.getValue();
                    stack.getTagCompound().setInteger("_cache_uid_", id);
                    choices.add(stack);
                }

                int start = 0;
                int end = choices.size();

                Collections.sort(choices, new Comparator<ItemStack>()
                {
                    @Override
                    public int compare(ItemStack o1, ItemStack o2)
                    {
                        return o1.getDisplayName().compareTo(o2.getDisplayName());
                    }
                });

                if (end > 45)
                {
                    if (args.length == 1) throw new CommandException("Include page argument, too many mobs..");
                    int page = parseInt(args[1]) - 1;
                    page = Math.max(0, page);
                    int maxPages = choices.size() / 45;
                    page = Math.min(page, maxPages);
                    start = 45 * page;
                    end = Math.min(end, start + 45);
                    sender.sendMessage(new TextComponentString("Page " + (page + 1)));
                }

                for (int i = start; i < end; i++)
                {
                    ItemStack stack = choices.get(i);
                    String command;
                    Integer id = stack.getTagCompound().getInteger("_cache_uid_");
                    command = "/legacy_options restore " + id;
                    NBTTagCompound tag = stack.getTagCompound().copy();
                    tag.removeTag(TagNames.POKEMOB);
                    ItemStack copy = stack.copy();
                    copy.setTagCompound(tag);
                    tag = copy.writeToNBT(new NBTTagCompound());
                    ClickEvent click = new ClickEvent(Action.RUN_COMMAND, command);
                    ITextComponent sub = stack.getTextComponent();
                    sub.getStyle().setClickEvent(click);
                    sub.appendText(" ");
                    message.appendSibling(sub);
                    int size = message.toString().getBytes().length;
                    if (size > 32000)
                    {
                        sender.sendMessage(message);
                        message = new TextComponentString("");
                    }
                }
                sender.sendMessage(message);
                break;
            case "restore":
                int uid = Integer.parseInt(args[1]);

                if (!pokemobs.cache.containsKey(uid)) throw new CommandException("You have already picked this.");

                ItemStack stack = pokemobs.cache.remove(uid);
                pokemobs.inPC.remove(uid);
                pokemobs.genesDeleted.remove(uid);
                player.getEntityData().setInteger("_legacy_starters_", legacies - 1);

                IPokemob oldPokemob = PokecubeManager.itemToPokemob(stack, player.getEntityWorld());
                if (oldPokemob == null)
                {
                    saveData(player.getUniqueID());
                    PokecubeMod.log(Level.WARNING, "Error with restoring pokemob from stack");
                    PokecubeMod.log(Level.WARNING, "Stack: " + stack);
                    PokecubeMod.log(Level.WARNING, "Stack Tag: " + stack.getTagCompound());
                    return;
                }

                Entity oldEntity = oldPokemob.getEntity();
                PokedexEntry entry = oldPokemob.getPokedexEntry();

                Entity newEntity = PokecubeMod.core.createPokemob(entry, oldEntity.getEntityWorld());
                IPokemob newPokemob = CapabilityPokemob.getPokemobFor(newEntity);

                newEntity.setUniqueId(oldEntity.getUniqueID());

                // Sync tags besides the ones that define species and form.
                NBTTagCompound tag = oldPokemob.writePokemobData();
                tag.getCompoundTag(TagNames.OWNERSHIPTAG).removeTag(TagNames.POKEDEXNB);
                tag.getCompoundTag(TagNames.VISUALSTAG).removeTag(TagNames.FORME);
                newPokemob.readPokemobData(tag);

                // clear items
                newPokemob.setHeldItem(ItemStack.EMPTY);
                newPokemob.getPokemobInventory().clear();

                // Sync genes
                IMobGenetics oldGenes = oldEntity.getCapability(IMobGenetics.GENETICS_CAP, null);
                IMobGenetics newGenes = newEntity.getCapability(IMobGenetics.GENETICS_CAP, null);
                newGenes.getAlleles().putAll(oldGenes.getAlleles());
                GeneticsManager.handleEpigenetics(newPokemob);
                newPokemob.onGenesChanged();

                // Sync entity data, UUID and location.
                newEntity.getEntityData().merge(oldEntity.getEntityData());

                newPokemob.setExp(Tools.levelToXp(entry.getEvolutionMode(), 5), false);

                ResourceLocation id = new ResourceLocation("pokecube:park");
                check:
                {
                    for (ResourceLocation h : IPokecube.BEHAVIORS.getKeys())
                    {
                        if (h.equals(id))
                        {
                            break check;
                        }
                    }
                    id = PokecubeBehavior.DEFAULTCUBE;
                }
                newPokemob.setPokecube(new ItemStack(PokecubeItems.getFilledCube(id)));
                stack = PokecubeManager.pokemobToItem(newPokemob);

                Tools.giveItem(player, stack);
                if (legacies - 1 <= 0)
                {
                    pokemobs.cache.clear();
                    pokemobs.genesDeleted.clear();
                    pokemobs.inPC.clear();
                }
                saveData(player.getUniqueID());
                break;
            }
        }

    }

    public static int legacyStarterCount = 3;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void PlayerLoggin(PlayerLoggedInEvent evt)
    {
        boolean checked = evt.player.getEntityData().hasKey("_checked_legacy_");
        boolean hasLegacy = evt.player.getEntityData().getBoolean("_checked_legacy_");
        // Already checked for legacy, do not have it.
        if (checked && !evt.player.getEntityData().getBoolean("_checked_legacy_")) { return; }
        if (!checked || hasLegacy)
        {
            World world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
            ISaveHandler saveHandler = world.getSaveHandler();
            File legacyFolder = new File(saveHandler.getWorldDirectory(), "legacy");
            if (legacyFolder.exists() && legacyFolder.isDirectory())
            {
                hasLegacy = new File(legacyFolder, evt.player.getCachedUniqueIdString()).exists();
                loadData(evt.player.getUniqueID());
                if (!checked)
                {
                    PlayerPokemobCache legacyCache = cache.get(evt.player.getUniqueID());
                    int legacies = Math.min(legacyStarterCount,
                            legacyCache.cache.size() - legacyCache.genesDeleted.size());
                    evt.player.getEntityData().setInteger("_legacy_starters_", legacies);
                }
                checked = true;
            }
        }
        evt.player.getEntityData().setBoolean("_checked_legacy_", hasLegacy);
        if (!hasLegacy) return;

        int legacies = evt.player.getEntityData().getInteger("_legacy_starters_");
        if (legacies <= 0) return;

        evt.player.sendMessage(
                new TextComponentString("Try using /legacy_options pick, to select some of your old pokemobs."));
        evt.player.sendMessage(new TextComponentString(String.format("You may pick up to %s of them.", legacies)));
    }

    public static void loadData(UUID uuid)
    {
        PlayerPokemobCache legacyCache = new PlayerPokemobCache();
        File file = getFileForUUID(uuid.toString(), legacyCache.dataFileName());
        if (file != null && file.exists())
        {
            try
            {
                FileInputStream fileinputstream = new FileInputStream(file);
                NBTTagCompound nbttagcompound = CompressedStreamTools.readCompressed(fileinputstream);
                fileinputstream.close();
                legacyCache.readFromNBT(nbttagcompound.getCompoundTag("Data"));
                if (legacyCache.cache.isEmpty())
                {
                    file.delete();
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        cache.put(uuid, legacyCache);
    }

    public static void saveData(UUID uuid)
    {
        PlayerPokemobCache legacyCache = cache.get(uuid);
        if (legacyCache == null) return;
        File file = getFileForUUID(uuid.toString(), legacyCache.dataFileName());
        if (file != null)
        {
            NBTTagCompound nbttagcompound = new NBTTagCompound();
            legacyCache.writeToNBT(nbttagcompound);
            NBTTagCompound nbttagcompound1 = new NBTTagCompound();
            nbttagcompound1.setTag("Data", nbttagcompound);
            try
            {
                FileOutputStream fileoutputstream = new FileOutputStream(file);
                CompressedStreamTools.writeCompressed(nbttagcompound1, fileoutputstream);
                fileoutputstream.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void cleanLegacyFolder()
    {
        PlayerPokemobCache legacyCache;
        World world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
        ISaveHandler saveHandler = world.getSaveHandler();
        File legacyFolder = new File(saveHandler.getWorldDirectory(), "legacy");
        for (File dir : legacyFolder.listFiles())
        {
            if (dir.isDirectory())
            {
                for (File file : dir.listFiles())
                {
                    legacyCache = new PlayerPokemobCache();
                    if (!file.getName().contains(legacyCache.dataFileName()))
                    {
                        file.delete();
                    }
                    else
                    {
                        try
                        {
                            FileInputStream fileinputstream = new FileInputStream(file);
                            NBTTagCompound nbttagcompound = CompressedStreamTools.readCompressed(fileinputstream);
                            fileinputstream.close();
                            legacyCache.readFromNBT(nbttagcompound.getCompoundTag("Data"));
                            if (legacyCache.cache.isEmpty())
                            {
                                file.delete();
                            }
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
                if (dir.listFiles().length == 0)
                {
                    dir.delete();
                }
            }
        }
    }

    public static File getFileForUUID(String uuid, String fileName)
    {
        World world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
        ISaveHandler saveHandler = world.getSaveHandler();
        String seperator = System.getProperty("file.separator");
        File legacyFolder = new File(saveHandler.getWorldDirectory(), "legacy");
        File file = new File(legacyFolder, uuid + seperator + fileName + ".dat");
        File dir = new File(file.getParentFile().getAbsolutePath());
        if (!file.exists())
        {
            dir.mkdirs();
        }
        return file;
    }
}

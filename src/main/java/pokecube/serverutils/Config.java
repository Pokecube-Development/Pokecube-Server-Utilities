package pokecube.serverutils;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pokecube.serverutils.starters.LegacyStarterManager;
import thut.core.common.config.ConfigBase;
import thut.core.common.config.Configure;

public class Config extends ConfigBase
{
    private static final String CAPTURE                  = "captureRestriction";
    private static final String DIMCTRL                  = "dimensionControl";
    private static final String CBTMODE                  = "battlemode";
    private static final String MOBCTRL                  = "mobcontrol";
    private static final String MISC                     = "misc";

    @Configure(category = CAPTURE)
    int                         maxCaptureLevelNormal    = 100;
    @Configure(category = CAPTURE)
    int                         maxCaptureLevelLegendary = 100;
    @Configure(category = CAPTURE)
    String[]                    maxCaptureLevelOverrides = { "mew:100" };

    @Configure(category = DIMCTRL)
    int[]                       dimensions               = {};
    @Configure(category = DIMCTRL)
    boolean                     dimsEnabled              = false;
    @Configure(category = DIMCTRL)
    boolean                     whitelist                = false;
    @Configure(category = DIMCTRL)
    boolean                     blacklist                = false;

    @Configure(category = CBTMODE)
    boolean                     turnbased                = false;

    @Configure(category = MOBCTRL)
    String[]                    pokemobBlacklist         = {};
    @Configure(category = MOBCTRL)
    boolean                     pokemobBlacklistenabled  = false;

    @Configure(category = MISC)
    int                         legacyStarters           = 3;

    @Configure(category = MISC)
    public boolean              cleanLegacies            = true;

    public Config()
    {
        super(null);
    }

    public Config(File configFile)
    {
        super(configFile, new Config());
        MinecraftForge.EVENT_BUS.register(this);
        populateSettings();
        applySettings();
        save();
    }

    @Override
    protected void applySettings()
    {
        PokeServerUtils.instance.dimensionList.clear();
        for (int i : dimensions)
            PokeServerUtils.instance.dimensionList.add(i);
        PokeServerUtils.turnbasedManager.disable();
        if (turnbased) PokeServerUtils.turnbasedManager.enable();
        LegacyStarterManager.legacyStarterCount = legacyStarters;
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent eventArgs)
    {
        if (eventArgs.getModID().equals(Reference.MODID))
        {
            populateSettings();
            applySettings();
            save();
        }
    }

}

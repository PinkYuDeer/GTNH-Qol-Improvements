package dev.gtnh.qol;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.gtnh.qol.config.QolConfig;
import dev.gtnh.qol.proxy.CommonProxy;

@Mod(
    modid = GTNHQolImprovements.MOD_ID,
    name = GTNHQolImprovements.MOD_NAME,
    version = Tags.VERSION,
    guiFactory = "dev.gtnh.qol.client.config.QolGuiFactory",
    dependencies = "required-after:appliedenergistics2;required-after:ae2fc;required-after:gregtech;required-after:NotEnoughItems;required-after:backhand;required-after:Baubles|Expanded;required-after:betterquesting")
public final class GTNHQolImprovements {

    public static final String MOD_ID = "gtnh_qol_improvements";
    public static final String MOD_NAME = "GTNH QoL Improvements";

    @Instance(MOD_ID)
    public static GTNHQolImprovements instance;

    @SidedProxy(clientSide = "dev.gtnh.qol.proxy.ClientProxy", serverSide = "dev.gtnh.qol.proxy.CommonProxy")
    public static CommonProxy proxy;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        QolConfig.load(event.getSuggestedConfigurationFile());
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        proxy.preInit();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }

    @EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        proxy.loadComplete();
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (MOD_ID.equals(event.modID)) {
            QolConfig.sync();
        }
    }
}

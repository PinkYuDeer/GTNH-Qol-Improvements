package dev.gtnh.qol.proxy;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import dev.gtnh.qol.GTNHQolImprovements;
import dev.gtnh.qol.network.QolNetwork;
import dev.gtnh.qol.network.ServerTerminalOpenQueue;
import dev.gtnh.qol.network.ServerVajraClickQueue;
import dev.gtnh.qol.terminal.QolItems;
import dev.gtnh.qol.terminal.TerminalGuiHandler;
import dev.gtnh.qol.vajra.VajraEventHandler;

public class CommonProxy {

    public void preInit() {
        QolItems.register();
        QolNetwork.register();
        NetworkRegistry.INSTANCE.registerGuiHandler(GTNHQolImprovements.instance, new TerminalGuiHandler());
        FMLCommonHandler.instance()
            .bus()
            .register(new ServerTerminalOpenQueue());
        FMLCommonHandler.instance()
            .bus()
            .register(new ServerVajraClickQueue());
        VajraEventHandler handler = new VajraEventHandler();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
    }

    public void init() {
        QolItems.registerRecipe();
    }

    public void loadComplete() {}
}

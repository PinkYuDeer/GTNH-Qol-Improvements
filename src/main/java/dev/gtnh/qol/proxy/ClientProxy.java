package dev.gtnh.qol.proxy;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import dev.gtnh.qol.client.nei.NeiIntegration;
import dev.gtnh.qol.client.terminal.DualTerminalKeyHandler;
import dev.gtnh.qol.client.vajra.VajraOverlayHandler;

public final class ClientProxy extends CommonProxy {

    private final DualTerminalKeyHandler keyHandler = new DualTerminalKeyHandler();
    private final VajraOverlayHandler vajraHandler = new VajraOverlayHandler();

    @Override
    public void preInit() {
        super.preInit();
        MinecraftForge.EVENT_BUS.register(vajraHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(vajraHandler);
        keyHandler.register();
        FMLCommonHandler.instance()
            .bus()
            .register(keyHandler);
    }

    @Override
    public void init() {
        super.init();
        NeiIntegration.register();
    }

    @Override
    public void loadComplete() {
        NeiIntegration.registerKnownHandlers();
    }
}

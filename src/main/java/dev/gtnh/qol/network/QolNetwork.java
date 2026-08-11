package dev.gtnh.qol.network;

import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import dev.gtnh.qol.GTNHQolImprovements;

public final class QolNetwork {

    public static final SimpleNetworkWrapper CHANNEL = new SimpleNetworkWrapper(GTNHQolImprovements.MOD_ID);

    private QolNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(OpenTerminalMessage.Handler.class, OpenTerminalMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(VajraToolClickMessage.Handler.class, VajraToolClickMessage.class, 1, Side.SERVER);
    }

    public static void openTerminal(int slot, int page) {
        CHANNEL.sendToServer(new OpenTerminalMessage(slot, page));
    }

    public static void vajraToolClick(int x, int y, int z, int face, float hitX, float hitY, float hitZ) {
        CHANNEL.sendToServer(new VajraToolClickMessage(x, y, z, face, hitX, hitY, hitZ));
    }
}

package dev.gtnh.qol.network;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEStack;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class QolNetwork {

    /** Minecraft 1.7.10 limits C17 custom-payload channel names to 20 characters. */
    private static final String CHANNEL_NAME = "gtnh_qol";
    public static final SimpleNetworkWrapper CHANNEL = new SimpleNetworkWrapper(CHANNEL_NAME);

    private QolNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(OpenTerminalMessage.Handler.class, OpenTerminalMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(VajraToolClickMessage.Handler.class, VajraToolClickMessage.class, 1, Side.SERVER);
        CHANNEL
            .registerMessage(MiddleClickRequestMessage.Handler.class, MiddleClickRequestMessage.class, 2, Side.SERVER);
    }

    public static void openTerminal(int slot, int page) {
        CHANNEL.sendToServer(new OpenTerminalMessage(slot, page));
    }

    public static void vajraToolClick(int x, int y, int z, int face, float hitX, float hitY, float hitZ) {
        CHANNEL.sendToServer(new VajraToolClickMessage(x, y, z, face, hitX, hitY, hitZ));
    }

    public static void middleClickBookmark(IAEStack<?> stack, long amount) {
        CHANNEL.sendToServer(MiddleClickRequestMessage.bookmark(stack, amount));
    }

    public static void middleClickWorldBlock(int hotbarSlot, ItemStack stack) {
        CHANNEL.sendToServer(MiddleClickRequestMessage.worldBlock(hotbarSlot, stack));
    }
}

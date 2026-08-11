package dev.gtnh.qol.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.util.Platform;
import cpw.mods.fml.common.network.IGuiHandler;
import dev.gtnh.qol.client.terminal.GuiQuickEncodingTerminal;
import dev.gtnh.qol.config.QolConfig;

public final class TerminalGuiHandler implements IGuiHandler {

    public static final int QUICK_ENCODING_TERMINAL = 0;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int slot, int y, int z) {
        DualTerminalGuiObject host = getHost(player, world, slot);
        return host != null && id == QUICK_ENCODING_TERMINAL
            ? new ContainerQuickEncodingTerminal(player.inventory, host)
            : null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int slot, int y, int z) {
        DualTerminalGuiObject host = getHost(player, world, slot);
        return host != null && id == QUICK_ENCODING_TERMINAL ? new GuiQuickEncodingTerminal(player.inventory, host)
            : null;
    }

    private static DualTerminalGuiObject getHost(EntityPlayer player, World world, int slot) {
        if (!QolConfig.dualTerminal) {
            return null;
        }
        ItemStack stack = Platform.getItemFromPlayerInventoryBySlotIndex(player, slot);
        if (stack == null || stack.getItem() != QolItems.dualTerminal) {
            return null;
        }
        Platform.openNbtData(stack);
        return new DualTerminalGuiObject(QolItems.dualTerminal, stack, player, world, slot);
    }
}

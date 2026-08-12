package dev.gtnh.qol.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.util.Platform;
import cpw.mods.fml.common.network.IGuiHandler;
import dev.gtnh.qol.client.terminal.GuiQuickEncodingTerminal;
import dev.gtnh.qol.config.QolConfig;

public final class TerminalGuiHandler implements IGuiHandler {

    public static final int QUICK_ENCODING_TERMINAL = 0;
    private static final int PANEL_TERMINAL_BASE = 10;

    public static int panelGuiId(ForgeDirection side) {
        int ordinal = side == null ? ForgeDirection.UNKNOWN.ordinal() : side.ordinal();
        return PANEL_TERMINAL_BASE + ordinal;
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int slot, int y, int z) {
        QuickEncodingTerminalHost host = getHost(id, player, world, slot, y, z);
        return host != null ? new ContainerQuickEncodingTerminal(player.inventory, host) : null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int slot, int y, int z) {
        QuickEncodingTerminalHost host = getHost(id, player, world, slot, y, z);
        return host != null ? new GuiQuickEncodingTerminal(player.inventory, host) : null;
    }

    private static QuickEncodingTerminalHost getHost(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (!QolConfig.dualTerminal) {
            return null;
        }
        if (id == QUICK_ENCODING_TERMINAL) return getWirelessHost(player, world, x);
        int sideOrdinal = id - PANEL_TERMINAL_BASE;
        if (sideOrdinal < 0 || sideOrdinal >= ForgeDirection.UNKNOWN.ordinal()) return null;
        if (!(world.getTileEntity(x, y, z) instanceof IPartHost partHost)) return null;
        IPart part = partHost.getPart(ForgeDirection.getOrientation(sideOrdinal));
        return part instanceof PartQuickEncodingTerminal panel ? panel : null;
    }

    private static DualTerminalGuiObject getWirelessHost(EntityPlayer player, World world, int slot) {
        ItemStack stack = Platform.getItemFromPlayerInventoryBySlotIndex(player, slot);
        if (stack == null || stack.getItem() != QolItems.dualTerminal) {
            return null;
        }
        Platform.openNbtData(stack);
        return new DualTerminalGuiObject(QolItems.dualTerminal, stack, player, world, slot);
    }
}

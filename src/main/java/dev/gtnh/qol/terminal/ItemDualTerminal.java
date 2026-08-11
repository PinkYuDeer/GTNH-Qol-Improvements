package dev.gtnh.qol.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.glodblock.github.common.item.ItemBaseWirelessTerminal;

import appeng.api.AEApi;
import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.util.Platform;
import dev.gtnh.qol.GTNHQolImprovements;
import dev.gtnh.qol.config.QolConfig;

public final class ItemDualTerminal extends ItemBaseWirelessTerminal implements IGuiItem {

    public ItemDualTerminal() {
        super(null);
        AEApi.instance()
            .registries()
            .wireless()
            .registerWirelessHandler(this);
    }

    @Override
    public boolean canHandle(ItemStack stack) {
        return stack != null && stack.getItem() == this;
    }

    @Override
    public void openGui(ItemStack stack, World world, EntityPlayer player, Object mode) {
        if (QolConfig.dualTerminal && !world.isRemote) {
            openChecked(player, player.inventory.currentItem, false);
        }
    }

    public static void openChecked(EntityPlayer player, int slot, boolean performWirelessCheck) {
        if (player == null || player.worldObj.isRemote) {
            return;
        }
        if (!QolConfig.dualTerminal) {
            player.addChatMessage(new ChatComponentTranslation("gtnh_qol_improvements.terminal.disabled"));
            return;
        }

        ItemStack stack = Platform.getItemFromPlayerInventoryBySlotIndex(player, slot);
        if (stack == null || stack.getItem() != QolItems.dualTerminal) {
            return;
        }
        if (performWirelessCheck && !AEApi.instance()
            .registries()
            .wireless()
            .performCheck(stack, player)) {
            return;
        }
        player.openGui(
            GTNHQolImprovements.instance,
            TerminalGuiHandler.QUICK_ENCODING_TERMINAL,
            player.worldObj,
            slot,
            0,
            0);
    }

    @Override
    public IGuiItemObject getGuiObject(ItemStack stack, World world, EntityPlayer player, int slot, int mode,
        int unused) {
        Platform.openNbtData(stack);
        return new DualTerminalGuiObject(this, stack, player, world, slot);
    }
}

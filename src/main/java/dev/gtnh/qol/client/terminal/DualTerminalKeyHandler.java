package dev.gtnh.qol.client.terminal;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;

import org.lwjgl.input.Mouse;

import appeng.util.Platform;
import baubles.api.BaublesApi;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.gtnh.qol.config.QolConfig;
import dev.gtnh.qol.network.QolNetwork;
import dev.gtnh.qol.terminal.QolItems;
import dev.gtnh.qol.terminal.TerminalGuiHandler;

public final class DualTerminalKeyHandler {

    private static final int BAUBLES_SLOT_OFFSET = 100_012;

    private final KeyBinding openTerminal = new KeyBinding(
        "key.gtnh_qol_improvements.openDualTerminal",
        0,
        "key.categories.gtnh_qol_improvements");

    public void register() {
        ClientRegistry.registerKeyBinding(openTerminal);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !openTerminal.isPressed() || !QolConfig.dualTerminal) {
            return;
        }
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }

        for (int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
            if (isDualTerminal(player.inventory.mainInventory[slot])) {
                QolNetwork.openTerminal(slot, TerminalGuiHandler.QUICK_ENCODING_TERMINAL);
                return;
            }
        }

        IInventory baubles = BaublesApi.getBaubles(player);
        if (baubles != null) {
            for (int slot = 0; slot < baubles.getSizeInventory(); slot++) {
                if (isDualTerminal(baubles.getStackInSlot(slot))) {
                    QolNetwork.openTerminal(BAUBLES_SLOT_OFFSET + slot, TerminalGuiHandler.QUICK_ENCODING_TERMINAL);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        if (!QolConfig.middleClickOrdering || !Mouse.getEventButtonState() || Mouse.getEventButton() != 2) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.thePlayer;
        if (minecraft.currentScreen != null || player == null
            || player.capabilities.isCreativeMode
            || player.inventory.getCurrentItem() != null) {
            return;
        }

        ItemStack target = getTargetBlock(minecraft, player);
        if (target == null || findPreciseStack(player, target) >= 0) {
            return;
        }

        target = target.copy();
        target.stackSize = 1;
        QolNetwork.middleClickWorldBlock(player.inventory.currentItem, target);
    }

    private static ItemStack getTargetBlock(Minecraft minecraft, EntityPlayer player) {
        MovingObjectPosition hit = minecraft.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }
        try {
            Block block = player.worldObj.getBlock(hit.blockX, hit.blockY, hit.blockZ);
            return block.getPickBlock(hit, player.worldObj, hit.blockX, hit.blockY, hit.blockZ, player);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static int findPreciseStack(EntityPlayer player, ItemStack target) {
        for (int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
            ItemStack candidate = player.inventory.mainInventory[slot];
            if (candidate != null && Platform.isSameItemPrecise(candidate, target)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean isDualTerminal(ItemStack stack) {
        return stack != null && stack.getItem() == QolItems.dualTerminal;
    }
}

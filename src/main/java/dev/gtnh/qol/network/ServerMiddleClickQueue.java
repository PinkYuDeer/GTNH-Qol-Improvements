package dev.gtnh.qol.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.ContainerOpenContext;
import appeng.container.PrimaryGui;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.core.sync.GuiBridge;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import baubles.api.BaublesApi;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.gtnh.qol.config.QolConfig;
import dev.gtnh.qol.terminal.ContainerQuickEncodingTerminal;
import dev.gtnh.qol.terminal.DualTerminalGuiObject;
import dev.gtnh.qol.terminal.ItemDualTerminal;
import dev.gtnh.qol.terminal.QolItems;

/** Executes middle-click requests on the server thread. */
public final class ServerMiddleClickQueue {

    private static final int BAUBLES_SLOT_OFFSET = 100_012;
    private static final Queue<Request> REQUESTS = new ConcurrentLinkedQueue<>();

    public static void enqueue(EntityPlayerMP player, boolean worldBlock, int hotbarSlot, long amount,
        ItemStack stack) {
        REQUESTS.add(new Request(player, worldBlock, hotbarSlot, amount, stack == null ? null : stack.copy()));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Request request;
        while ((request = REQUESTS.poll()) != null) {
            process(request);
        }
    }

    private static void process(Request request) {
        EntityPlayerMP player = request.player;
        if (!QolConfig.middleClickOrdering || player == null
            || player.playerNetServerHandler == null
            || request.stack == null
            || request.stack.getItem() == null) {
            return;
        }

        ItemStack targetStack = request.stack.copy();
        targetStack.stackSize = 1;
        IAEItemStack target = AEItemStack.create(targetStack);
        if (target == null) {
            return;
        }
        target.setStackSize(1);

        if (request.worldBlock) {
            if (!QolConfig.dualTerminal) return;
            processWorldBlock(player, request.hotbarSlot, targetStack, target);
        } else {
            processBookmark(player, target, request.amount);
        }
    }

    private static void processBookmark(EntityPlayerMP player, IAEItemStack target, long amount) {
        if (!(player.openContainer instanceof ContainerMEMonitorable container)
            || !isCraftable(container.getNetworkNode(), target, player)) {
            return;
        }
        openCraftAmount(player, container, target, amount);
    }

    private static void processWorldBlock(EntityPlayerMP player, int hotbarSlot, ItemStack targetStack,
        IAEItemStack target) {
        if (hotbarSlot < 0 || hotbarSlot >= 9
            || hotbarSlot != player.inventory.currentItem
            || player.inventory.mainInventory[hotbarSlot] != null
            || player.inventory.getItemStack() != null
            || player.capabilities.isCreativeMode
            || !isBlockItem(targetStack)) {
            return;
        }

        List<TerminalAccess> terminals = findAccessibleTerminals(player);
        for (TerminalAccess terminal : terminals) {
            if (extractStack(player, hotbarSlot, terminal.host, target)) {
                return;
            }
        }

        for (TerminalAccess terminal : terminals) {
            if (isCraftable(terminal.host.getActionableNode(), target, player)) {
                ItemDualTerminal.openChecked(player, terminal.inventorySlot, true);
                if (player.openContainer instanceof ContainerQuickEncodingTerminal container) {
                    openCraftAmount(player, container, target, 1);
                }
                return;
            }
        }
    }

    private static boolean extractStack(EntityPlayerMP player, int hotbarSlot, DualTerminalGuiObject host,
        IAEItemStack target) {
        IMEMonitor<IAEItemStack> monitor = host.getItemInventory();
        if (monitor == null) {
            return false;
        }
        IAEItemStack request = target.copy();
        request.setStackSize(
            Math.max(
                1,
                target.getItemStack()
                    .getMaxStackSize()));
        PlayerSource source = new PlayerSource(player, host);
        IAEItemStack available = monitor.extractItems(request, Actionable.SIMULATE, source);
        if (available == null || available.getStackSize() <= 0) {
            return false;
        }

        request.setStackSize(Math.min(request.getStackSize(), available.getStackSize()));
        IAEItemStack extracted = Platform.poweredExtraction(host, monitor, request, source);
        if (extracted == null || extracted.getStackSize() <= 0) {
            return false;
        }

        player.inventory.setInventorySlotContents(hotbarSlot, extracted.getItemStack());
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
        return true;
    }

    private static List<TerminalAccess> findAccessibleTerminals(EntityPlayerMP player) {
        List<TerminalAccess> result = new ArrayList<>();
        for (int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
            addTerminal(result, player, slot, player.inventory.mainInventory[slot]);
        }

        IInventory baubles = BaublesApi.getBaubles(player);
        if (baubles != null) {
            for (int slot = 0; slot < baubles.getSizeInventory(); slot++) {
                addTerminal(result, player, BAUBLES_SLOT_OFFSET + slot, baubles.getStackInSlot(slot));
            }
        }
        return result;
    }

    private static void addTerminal(List<TerminalAccess> terminals, EntityPlayerMP player, int inventorySlot,
        ItemStack stack) {
        if (stack == null || stack.getItem() != QolItems.dualTerminal) {
            return;
        }
        try {
            Platform.openNbtData(stack);
            DualTerminalGuiObject host = new DualTerminalGuiObject(
                QolItems.dualTerminal,
                stack,
                player,
                player.worldObj,
                inventorySlot);
            if (host.rangeCheck() && host.getActionableNode() != null) {
                terminals.add(new TerminalAccess(inventorySlot, host));
            }
        } catch (RuntimeException | LinkageError ignored) {}
    }

    private static boolean isCraftable(IGridNode node, IAEItemStack target, EntityPlayerMP player) {
        if (node == null || node.getGrid() == null) {
            return false;
        }
        try {
            IGrid grid = node.getGrid();
            ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
            return crafting != null && !crafting.getCraftingFor(target, null, 0, player.worldObj)
                .isEmpty();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isBlockItem(ItemStack stack) {
        Block block = Block.getBlockFromItem(stack.getItem());
        return block != null && block != Blocks.air;
    }

    private static void openCraftAmount(EntityPlayerMP player, ContainerMEMonitorable container, IAEItemStack target,
        long initialAmount) {
        container.setTargetStack(target);
        PrimaryGui primaryGui = container.createPrimaryGui();
        ContainerOpenContext context = container.getOpenContext();
        if (context == null) {
            return;
        }

        Platform.openGUI(
            player,
            context.getTile(),
            context.getSide(),
            GuiBridge.GUI_CRAFTING_AMOUNT,
            container.getTargetSlotIndex());
        if (player.openContainer instanceof ContainerCraftAmount amount) {
            amount.setPrimaryGui(primaryGui);
            amount.setItemToCraft(target);
            amount.setInitialCraftAmount(Math.max(1, initialAmount));
            amount.detectAndSendChanges();
        }
    }

    private static final class TerminalAccess {

        private final int inventorySlot;
        private final DualTerminalGuiObject host;

        private TerminalAccess(int inventorySlot, DualTerminalGuiObject host) {
            this.inventorySlot = inventorySlot;
            this.host = host;
        }
    }

    private static final class Request {

        private final EntityPlayerMP player;
        private final boolean worldBlock;
        private final int hotbarSlot;
        private final long amount;
        private final ItemStack stack;

        private Request(EntityPlayerMP player, boolean worldBlock, int hotbarSlot, long amount, ItemStack stack) {
            this.player = player;
            this.worldBlock = worldBlock;
            this.hotbarSlot = hotbarSlot;
            this.amount = Math.max(1, amount);
            this.stack = stack;
        }
    }
}

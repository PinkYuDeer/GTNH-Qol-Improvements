package dev.gtnh.qol.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import appeng.api.features.IWirelessTermHandler;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.parts.IInterfaceTerminal;
import appeng.api.storage.data.IAEStack;
import appeng.items.contents.WirelessPatternTerminalGuiObject;

public final class DualTerminalGuiObject extends WirelessPatternTerminalGuiObject implements IInterfaceTerminal {

    private static final String QUICK_CRAFTING_MODE = "gtnhQolQuickCraftingMode";
    private static final String QUICK_PROCESSING_GRID_SIZE = "gtnhQolQuickProcessingGridSize";
    private static final String QUICK_CRAFTING_PIN_ROWS = "gtnhQolQuickCraftingPinRows";
    private static final String QUICK_PLAYER_PIN_ROWS = "gtnhQolQuickPlayerPinRows";
    private static final String QUICK_PROCESSING_FLUID_INPUTS = "gtnhQolQuickProcessingFluidInputs";
    private static final String QUICK_CRAFTING_SNAPSHOT = "gtnhQolQuickCraftingSnapshot";
    private static final String QUICK_PROCESSING_SNAPSHOT = "gtnhQolQuickProcessingSnapshot";
    private static final String QUICK_PATTERN_SNAPSHOTS_LINKED = "gtnhQolQuickPatternSnapshotsLinked";
    private static final String SNAPSHOT_INPUTS = "Inputs";
    private static final String SNAPSHOT_OUTPUTS = "Outputs";

    private boolean needsUpdate = true;

    public DualTerminalGuiObject(IWirelessTermHandler handler, ItemStack stack, EntityPlayer player, World world,
        int slot) {
        super(handler, stack, player, world, slot, 2, 0);
    }

    @Override
    public boolean needsUpdate() {
        boolean result = needsUpdate;
        needsUpdate = false;
        return result;
    }

    @MENetworkEventSubscribe
    public void onNetworkBootingChanged(MENetworkBootingStatusChange event) {
        if (!event.isBooting) {
            needsUpdate = true;
        }
    }

    @Override
    public boolean isCraftingRecipe() {
        NBTTagCompound data = getItemStack().getTagCompound();
        return data == null || !data.hasKey(QUICK_CRAFTING_MODE) || data.getBoolean(QUICK_CRAFTING_MODE);
    }

    @Override
    public void setCraftingRecipe(boolean craftingMode) {
        super.setCraftingRecipe(craftingMode);
        NBTTagCompound data = getItemStack().getTagCompound();
        if (data != null) {
            data.setBoolean(QUICK_CRAFTING_MODE, craftingMode);
        }
    }

    public int getProcessingGridSize() {
        NBTTagCompound data = getItemStack().getTagCompound();
        if (data == null || !data.hasKey(QUICK_PROCESSING_GRID_SIZE)) return 4;
        return data.getInteger(QUICK_PROCESSING_GRID_SIZE) == 3 ? 3 : 4;
    }

    public void setProcessingGridSize(int gridSize) {
        NBTTagCompound data = getItemStack().getTagCompound();
        if (data == null) {
            data = new NBTTagCompound();
            getItemStack().setTagCompound(data);
        }
        data.setInteger(QUICK_PROCESSING_GRID_SIZE, gridSize == 3 ? 3 : 4);
    }

    public int getCraftingPinRows(int fallback) {
        return getPinRows(QUICK_CRAFTING_PIN_ROWS, fallback);
    }

    public int getPlayerPinRows(int fallback) {
        return getPinRows(QUICK_PLAYER_PIN_ROWS, fallback);
    }

    public void setPinRows(int craftingRows, int playerRows) {
        NBTTagCompound data = getItemStack().getTagCompound();
        if (data == null) {
            data = new NBTTagCompound();
            getItemStack().setTagCompound(data);
        }
        data.setInteger(QUICK_CRAFTING_PIN_ROWS, Math.max(0, craftingRows));
        data.setInteger(QUICK_PLAYER_PIN_ROWS, Math.max(0, playerRows));
    }

    private int getPinRows(String key, int fallback) {
        NBTTagCompound data = getItemStack().getTagCompound();
        return data == null || !data.hasKey(key) ? Math.max(0, fallback) : Math.max(0, data.getInteger(key));
    }

    public IAEStack<?>[] getProcessingFluidInputs(int size) {
        IAEStack<?>[] result = new IAEStack<?>[size];
        NBTTagCompound data = getItemStack().getTagCompound();
        if (data == null) return result;

        NBTTagList entries = data.getTagList(QUICK_PROCESSING_FLUID_INPUTS, 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            int slot = entry.getInteger("Slot");
            if (slot >= 0 && slot < result.length && entry.hasKey("Stack")) {
                result[slot] = IAEStack.fromNBTGeneric(entry.getCompoundTag("Stack"));
            }
        }
        return result;
    }

    public void setProcessingFluidInputs(IAEStack<?>[] inputs) {
        NBTTagCompound data = getItemStack().getTagCompound();
        if (data == null) {
            data = new NBTTagCompound();
            getItemStack().setTagCompound(data);
        }
        NBTTagList entries = new NBTTagList();
        for (int slot = 0; slot < inputs.length; slot++) {
            IAEStack<?> stack = inputs[slot];
            if (stack == null) continue;
            NBTTagCompound entry = new NBTTagCompound();
            NBTTagCompound stackTag = new NBTTagCompound();
            stack.writeToNBTGeneric(stackTag);
            entry.setInteger("Slot", slot);
            entry.setTag("Stack", stackTag);
            entries.appendTag(entry);
        }
        data.setTag(QUICK_PROCESSING_FLUID_INPUTS, entries);
    }

    public boolean hasPatternSnapshot(boolean crafting) {
        NBTTagCompound data = getItemStack().getTagCompound();
        return data != null && data.hasKey(snapshotKey(crafting), 10);
    }

    public IAEStack<?>[] getPatternSnapshotInputs(boolean crafting, int size) {
        return readSnapshotInventory(crafting, SNAPSHOT_INPUTS, size);
    }

    public IAEStack<?>[] getPatternSnapshotOutputs(boolean crafting, int size) {
        return readSnapshotInventory(crafting, SNAPSHOT_OUTPUTS, size);
    }

    public void setPatternSnapshot(boolean crafting, IAEStack<?>[] inputs, IAEStack<?>[] outputs) {
        NBTTagCompound data = getItemStack().getTagCompound();
        if (data == null) {
            data = new NBTTagCompound();
            getItemStack().setTagCompound(data);
        }
        NBTTagCompound snapshot = new NBTTagCompound();
        snapshot.setTag(SNAPSHOT_INPUTS, writeStackList(inputs));
        snapshot.setTag(SNAPSHOT_OUTPUTS, writeStackList(outputs));
        data.setTag(snapshotKey(crafting), snapshot);
    }

    public boolean arePatternSnapshotsLinked() {
        NBTTagCompound data = getItemStack().getTagCompound();
        return data != null && data.getBoolean(QUICK_PATTERN_SNAPSHOTS_LINKED);
    }

    public void setPatternSnapshotsLinked(boolean linked) {
        NBTTagCompound data = getItemStack().getTagCompound();
        if (data == null) {
            data = new NBTTagCompound();
            getItemStack().setTagCompound(data);
        }
        data.setBoolean(QUICK_PATTERN_SNAPSHOTS_LINKED, linked);
    }

    private IAEStack<?>[] readSnapshotInventory(boolean crafting, String inventoryName, int size) {
        IAEStack<?>[] result = new IAEStack<?>[size];
        NBTTagCompound data = getItemStack().getTagCompound();
        if (data == null || !data.hasKey(snapshotKey(crafting), 10)) return result;

        NBTTagCompound snapshot = data.getCompoundTag(snapshotKey(crafting));
        NBTTagList entries = snapshot.getTagList(inventoryName, 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            int slot = entry.getInteger("Slot");
            if (slot >= 0 && slot < result.length && entry.hasKey("Stack", 10)) {
                result[slot] = IAEStack.fromNBTGeneric(entry.getCompoundTag("Stack"));
            }
        }
        return result;
    }

    private static NBTTagList writeStackList(IAEStack<?>[] stacks) {
        NBTTagList entries = new NBTTagList();
        for (int slot = 0; slot < stacks.length; slot++) {
            IAEStack<?> stack = stacks[slot];
            if (stack == null) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("Slot", slot);
            entry.setTag("Stack", stack.toNBTGeneric());
            entries.appendTag(entry);
        }
        return entries;
    }

    private static String snapshotKey(boolean crafting) {
        return crafting ? QUICK_CRAFTING_SNAPSHOT : QUICK_PROCESSING_SNAPSHOT;
    }

    @Override
    public void writeCustomButtonData() {}

    @Override
    public void readCustomButtonData() {}
}

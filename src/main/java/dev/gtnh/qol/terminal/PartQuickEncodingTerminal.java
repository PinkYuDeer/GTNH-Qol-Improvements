package dev.gtnh.qol.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.Vec3;

import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.storage.data.IAEStack;
import appeng.core.sync.GuiBridge;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.util.Platform;
import dev.gtnh.qol.GTNHQolImprovements;
import dev.gtnh.qol.config.QolConfig;

/**
 * Cable-mounted quick encoding terminal. It intentionally exposes only the
 * normal pattern terminal's 3x3 input and three processing output slots.
 */
public final class PartQuickEncodingTerminal extends PartPatternTerminal implements QuickEncodingTerminalHost {

    private static final String QUICK_DATA = "gtnhQolQuickEncoding";
    private static final String QUICK_CRAFTING_MODE = "craftingMode";
    private static final String QUICK_CRAFTING_PIN_ROWS = "craftingPinRows";
    private static final String QUICK_PLAYER_PIN_ROWS = "playerPinRows";
    private static final String QUICK_CRAFTING_SNAPSHOT = "craftingSnapshot";
    private static final String QUICK_PROCESSING_SNAPSHOT = "processingSnapshot";
    private static final String QUICK_PATTERN_SNAPSHOTS_LINKED = "snapshotsLinked";
    private static final String SNAPSHOT_INPUTS = "Inputs";
    private static final String SNAPSHOT_OUTPUTS = "Outputs";

    private NBTTagCompound quickData = new NBTTagCompound();
    private boolean needsUpdate = true;

    public PartQuickEncodingTerminal(ItemStack stack) {
        super(stack);
    }

    @Override
    public boolean onPartActivate(EntityPlayer player, Vec3 pos) {
        if (!player.isSneaking() && Platform.isWrench(
            player,
            player.inventory.getCurrentItem(),
            getTile().xCoord,
            getTile().yCoord,
            getTile().zCoord)) {
            return super.onPartActivate(player, pos);
        }
        if (player.isSneaking()) return false;
        if (Platform.isClient()) return true;
        if (!QolConfig.dualTerminal) return true;
        if (!GuiBridge.GUI_PATTERN_TERMINAL.hasPermissions(
            getHost().getTile(),
            getTile().xCoord,
            getTile().yCoord,
            getTile().zCoord,
            getSide(),
            player)) {
            return true;
        }
        player.openGui(
            GTNHQolImprovements.instance,
            TerminalGuiHandler.panelGuiId(getSide()),
            getTile().getWorldObj(),
            getTile().xCoord,
            getTile().yCoord,
            getTile().zCoord);
        return true;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        quickData = data.hasKey(QUICK_DATA, 10) ? data.getCompoundTag(QUICK_DATA) : new NBTTagCompound();
        if (quickData.hasKey(QUICK_CRAFTING_MODE)) {
            super.setCraftingRecipe(quickData.getBoolean(QUICK_CRAFTING_MODE));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        quickData.setBoolean(QUICK_CRAFTING_MODE, isCraftingRecipe());
        data.setTag(QUICK_DATA, quickData);
        super.writeToNBT(data);
    }

    @Override
    public void setCraftingRecipe(boolean craftingMode) {
        super.setCraftingRecipe(craftingMode);
        quickData.setBoolean(QUICK_CRAFTING_MODE, craftingMode);
        saveState();
    }

    @Override
    public boolean supportsExtendedProcessing() {
        return false;
    }

    @Override
    public int getProcessingGridSize() {
        return 3;
    }

    @Override
    public void setProcessingGridSize(int gridSize) {}

    @Override
    public boolean isInverted() {
        return false;
    }

    @Override
    public void setInverted(boolean inverted) {}

    @Override
    public int getActivePage() {
        return 0;
    }

    @Override
    public void setActivePage(int activePage) {}

    @Override
    public int getCraftingPinRows(int fallback) {
        return getPinRows(QUICK_CRAFTING_PIN_ROWS, fallback);
    }

    @Override
    public int getPlayerPinRows(int fallback) {
        return getPinRows(QUICK_PLAYER_PIN_ROWS, fallback);
    }

    @Override
    public void setPinRows(int craftingRows, int playerRows) {
        quickData.setInteger(QUICK_CRAFTING_PIN_ROWS, Math.max(0, craftingRows));
        quickData.setInteger(QUICK_PLAYER_PIN_ROWS, Math.max(0, playerRows));
        saveState();
    }

    private int getPinRows(String key, int fallback) {
        return quickData.hasKey(key) ? Math.max(0, quickData.getInteger(key)) : Math.max(0, fallback);
    }

    @Override
    public IAEStack<?>[] getProcessingFluidInputs(int size) {
        return new IAEStack<?>[size];
    }

    @Override
    public boolean hasPatternSnapshot(boolean crafting) {
        return quickData.hasKey(snapshotKey(crafting), 10);
    }

    @Override
    public IAEStack<?>[] getPatternSnapshotInputs(boolean crafting, int size) {
        return readSnapshotInventory(crafting, SNAPSHOT_INPUTS, size);
    }

    @Override
    public IAEStack<?>[] getPatternSnapshotOutputs(boolean crafting, int size) {
        return readSnapshotInventory(crafting, SNAPSHOT_OUTPUTS, size);
    }

    @Override
    public void setPatternSnapshot(boolean crafting, IAEStack<?>[] inputs, IAEStack<?>[] outputs) {
        NBTTagCompound snapshot = new NBTTagCompound();
        snapshot.setTag(SNAPSHOT_INPUTS, writeStackList(inputs));
        snapshot.setTag(SNAPSHOT_OUTPUTS, writeStackList(outputs));
        quickData.setTag(snapshotKey(crafting), snapshot);
        saveState();
    }

    @Override
    public boolean arePatternSnapshotsLinked() {
        return quickData.getBoolean(QUICK_PATTERN_SNAPSHOTS_LINKED);
    }

    @Override
    public void setPatternSnapshotsLinked(boolean linked) {
        quickData.setBoolean(QUICK_PATTERN_SNAPSHOTS_LINKED, linked);
        saveState();
    }

    private IAEStack<?>[] readSnapshotInventory(boolean crafting, String inventoryName, int size) {
        IAEStack<?>[] result = new IAEStack<?>[size];
        if (!quickData.hasKey(snapshotKey(crafting), 10)) return result;
        NBTTagList entries = quickData.getCompoundTag(snapshotKey(crafting))
            .getTagList(inventoryName, 10);
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

    private void saveState() {
        if (getHost() != null) saveChanges();
    }

    @Override
    public boolean needsUpdate() {
        boolean result = needsUpdate;
        needsUpdate = false;
        return result;
    }

    @MENetworkEventSubscribe
    public void onNetworkBootingChanged(MENetworkBootingStatusChange event) {
        if (!event.isBooting) needsUpdate = true;
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return new ItemStack(QolItems.panelTerminal);
    }

}

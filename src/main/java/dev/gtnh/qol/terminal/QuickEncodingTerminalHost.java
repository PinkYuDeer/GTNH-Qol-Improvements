package dev.gtnh.qol.terminal;

import net.minecraft.item.ItemStack;

import appeng.api.parts.IInterfaceTerminal;
import appeng.api.parts.IPatternTerminalEx;
import appeng.api.storage.data.IAEStack;

/** Shared capabilities implemented by the wireless and cable-mounted terminals. */
public interface QuickEncodingTerminalHost extends IPatternTerminalEx, IInterfaceTerminal {

    boolean supportsExtendedProcessing();

    int getProcessingGridSize();

    void setProcessingGridSize(int gridSize);

    int getCraftingPinRows(int fallback);

    int getPlayerPinRows(int fallback);

    void setPinRows(int craftingRows, int playerRows);

    IAEStack<?>[] getProcessingFluidInputs(int size);

    boolean hasPatternSnapshot(boolean crafting);

    IAEStack<?>[] getPatternSnapshotInputs(boolean crafting, int size);

    IAEStack<?>[] getPatternSnapshotOutputs(boolean crafting, int size);

    void setPatternSnapshot(boolean crafting, IAEStack<?>[] inputs, IAEStack<?>[] outputs);

    boolean arePatternSnapshotsLinked();

    void setPatternSnapshotsLinked(boolean linked);

    ItemStack getPrimaryGuiIcon();
}

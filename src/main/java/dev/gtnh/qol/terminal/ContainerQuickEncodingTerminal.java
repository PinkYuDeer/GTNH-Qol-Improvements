package dev.gtnh.qol.terminal;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.PinsRows;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.parts.IInterfaceTerminal;
import appeng.api.parts.IPatternTerminalEx;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IInterfaceViewable;
import appeng.container.ContainerOpenContext;
import appeng.container.PrimaryGui;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.container.sync.ActionHandler;
import appeng.container.sync.StreamCodecs;
import appeng.container.sync.SyncRegistrar;
import appeng.container.sync.handlers.BooleanSyncHandler;
import appeng.container.sync.handlers.IntSyncHandler;
import appeng.core.AEConfig;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.InventoryAction;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

/**
 * One server container that keeps AE2's storage/pattern terminal active while delegating
 * interface-terminal discovery and clicks to AE2's original implementation.
 */
public final class ContainerQuickEncodingTerminal extends ContainerPatternTerm {

    private static final Field TRACKED_BY_ID = findTrackedById();
    private static final Field TRACKED = findField(ContainerInterfaceTerminal.class, "tracked");
    private static final Field CRAFTING_MATRIX = findField(ContainerPatternTerm.class, "craftingMatrix");
    private static final Field INTERNAL_SIZE = findField(AppEngInternalInventory.class, "size");
    private static final Field INTERNAL_ITEMS = findField(AppEngInternalInventory.class, "inv");
    private static final Field AE_STACK_INVENTORY_SIZE = findField(IAEStackInventory.class, "size");

    private final ContainerInterfaceTerminal interfaceDelegate;
    private final SlotRestrictedInput blankPatternSlot;
    private final SlotRestrictedInput encodedPatternSlot;

    public final BooleanSyncHandler invertedSync;
    public final IntSyncHandler activePageSync;
    public final IntSyncHandler processingGridSizeSync;
    public final IntSyncHandler craftingPinRowsSync;
    public final IntSyncHandler playerPinRowsSync;
    public final ActionHandler<Boolean> setInvertedAction;
    public final ActionHandler<Integer> setProcessingGridSizeAction;
    public final ActionHandler<Integer> setPinRowsAction;
    public final ActionHandler<RecipeTransferPayload> transferRecipeAction;
    public final ActionHandler<InterfacePatternTarget> placeEncodedPatternAction;
    public final ActionHandler<Integer> takeBlankPatternAction;
    public final ActionHandler<Integer> takeEncodedPatternAction;
    public final ActionHandler<InterfacePatternTarget> clickInterfacePatternAction;
    public final ActionHandler<InterfacePatternTarget> shiftClickInterfacePatternAction;

    private final IAEStack<?>[] craftingInputSnapshot = new IAEStack<?>[RecipeTransferPayload.SLOT_COUNT];
    private final IAEStack<?>[] craftingOutputSnapshot = new IAEStack<?>[RecipeTransferPayload.SLOT_COUNT];
    private final IAEStack<?>[] processingInputSnapshot = new IAEStack<?>[RecipeTransferPayload.SLOT_COUNT];
    private final IAEStack<?>[] processingOutputSnapshot = new IAEStack<?>[RecipeTransferPayload.SLOT_COUNT];
    private boolean craftingSnapshotValid;
    private boolean processingSnapshotValid;
    private boolean appliedCraftingMode;
    private boolean appliedCraftingModeInitialized;
    /**
     * The two snapshots are representations of one recipe, never independent
     * recipes. The flag is valid only while the active representation still
     * matches its snapshot. Editing either view invalidates the relationship;
     * the next mode switch then regenerates the target view from the active one.
     */
    private boolean patternSnapshotsLinked;

    public ContainerQuickEncodingTerminal(InventoryPlayer inventoryPlayer, ITerminalHost host) {
        super(inventoryPlayer, host, true);
        ContainerOpenContext context = new ContainerOpenContext(host);
        context.setWorld(inventoryPlayer.player.worldObj);
        context.setX(getDualTerminal().getInventorySlot());
        context.setY(0);
        context.setZ(0);
        context.setSide(ForgeDirection.UNKNOWN);
        setOpenContext(context);
        expandCraftingMatrix();
        blankPatternSlot = findPatternSlot(SlotRestrictedInput.PlacableItemType.BLANK_PATTERN);
        encodedPatternSlot = findPatternSlot(SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN);
        loadPatternSnapshots();
        appliedCraftingMode = isCraftingMode();
        appliedCraftingModeInitialized = true;

        SyncRegistrar sync = syncRegistrar();
        invertedSync = sync.booleanSync("inverted")
            .onServerChange((oldValue, newValue) -> getExtendedPatternTerminal().setInverted(newValue));
        activePageSync = sync.intSync("activePage")
            .onServerChange((oldValue, newValue) -> getExtendedPatternTerminal().setActivePage(newValue));
        processingGridSizeSync = sync.intSync("processingGridSize");
        craftingPinRowsSync = sync.intSync("craftingPinRows");
        playerPinRowsSync = sync.intSync("playerPinRows");
        setInvertedAction = sync.actionC2S("setInverted", StreamCodecs.booleanValue())
            .onServerAction(this::applyInverted);
        setProcessingGridSizeAction = sync.actionC2S("setProcessingGridSize", StreamCodecs.intValue())
            .onServerAction(this::applyProcessingGridSize);
        setPinRowsAction = sync.actionC2S("setPinRows", StreamCodecs.intValue())
            .onServerAction(this::applyPinRows);
        transferRecipeAction = sync.actionC2S("transferRecipe", RecipeTransferPayload.CODEC)
            .onServerAction(this::applyRecipeTransfer);
        placeEncodedPatternAction = sync.actionC2S("placeEncodedPattern", InterfacePatternTarget.CODEC)
            .onServerAction(this::placeEncodedPattern);
        takeBlankPatternAction = sync.actionC2S("takeBlankPattern", StreamCodecs.intValue())
            .onServerAction(action -> clickPatternSlot(blankPatternSlot, action));
        takeEncodedPatternAction = sync.actionC2S("takeEncodedPattern", StreamCodecs.intValue())
            .onServerAction(action -> clickPatternSlot(encodedPatternSlot, action));
        clickInterfacePatternAction = sync.actionC2S("clickInterfacePattern", InterfacePatternTarget.CODEC)
            .onServerAction(target -> performInterfacePatternAction(target, InventoryAction.PICKUP_OR_SET_DOWN));
        shiftClickInterfacePatternAction = sync.actionC2S("shiftClickInterfacePattern", InterfacePatternTarget.CODEC)
            .onServerAction(target -> performInterfacePatternAction(target, InventoryAction.SHIFT_CLICK));
        if (Platform.isServer()) {
            invertedSync.set(getExtendedPatternTerminal().isInverted());
            activePageSync.set(getExtendedPatternTerminal().getActivePage());
            processingGridSizeSync.set(getDualTerminal().getProcessingGridSize());
            int craftingPinRows = getDualTerminal().getCraftingPinRows(getCraftingPinsRows().ordinal());
            int playerPinRows = getDualTerminal().getPlayerPinRows(getPlayerPinsRows().ordinal());
            applyPinRows(packPinRows(craftingPinRows, playerPinRows));
        }

        interfaceDelegate = new ContainerInterfaceTerminal(inventoryPlayer, (IInterfaceTerminal) host);
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            IPatternTerminalEx terminal = getExtendedPatternTerminal();
            invertedSync.set(terminal.isInverted());
            activePageSync.set(terminal.getActivePage());
            processingGridSizeSync.set(getDualTerminal().getProcessingGridSize());
            craftingPinRowsSync.set(getDualTerminal().getCraftingPinRows(getCraftingPinsRows().ordinal()));
            playerPinRowsSync.set(getDualTerminal().getPlayerPinRows(getPlayerPinsRows().ordinal()));
        }
        super.detectAndSendChanges();
        // AE2's interface-terminal container assumes its actionable node can
        // never disappear after construction. A wireless terminal can lose
        // that node while this combined GUI is still open (for example after
        // disconnecting or moving out of range), so do not tick the delegate
        // until the node exists again.
        if (Platform.isServer() && getDualTerminal().getActionableNode() != null) {
            interfaceDelegate.detectAndSendChanges();
        }
    }

    @Override
    public int getPatternInputsWidth() {
        return 4;
    }

    @Override
    public int getPatternInputsHeigh() {
        return 4;
    }

    @Override
    public int getPatternInputPages() {
        return 2;
    }

    @Override
    public int getPatternOutputsWidth() {
        return 4;
    }

    @Override
    public int getPatternOutputsHeigh() {
        return 4;
    }

    @Override
    public int getPatternOutputPages() {
        return 2;
    }

    /**
     * AE2 cannot map this FML-owned container class back through GuiBridge.
     * Preserve the terminal item slot explicitly so crafting amount/confirm
     * sub-screens can return to this exact combined terminal.
     */
    @Override
    public PrimaryGui createPrimaryGui() {
        return new QuickTerminalPrimaryGui(getDualTerminal().getPrimaryGuiIcon(), getDualTerminal().getInventorySlot());
    }

    private IPatternTerminalEx getExtendedPatternTerminal() {
        return (IPatternTerminalEx) getPatternTerminal();
    }

    private DualTerminalGuiObject getDualTerminal() {
        return (DualTerminalGuiObject) getPatternTerminal();
    }

    public void requestInverted(boolean inverted) {
        invertedSync.setLocalValue(inverted);
        setInvertedAction.send(inverted);
    }

    public void requestProcessingGridSize(int gridSize) {
        int normalized = gridSize == 3 ? 3 : 4;
        processingGridSizeSync.setLocalValue(normalized);
        setProcessingGridSizeAction.send(normalized);
    }

    public void requestPinRows(int craftingRows, int playerRows) {
        int normalizedCrafting = clampVisualPinRows(craftingRows, AEConfig.instance.maxCraftingPinRows);
        int normalizedPlayer = clampVisualPinRows(playerRows, AEConfig.instance.maxPlayerPinRows);
        craftingPinRowsSync.setLocalValue(normalizedCrafting);
        playerPinRowsSync.setLocalValue(normalizedPlayer);
        setPinRowsAction.send(packPinRows(normalizedCrafting, normalizedPlayer));
    }

    /** Updates the visible tab immediately; authoritative slot contents arrive from the server action. */
    public void requestRecipeTransfer(RecipeTransferPayload payload) {
        craftingModeSync.setLocalValue(payload.isCrafting());
        if (!payload.isCrafting()) {
            processingGridSizeSync.setLocalValue(payload.getProcessingGridSize());
            invertedSync.setLocalValue(payload.isInverted());
        }
        activePageSync.setLocalValue(0);
        for (int slot = 0; slot < RecipeTransferPayload.SLOT_COUNT; slot++) {
            updateVirtualSlot(appeng.api.storage.StorageName.CRAFTING_INPUT, slot, payload.getInput(slot));
            updateVirtualSlot(appeng.api.storage.StorageName.CRAFTING_OUTPUT, slot, payload.getOutput(slot));
        }
        transferRecipeAction.send(payload);
    }

    public void requestPlaceEncodedPattern(InterfacePatternTarget target) {
        if (target != null) placeEncodedPatternAction.send(target);
    }

    public void requestTakeEncodedPattern(boolean shiftToInventory) {
        takeEncodedPatternAction.send(shiftToInventory ? 2 : 0);
    }

    public void requestInterfacePatternClick(InterfacePatternTarget target, boolean shiftClick) {
        if (target == null) return;
        if (shiftClick) {
            shiftClickInterfacePatternAction.send(target);
        } else {
            clickInterfacePatternAction.send(target);
        }
    }

    private void applyInverted(boolean inverted) {
        getExtendedPatternTerminal().setInverted(inverted);
        invertedSync.set(inverted);
    }

    private void applyProcessingGridSize(int gridSize) {
        int normalized = gridSize == 3 ? 3 : 4;
        int previous = getDualTerminal().getProcessingGridSize();
        getDualTerminal().setProcessingGridSize(normalized);
        processingGridSizeSync.set(normalized);
        boolean currentlyCrafting = appliedCraftingModeInitialized ? appliedCraftingMode : isCraftingMode();
        if (Platform.isServer() && !currentlyCrafting && previous != normalized && normalized == 4) {
            // A 3x3 processing view may intentionally retain the holes of a
            // shaped crafting recipe. The 4x4 view is an unordered processing
            // list, so compact it when that view becomes active. Updating the
            // linked processing snapshot keeps the exact crafting layout in
            // its own representation for a later switch back.
            compactInventory(inputsSync.get());
            compactInventory(outputsSync.get());
            rememberActivePattern(false);
        }
        if (normalized == 3) applyInverted(false);
    }

    private void applyPinRows(int packedRows) {
        int craftingRows = clampVisualPinRows(packedRows & 0xFFFF, AEConfig.instance.maxCraftingPinRows);
        int playerRows = clampVisualPinRows(packedRows >>> 16, AEConfig.instance.maxPlayerPinRows);
        int craftingGroups = groupsForVisualPinRows(craftingRows);
        int playerGroups = groupsForVisualPinRows(playerRows);

        getDualTerminal().setPinRows(craftingRows, playerRows);
        craftingPinRowsSync.set(craftingRows);
        playerPinRowsSync.set(playerRows);
        setPinsRows(PinsRows.fromOrdinal(craftingGroups), PinsRows.fromOrdinal(playerGroups));
    }

    private static int clampVisualPinRows(int rows, int maxNativeGroups) {
        return Math.max(0, Math.min(rows, maxNativeGroups * 9 / 4));
    }

    private static int groupsForVisualPinRows(int rows) {
        return (rows * 4 + 8) / 9;
    }

    private static int packPinRows(int craftingRows, int playerRows) {
        return craftingRows & 0xFFFF | (playerRows & 0xFFFF) << 16;
    }

    @Override
    public void setCraftingMode(boolean craftingMode) {
        // BooleanSyncHandler installs the received value before invoking its
        // server callback. isCraftingMode() therefore already reports the new
        // value here and cannot be used to discover the mode being left.
        boolean wasCrafting = appliedCraftingModeInitialized ? appliedCraftingMode : isCraftingMode();
        if (craftingInputSnapshot == null || wasCrafting == craftingMode || !Platform.isServer()) {
            super.setCraftingMode(craftingMode);
            appliedCraftingMode = craftingMode;
            appliedCraftingModeInitialized = true;
            return;
        }

        IAEStack<?> craftingResult = wasCrafting ? getCraftingResult() : null;
        boolean canRestoreLinkedView = patternSnapshotsLinked && activePatternMatches(wasCrafting);
        rememberActivePattern(wasCrafting);
        super.setCraftingMode(craftingMode);
        if (craftingMode) {
            if (!canRestoreLinkedView || !craftingSnapshotValid) seedCraftingFromProcessing();
            restorePattern(craftingInputSnapshot, craftingOutputSnapshot);
            // AE2 copied the old processing inventory into its crafting matrix
            // before the snapshot was restored. A second native update rebuilds
            // the matrix and its temporary crafting-result slot from the
            // restored 3x3 recipe. The snapshot itself remains untouched, so
            // processing quantities can still be restored exactly later.
            super.setCraftingMode(true);
        } else {
            if (!canRestoreLinkedView || !processingSnapshotValid) {
                seedProcessingFromCrafting(craftingResult);
            }
            restorePattern(processingInputSnapshot, processingOutputSnapshot);
        }
        patternSnapshotsLinked = true;
        persistPatternSnapshots();
        appliedCraftingMode = craftingMode;
        appliedCraftingModeInitialized = true;
    }

    private void loadPatternSnapshots() {
        DualTerminalGuiObject terminal = getDualTerminal();
        craftingSnapshotValid = terminal.hasPatternSnapshot(true);
        processingSnapshotValid = terminal.hasPatternSnapshot(false);
        patternSnapshotsLinked = terminal.arePatternSnapshotsLinked();
        copyArray(terminal.getPatternSnapshotInputs(true, craftingInputSnapshot.length), craftingInputSnapshot);
        copyArray(terminal.getPatternSnapshotOutputs(true, craftingOutputSnapshot.length), craftingOutputSnapshot);
        copyArray(terminal.getPatternSnapshotInputs(false, processingInputSnapshot.length), processingInputSnapshot);
        copyArray(terminal.getPatternSnapshotOutputs(false, processingOutputSnapshot.length), processingOutputSnapshot);

        if (isCraftingMode()) {
            patternSnapshotsLinked &= activePatternMatches(true);
            copyInventory(inputsSync.get(), craftingInputSnapshot);
            // Crafting uses AE2's separate computed cOut slot. Anything left in
            // the shared processing-output inventory is stale and must not
            // become part of the crafting snapshot.
            clearArray(craftingOutputSnapshot);
            craftingSnapshotValid = true;
            migrateLegacyProcessingFluids();
        } else {
            patternSnapshotsLinked &= activePatternMatches(false);
            copyInventory(inputsSync.get(), processingInputSnapshot);
            copyInventory(outputsSync.get(), processingOutputSnapshot);
            processingSnapshotValid = true;
        }
        terminal.setPatternSnapshotsLinked(patternSnapshotsLinked);
    }

    private void migrateLegacyProcessingFluids() {
        if (processingSnapshotValid) return;
        IAEStack<?>[] legacyFluids = getDualTerminal().getProcessingFluidInputs(processingInputSnapshot.length);
        if (!containsStack(legacyFluids)) return;

        copyInventory(inputsSync.get(), processingInputSnapshot);
        copyInventory(outputsSync.get(), processingOutputSnapshot);
        for (int slot = 0; slot < legacyFluids.length; slot++) {
            if (legacyFluids[slot] != null) processingInputSnapshot[slot] = legacyFluids[slot].copy();
        }
        processingSnapshotValid = true;
        persistPatternSnapshot(false);
    }

    private void rememberActivePattern(boolean crafting) {
        IAEStack<?>[] inputs = crafting ? craftingInputSnapshot : processingInputSnapshot;
        IAEStack<?>[] outputs = crafting ? craftingOutputSnapshot : processingOutputSnapshot;
        copyInventory(inputsSync.get(), inputs);
        if (crafting) {
            clearArray(outputs);
        } else {
            copyInventory(outputsSync.get(), outputs);
        }
        if (crafting) {
            craftingSnapshotValid = true;
        } else {
            processingSnapshotValid = true;
        }
        persistPatternSnapshot(crafting);
    }

    private void seedCraftingFromProcessing() {
        clearArray(craftingInputSnapshot);
        clearArray(craftingOutputSnapshot);
        int target = 0;
        for (IAEStack<?> stack : processingInputSnapshot) {
            if (!(stack instanceof IAEItemStack) || target >= 9) continue;
            craftingInputSnapshot[target++] = stack.copy();
        }
        craftingSnapshotValid = true;
        persistPatternSnapshot(true);
    }

    private void seedProcessingFromCrafting(IAEStack<?> craftingResult) {
        clearArray(processingInputSnapshot);
        clearArray(processingOutputSnapshot);
        boolean compact = getDualTerminal().getProcessingGridSize() == 4;
        int packedSlot = 0;
        for (int sourceSlot = 0; sourceSlot < 9; sourceSlot++) {
            IAEStack<?> stack = craftingInputSnapshot[sourceSlot];
            if (stack == null) continue;
            int targetSlot = compact ? packedSlot++ : sourceSlot;
            processingInputSnapshot[targetSlot] = stack.copy();
        }
        if (craftingResult != null) processingOutputSnapshot[0] = craftingResult.copy();
        processingSnapshotValid = true;
        persistPatternSnapshot(false);
    }

    private IAEStack<?> getCraftingResult() {
        for (Object value : inventorySlots) {
            if (!(value instanceof appeng.container.slot.SlotPatternTerm slot)) continue;
            ItemStack result = slot.getStack();
            return result == null ? null : AEItemStack.create(result.copy());
        }
        return null;
    }

    private void restorePattern(IAEStack<?>[] inputSnapshot, IAEStack<?>[] outputSnapshot) {
        restoreInventory(inputsSync.get(), inputSnapshot);
        restoreInventory(outputsSync.get(), outputSnapshot);
        inputsSync.markDirty();
        outputsSync.markDirty();
    }

    private void persistPatternSnapshot(boolean crafting) {
        getDualTerminal().setPatternSnapshot(
            crafting,
            crafting ? craftingInputSnapshot : processingInputSnapshot,
            crafting ? craftingOutputSnapshot : processingOutputSnapshot);
    }

    private void persistPatternSnapshots() {
        persistPatternSnapshot(true);
        persistPatternSnapshot(false);
        getDualTerminal().setPatternSnapshotsLinked(patternSnapshotsLinked);
    }

    private boolean activePatternMatches(boolean crafting) {
        if (crafting) {
            return craftingSnapshotValid && craftingInventoryMatches(inputsSync.get(), craftingInputSnapshot);
        }
        return processingSnapshotValid && inventoryMatches(inputsSync.get(), processingInputSnapshot)
            && inventoryMatches(outputsSync.get(), processingOutputSnapshot);
    }

    private static boolean craftingInventoryMatches(IAEStackInventory inventory, IAEStack<?>[] snapshot) {
        int size = Math.max(inventory.getSizeInventory(), snapshot.length);
        for (int slot = 0; slot < size; slot++) {
            IAEStack<?> active = slot < inventory.getSizeInventory() ? inventory.getAEStackInSlot(slot) : null;
            IAEStack<?> saved = slot < snapshot.length ? snapshot[slot] : null;
            if (active == null || saved == null) {
                if (active != saved) return false;
            } else if (!(active instanceof IAEItemStack activeItem) || !(saved instanceof IAEItemStack savedItem)
                || !activeItem.isSameType(savedItem)) {
                    return false;
                }
        }
        return true;
    }

    private static boolean inventoryMatches(IAEStackInventory inventory, IAEStack<?>[] snapshot) {
        int size = Math.max(inventory.getSizeInventory(), snapshot.length);
        for (int slot = 0; slot < size; slot++) {
            IAEStack<?> active = slot < inventory.getSizeInventory() ? inventory.getAEStackInSlot(slot) : null;
            IAEStack<?> saved = slot < snapshot.length ? snapshot[slot] : null;
            if (active == null || saved == null) {
                if (active != saved) return false;
            } else if (!active.toNBTGeneric()
                .equals(saved.toNBTGeneric())) {
                    return false;
                }
        }
        return true;
    }

    private static void copyInventory(IAEStackInventory source, IAEStack<?>[] destination) {
        clearArray(destination);
        int size = Math.min(source.getSizeInventory(), destination.length);
        for (int slot = 0; slot < size; slot++) {
            IAEStack<?> stack = source.getAEStackInSlot(slot);
            destination[slot] = stack == null ? null : stack.copy();
        }
    }

    private static void restoreInventory(IAEStackInventory destination, IAEStack<?>[] source) {
        for (int slot = 0; slot < destination.getSizeInventory(); slot++) {
            IAEStack<?> stack = slot < source.length ? source[slot] : null;
            destination.putAEStackInSlot(slot, stack == null ? null : stack.copy());
        }
    }

    private static void compactInventory(IAEStackInventory inventory) {
        IAEStack<?>[] compacted = new IAEStack<?>[inventory.getSizeInventory()];
        int target = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            IAEStack<?> stack = inventory.getAEStackInSlot(slot);
            if (stack != null) compacted[target++] = stack.copy();
        }
        restoreInventory(inventory, compacted);
        inventory.markDirty();
    }

    private static void copyArray(IAEStack<?>[] source, IAEStack<?>[] destination) {
        clearArray(destination);
        int size = Math.min(source.length, destination.length);
        for (int slot = 0; slot < size; slot++) {
            destination[slot] = source[slot] == null ? null : source[slot].copy();
        }
    }

    private static void clearArray(IAEStack<?>[] stacks) {
        java.util.Arrays.fill(stacks, null);
    }

    private static boolean containsStack(IAEStack<?>[] stacks) {
        for (IAEStack<?> stack : stacks) {
            if (stack != null) return true;
        }
        return false;
    }

    private void applyRecipeTransfer(RecipeTransferPayload payload) {
        if (!payload.isCrafting()) {
            applyProcessingGridSize(payload.getProcessingGridSize());
            applyInverted(payload.isInverted());
        }
        setCraftingMode(payload.isCrafting());
        getExtendedPatternTerminal().setActivePage(0);
        activePageSync.set(0);

        for (int slot = 0; slot < RecipeTransferPayload.SLOT_COUNT; slot++) {
            updateVirtualSlot(appeng.api.storage.StorageName.CRAFTING_INPUT, slot, payload.getInput(slot));
            updateVirtualSlot(appeng.api.storage.StorageName.CRAFTING_OUTPUT, slot, payload.getOutput(slot));
        }
        rememberActivePattern(payload.isCrafting());
        // A NEI transfer establishes a new authoritative recipe. The other
        // representation is regenerated from it on the next mode switch.
        patternSnapshotsLinked = false;
        getDualTerminal().setPatternSnapshotsLinked(false);
        saveChanges();
        if (payload.shouldEncode()) encode();
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        if (Platform.isServer()) {
            boolean activeMode = appliedCraftingModeInitialized ? appliedCraftingMode : isCraftingMode();
            patternSnapshotsLinked &= activePatternMatches(activeMode);
            rememberActivePattern(activeMode);
            getDualTerminal().setPatternSnapshotsLinked(patternSnapshotsLinked);
        }
        super.onContainerClosed(player);
    }

    /**
     * Moves the freshly encoded pattern straight into the highlighted interface
     * slot. The target id comes from this delegate's own synchronized entry list,
     * so no world-coordinate lookup or cross-dimensional tile access is needed.
     */
    private void placeEncodedPattern(InterfacePatternTarget target) {
        if (encodedPatternSlot == null || !encodedPatternSlot.getHasStack() || TRACKED_BY_ID == null) return;
        try {
            Object tracker = ((Map<?, ?>) TRACKED_BY_ID.get(interfaceDelegate)).get(target.getEntryId());
            if (tracker == null) return;
            Field patternsField = findField(tracker.getClass(), "patterns");
            if (patternsField == null) return;
            IInventory patterns = (IInventory) patternsField.get(tracker);
            int slot = target.getSlot();
            if (slot < 0 || slot >= patterns.getSizeInventory() || patterns.getStackInSlot(slot) != null) return;

            ItemStack pattern = encodedPatternSlot.getStack();
            if (pattern == null || !patterns.isItemValidForSlot(slot, pattern)) return;
            InventoryPlayer playerInventory = getPlayerInv();
            if (!(playerInventory.player instanceof EntityPlayerMP player) || playerInventory.getItemStack() != null)
                return;

            // ContainerInterfaceTerminal.doAction is the authoritative path:
            // besides changing the real interface inventory it also updates
            // InvTracker's NBT cache and queues the exact client-side slot delta.
            encodedPatternSlot.putStack(null);
            playerInventory.setItemStack(pattern.copy());
            interfaceDelegate.doAction(player, InventoryAction.PICKUP_OR_SET_DOWN, slot, target.getEntryId());
            if (playerInventory.getItemStack() != null) {
                // The target changed between selection and this action. Restore
                // the encoder output instead of leaving an unexpected cursor item.
                encodedPatternSlot.putStack(playerInventory.getItemStack());
                playerInventory.setItemStack(null);
                updateHeld(player);
                return;
            }
            interfaceDelegate.scheduleUpdate();
            queuePatternOutputs(pattern);
            refreshCraftingAvailability(target);
            detectAndSendChanges();
        } catch (IllegalAccessException ignored) {}
    }

    /**
     * These slots are outside GuiContainer's native rectangle. Send one explicit
     * action, but let AE2/Minecraft perform the real click so item validation,
     * right-click splitting and shift transfer remain identical to the original
     * pattern terminal.
     */
    private void clickPatternSlot(SlotRestrictedInput patternSlot, int action) {
        if (patternSlot == null || !(getPlayerInv().player instanceof EntityPlayerMP player)) return;
        int mouseButton = action & 1;
        int clickMode = (action & 2) == 0 ? 0 : 1;
        super.slotClick(patternSlot.slotNumber, mouseButton, clickMode, player);
        updateHeld(player);
        detectAndSendChanges();
    }

    private void performInterfacePatternAction(InterfacePatternTarget target, InventoryAction action) {
        if (!(getPlayerInv().player instanceof EntityPlayerMP player) || !isInterfaceEntry(target.getEntryId())) return;
        IInventory patterns = trackedPatterns(target.getEntryId());
        ItemStack previous = stackAt(patterns, target.getSlot());
        interfaceDelegate.doAction(player, action, target.getSlot(), target.getEntryId());
        interfaceDelegate.scheduleUpdate();
        refreshCraftingAvailability(target);
        queuePatternOutputs(previous);
        queuePatternOutputs(stackAt(patterns, target.getSlot()));
        detectAndSendChanges();
    }

    private IInventory trackedPatterns(long entryId) {
        if (TRACKED_BY_ID == null) return null;
        try {
            Object tracker = ((Map<?, ?>) TRACKED_BY_ID.get(interfaceDelegate)).get(entryId);
            if (tracker == null) return null;
            Field patternsField = findField(tracker.getClass(), "patterns");
            Object patterns = patternsField == null ? null : patternsField.get(tracker);
            return patterns instanceof IInventory inventory ? inventory : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static ItemStack stackAt(IInventory inventory, int slot) {
        if (inventory == null || slot < 0 || slot >= inventory.getSizeInventory()) return null;
        ItemStack stack = inventory.getStackInSlot(slot);
        return stack == null ? null : stack.copy();
    }

    /**
     * Crafting-cache rebuilds do not necessarily include products which just
     * disappeared. Queue the affected products explicitly so the monitor sends
     * either their new state or a zero-sized removal to the embedded storage UI.
     */
    private void queuePatternOutputs(ItemStack pattern) {
        if (pattern == null || !(pattern.getItem() instanceof ICraftingPatternItem patternItem)) return;
        ICraftingPatternDetails details = patternItem.getPatternForItem(pattern, getPlayerInv().player.worldObj);
        if (details == null) return;
        List<IAEStack<?>> changed = new ArrayList<>();
        for (IAEStack<?> output : details.getAEOutputs()) {
            if (output != null) changed.add(
                output.copy()
                    .reset());
        }
        if (!changed.isEmpty()) postChange(null, changed, getActionSource());
    }

    private void refreshCraftingAvailability(InterfacePatternTarget target) {
        if (TRACKED == null || TRACKED_BY_ID == null || getDualTerminal().getActionableNode() == null) return;
        try {
            Object tracker = ((Map<?, ?>) TRACKED_BY_ID.get(interfaceDelegate)).get(target.getEntryId());
            if (tracker == null) return;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) TRACKED.get(interfaceDelegate)).entrySet()) {
                if (entry.getValue() != tracker || !(entry.getKey() instanceof IInterfaceViewable viewable)) continue;
                if (viewable instanceof IInterfaceHost interfaceHost) {
                    interfaceHost.getInterfaceDuality()
                        .updateCraftingList();
                }
                IGridNode node = interfaceNode(viewable);
                ICraftingProvider provider = viewable instanceof ICraftingProvider craftingProvider ? craftingProvider
                    : null;
                (node == null ? getDualTerminal().getActionableNode()
                    .getGrid() : node.getGrid()).postEvent(new MENetworkCraftingPatternChange(provider, node));
                return;
            }
        } catch (IllegalAccessException ignored) {}
    }

    private static IGridNode interfaceNode(IInterfaceViewable viewable) {
        for (ForgeDirection side : ForgeDirection.values()) {
            IGridNode node = viewable.getGridNode(side);
            if (node != null) return node;
        }
        return null;
    }

    private SlotRestrictedInput findPatternSlot(SlotRestrictedInput.PlacableItemType type) {
        for (Object value : inventorySlots) {
            if (value instanceof SlotRestrictedInput slot && slot.getItemType() == type) {
                return slot;
            }
        }
        return null;
    }

    @Override
    public void encode() {
        if (isCraftingMode()) {
            encodeWithInventorySizes(9, -1);
            return;
        }
        if (processingGridSizeSync.get() != 3) {
            super.encode();
            return;
        }

        IAEStackInventory inputs = inputsSync.get();
        IAEStackInventory outputs = outputsSync.get();
        IAEStack<?>[] hiddenInputs = new IAEStack<?>[inputs.getSizeInventory()];
        IAEStack<?>[] hiddenOutputs = new IAEStack<?>[outputs.getSizeInventory()];
        for (int slot = 0; slot < inputs.getSizeInventory(); slot++) {
            if (slot >= 9) {
                hiddenInputs[slot] = inputs.getAEStackInSlot(slot);
                inputs.putAEStackInSlot(slot, null);
            }
        }
        for (int slot = 0; slot < outputs.getSizeInventory(); slot++) {
            if (slot >= 3) {
                hiddenOutputs[slot] = outputs.getAEStackInSlot(slot);
                outputs.putAEStackInSlot(slot, null);
            }
        }
        try {
            encodeWithInventorySizes(9, 3);
        } finally {
            for (int slot = 0; slot < hiddenInputs.length; slot++) {
                if (hiddenInputs[slot] != null) inputs.putAEStackInSlot(slot, hiddenInputs[slot]);
            }
            for (int slot = 0; slot < hiddenOutputs.length; slot++) {
                if (hiddenOutputs[slot] != null) outputs.putAEStackInSlot(slot, hiddenOutputs[slot]);
            }
            inputsSync.markDirty();
            outputsSync.markDirty();
        }
    }

    /**
     * PatternEncodingHelper serializes every slot reported by the backing AE
     * inventories. This terminal keeps 32 slots allocated for 4x4 processing,
     * but a normal crafting pattern must contain exactly the visible 3x3 input
     * grid. Temporarily expose the active logical sizes while encoding without
     * reallocating or discarding the hidden slot contents.
     */
    private void encodeWithInventorySizes(int inputSize, int outputSize) {
        if (AE_STACK_INVENTORY_SIZE == null) {
            throw new IllegalStateException("Unable to access AE2 pattern inventory size");
        }
        IAEStackInventory inputs = inputsSync.get();
        IAEStackInventory outputs = outputsSync.get();
        int oldInputSize = -1;
        int oldOutputSize = -1;
        try {
            oldInputSize = AE_STACK_INVENTORY_SIZE.getInt(inputs);
            oldOutputSize = AE_STACK_INVENTORY_SIZE.getInt(outputs);
            AE_STACK_INVENTORY_SIZE.setInt(inputs, inputSize);
            if (outputSize >= 0) AE_STACK_INVENTORY_SIZE.setInt(outputs, outputSize);
            super.encode();
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to resize AE2 pattern inventory for encoding", exception);
        } finally {
            try {
                if (oldInputSize >= 0) AE_STACK_INVENTORY_SIZE.setInt(inputs, oldInputSize);
                if (oldOutputSize >= 0 && outputSize >= 0) AE_STACK_INVENTORY_SIZE.setInt(outputs, oldOutputSize);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Unable to restore AE2 pattern inventory size", exception);
            }
        }
    }

    /**
     * AE2's hybrid container assumes the backing crafting matrix has exactly the
     * same size as its input inventory while its recipe calculation still uses
     * only the first 3x3 cells. Expand that existing object (which is also held by
     * SlotPatternTerm) so switching between 3x3 crafting and 4x4x2 processing is
     * safe.
     */
    private void expandCraftingMatrix() {
        if (CRAFTING_MATRIX == null || INTERNAL_SIZE == null || INTERNAL_ITEMS == null) {
            throw new IllegalStateException("Unable to access AE2 crafting matrix fields");
        }
        try {
            IInventory matrix = (IInventory) CRAFTING_MATRIX.get(this);
            int size = getPatternInputsWidth() * getPatternInputsHeigh() * getPatternInputPages();
            INTERNAL_SIZE.setInt(matrix, size);
            INTERNAL_ITEMS.set(matrix, new ItemStack[size]);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to expand AE2 crafting matrix", exception);
        }
    }

    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        if (isInterfaceEntry(id)) {
            interfaceDelegate.doAction(player, action, slot, id);
        } else {
            super.doAction(player, action, slot, id);
        }
    }

    private boolean isInterfaceEntry(long id) {
        if (TRACKED_BY_ID == null) return false;
        try {
            return ((Map<?, ?>) TRACKED_BY_ID.get(interfaceDelegate)).containsKey(id);
        } catch (IllegalAccessException ignored) {
            return false;
        }
    }

    private static Field findTrackedById() {
        return findField(ContainerInterfaceTerminal.class, "trackedById");
    }

    private static Field findField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static final class QuickTerminalPrimaryGui extends PrimaryGui {

        private QuickTerminalPrimaryGui(ItemStack icon, int terminalSlot) {
            super(null, icon, null, ForgeDirection.UNKNOWN);
            setSlotIndex(terminalSlot);
        }

        @Override
        public void open(EntityPlayer player) {
            ItemDualTerminal.openChecked(player, slotIndex, false);
        }
    }
}

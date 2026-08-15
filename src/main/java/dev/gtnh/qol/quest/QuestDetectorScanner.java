package dev.gtnh.qol.quest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.GridAccessException;
import appeng.util.item.AEFluidStackType;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.party.IParty;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.party.PartyManager;
import bq_standard.tasks.TaskFluid;
import bq_standard.tasks.TaskRetrieval;

final class QuestDetectorScanner {

    private static final Logger LOGGER = LogManager.getLogger("GTNH-QoL-QuestDetector");
    private static final int MAX_WATCHER_INTERESTS = 512;
    private static final long DUPLICATE_SCAN_WINDOW = 20L;
    private static final Map<IGrid, Map<BindingKey, Long>> LAST_SCANS = new WeakHashMap<>();
    private static final Set<String> REPORTED_TASK_ERRORS = new HashSet<>();

    private QuestDetectorScanner() {}

    static boolean scan(TileQuestDetector detector, long now) {
        ParticipantInfo participant = findParticipant(detector);
        if (participant == null) return true;

        IGrid grid;
        IStorageGrid storage;
        try {
            grid = detector.getProxy()
                .getGrid();
            storage = detector.getProxy()
                .getStorage();
        } catch (GridAccessException ignored) {
            return true;
        }

        BindingKey binding = new BindingKey(detector.getOwnerId(), detector.getOwnerPartyId());
        synchronized (LAST_SCANS) {
            Map<BindingKey, Long> gridScans = LAST_SCANS.computeIfAbsent(grid, ignored -> new HashMap<>());
            Long previous = gridScans.get(binding);
            if (previous != null && now - previous < DUPLICATE_SCAN_WINDOW) return true;
            gridScans.put(binding, now);
        }

        List<TaskRef> tasks = QuestTaskIndex.getActiveTasks(participant, now);
        detector.setWatcherInterests(buildWatcherInterests(tasks));
        if (tasks.isEmpty()) return true;

        StorageSnapshot snapshot = StorageSnapshot.capture(storage, tasks);
        for (TaskRef ref : tasks) {
            try {
                if (ref.task instanceof TaskRetrieval retrieval) {
                    processItemTask(retrieval, ref, participant, snapshot);
                } else if (ref.task instanceof TaskFluid fluid) {
                    processFluidTask(fluid, ref, participant, snapshot);
                }
            } catch (RuntimeException e) {
                reportTaskError(ref, e);
            }
        }
        return true;
    }

    private static ParticipantInfo findParticipant(TileQuestDetector detector) {
        int partyId = detector.getOwnerPartyId();
        if (partyId >= 0) {
            IParty party = PartyManager.INSTANCE.getValue(partyId);
            if (party == null) return null;
            for (UUID member : party.getMembers()) {
                EntityPlayerMP memberPlayer = QuestingAPI.getPlayer(member);
                if (memberPlayer != null) return new ParticipantInfo(memberPlayer);
            }
            return null;
        }

        EntityPlayerMP owner = QuestingAPI.getPlayer(detector.getOwnerId());
        return owner == null ? null : new ParticipantInfo(owner);
    }

    private static Collection<IAEStack> buildWatcherInterests(List<TaskRef> tasks) {
        Set<IAEStack> interests = new HashSet<>();
        for (TaskRef ref : tasks) {
            if (interests.size() >= MAX_WATCHER_INTERESTS) break;
            if (ref.task instanceof TaskRetrieval retrieval) {
                for (BigItemStack required : retrieval.requiredItems) {
                    addItemInterest(interests, required.getBaseStack());
                    IAEFluidStack contained = getContainedFluid(required.getBaseStack());
                    if (contained != null) interests.add(contained);
                    for (ItemStack matching : QuestItemMatcher.getOreStacks(required)) {
                        addItemInterest(interests, matching);
                        if (interests.size() >= MAX_WATCHER_INTERESTS) break;
                    }
                    if (interests.size() >= MAX_WATCHER_INTERESTS) break;
                }
            } else if (ref.task instanceof TaskFluid fluid) {
                for (FluidStack required : fluid.requiredFluids) {
                    IAEFluidStack aeFluid = AEApi.instance()
                        .storage()
                        .createFluidStack(required);
                    if (aeFluid != null) interests.add(aeFluid);
                    if (interests.size() >= MAX_WATCHER_INTERESTS) break;
                }
            }
        }
        return interests;
    }

    private static void addItemInterest(Set<IAEStack> interests, ItemStack stack) {
        if (stack == null || stack.getItem() == null) return;
        IAEItemStack aeStack = AEApi.instance()
            .storage()
            .createItemStack(stack);
        if (aeStack != null) interests.add(aeStack);
    }

    private static void processItemTask(TaskRetrieval task, TaskRef ref, ParticipantInfo participant,
        StorageSnapshot snapshot) {
        LinkedHashSet<StoredItem> candidates = new LinkedHashSet<>();
        for (BigItemStack required : task.requiredItems) {
            snapshot.addItemsFor(required.getBaseStack(), candidates);
            for (ItemStack matching : QuestItemMatcher.getOreStacks(required)) {
                snapshot.addItemsFor(matching, candidates);
            }
        }

        List<ItemStack> detected = new ArrayList<>(candidates.size() + task.requiredItems.size());
        for (StoredItem candidate : candidates) detected.add(candidate.asItemStack());
        addNativeFluidContainers(task, candidates, detected, snapshot);
        task.retrieveItems(participant, ref.questEntry, detected.toArray(new ItemStack[0]));
    }

    private static void addNativeFluidContainers(TaskRetrieval task, Collection<StoredItem> actualItems,
        List<ItemStack> detected, StorageSnapshot snapshot) {
        long[] covered = estimateItemCoverage(task, actualItems);
        Map<StoredFluid, Long> reservations = new IdentityHashMap<>();

        for (int i = 0; i < task.requiredItems.size(); i++) {
            BigItemStack required = task.requiredItems.get(i);
            long missing = Math.max(0L, (long) required.stackSize - covered[i]);
            if (missing <= 0L) continue;

            IAEFluidStack contained = getContainedFluid(required.getBaseStack());
            if (contained == null || contained.getStackSize() <= 0L) continue;
            FluidStack wanted = contained.getFluidStack();
            long units = snapshot.reserveNativeFluidUnits(wanted, contained.getStackSize(), missing, reservations);
            if (units <= 0L) continue;

            ItemStack synthetic = required.getBaseStack()
                .copy();
            synthetic.stackSize = clampToPositiveInt(units);
            detected.add(synthetic);
        }
    }

    private static long[] estimateItemCoverage(TaskRetrieval task, Collection<StoredItem> actualItems) {
        long[] covered = new long[task.requiredItems.size()];
        for (StoredItem stored : actualItems) {
            long remaining = stored.amount;
            for (int i = 0; i < task.requiredItems.size() && remaining > 0L; i++) {
                BigItemStack required = task.requiredItems.get(i);
                if (!matches(task, required, stored.sample)) continue;
                long needed = Math.max(0L, (long) required.stackSize - covered[i]);
                long used = Math.min(needed, remaining);
                covered[i] += used;
                remaining -= used;
            }
        }
        return covered;
    }

    private static boolean matches(TaskRetrieval task, BigItemStack required, ItemStack stack) {
        return QuestItemMatcher.matches(task, required, stack);
    }

    private static void processFluidTask(TaskFluid task, TaskRef ref, ParticipantInfo participant,
        StorageSnapshot snapshot) {
        Set<StoredFluid> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
        for (FluidStack required : task.requiredFluids) snapshot.addFluidsFor(required, candidates);

        List<FluidStack> detected = new ArrayList<>(candidates.size());
        for (StoredFluid candidate : candidates) detected.add(candidate.asFluidStack());
        task.retrieveFluids(participant, ref.questEntry, detected.toArray(new FluidStack[0]));
    }

    private static IAEFluidStack getContainedFluid(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        ItemStack single = stack.copy();
        single.stackSize = 1;
        return AEFluidStackType.FLUID_STACK_TYPE.getStackFromContainerItem(single);
    }

    private static int clampToPositiveInt(long amount) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, amount));
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static void reportTaskError(TaskRef ref, RuntimeException error) {
        String key = ref.questEntry.getKey() + ":"
            + ref.task.getClass()
                .getName();
        synchronized (REPORTED_TASK_ERRORS) {
            if (!REPORTED_TASK_ERRORS.add(key)) return;
        }
        LOGGER.warn(
            "Unable to check BetterQuesting task {} in quest {}",
            ref.task.getClass()
                .getName(),
            ref.questEntry.getKey(),
            error);
    }

    private static final class BindingKey {

        private final UUID owner;
        private final int partyId;

        private BindingKey(UUID owner, int partyId) {
            this.owner = owner;
            this.partyId = partyId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BindingKey key)) return false;
            if (partyId >= 0 || key.partyId >= 0) return partyId >= 0 && partyId == key.partyId;
            return owner.equals(key.owner);
        }

        @Override
        public int hashCode() {
            return partyId >= 0 ? partyId : owner.hashCode();
        }
    }

    private static final class TaskRef {

        private final Map.Entry<UUID, IQuest> questEntry;
        private final ITask task;

        private TaskRef(Map.Entry<UUID, IQuest> questEntry, ITask task) {
            this.questEntry = questEntry;
            this.task = task;
        }
    }

    private static final class QuestTaskIndex {

        private static final long INDEX_CHECK_INTERVAL = 200L;
        private static final long FORCED_REBUILD_INTERVAL = 1200L;
        private static Map<UUID, List<TaskRef>> tasksByQuest = Collections.emptyMap();
        private static int definitionSignature;
        private static long lastCheck = Long.MIN_VALUE / 2L;
        private static long lastBuild = Long.MIN_VALUE / 2L;

        private QuestTaskIndex() {}

        private static synchronized List<TaskRef> getActiveTasks(ParticipantInfo participant, long now) {
            refresh(now);
            Set<UUID> activeQuests = participant.getSharedQuests();
            List<TaskRef> result = new ArrayList<>();
            for (UUID questId : activeQuests) {
                List<TaskRef> refs = tasksByQuest.get(questId);
                if (refs == null || refs.isEmpty()) continue;
                IQuest quest = refs.get(0).questEntry.getValue();
                if (!quest.isUnlocked(participant.UUID) || quest.isComplete(participant.UUID)) continue;
                for (TaskRef ref : refs) {
                    if (!ref.task.isComplete(participant.UUID)) result.add(ref);
                }
            }
            return result;
        }

        private static void refresh(long now) {
            if (now < lastCheck || now < lastBuild) {
                tasksByQuest = Collections.emptyMap();
                definitionSignature = 0;
                lastCheck = Long.MIN_VALUE / 2L;
                lastBuild = Long.MIN_VALUE / 2L;
            }
            if (now - lastCheck < INDEX_CHECK_INTERVAL && !tasksByQuest.isEmpty()) return;
            lastCheck = now;

            int signature = QuestDatabase.INSTANCE.size();
            for (Map.Entry<UUID, IQuest> entry : QuestDatabase.INSTANCE.entrySet()) {
                signature = 31 * signature + System.identityHashCode(entry.getValue());
                signature = 31 * signature + entry.getValue()
                    .getTasks()
                    .size();
            }
            if (signature == definitionSignature && now - lastBuild < FORCED_REBUILD_INTERVAL
                && !tasksByQuest.isEmpty()) return;

            Map<UUID, List<TaskRef>> rebuilt = new HashMap<>();
            for (Map.Entry<UUID, IQuest> entry : QuestDatabase.INSTANCE.entrySet()) {
                for (DBEntry<ITask> taskEntry : entry.getValue()
                    .getTasks()
                    .getEntries()) {
                    ITask task = taskEntry.getValue();
                    if (task instanceof TaskRetrieval retrieval && !retrieval.consume
                        || task instanceof TaskFluid fluid && !fluid.consume) {
                        rebuilt.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                            .add(new TaskRef(entry, task));
                    }
                }
            }
            tasksByQuest = rebuilt;
            definitionSignature = signature;
            lastBuild = now;
        }
    }

    private static final class StorageSnapshot {

        private final Map<Item, List<StoredItem>> itemsByItem = new HashMap<>();
        private final Map<Fluid, List<StoredFluid>> allFluidsByFluid = new HashMap<>();
        private final Map<Fluid, List<StoredFluid>> nativeFluidsByFluid = new HashMap<>();

        private static StorageSnapshot capture(IStorageGrid storage, List<TaskRef> tasks) {
            StorageSnapshot snapshot = new StorageSnapshot();
            boolean needContainerFluids = tasks.stream()
                .anyMatch(ref -> ref.task instanceof TaskFluid);

            IMEMonitor<IAEItemStack> itemMonitor = storage.getItemInventory();
            if (itemMonitor != null) {
                for (IAEItemStack aeStack : itemMonitor.getStorageList()) {
                    long amount = aeStack.getStackSize();
                    if (amount <= 0L) continue;
                    ItemStack sample = aeStack.getItemStack();
                    if (sample == null || sample.getItem() == null) continue;
                    sample.stackSize = 1;
                    StoredItem stored = new StoredItem(sample, amount);
                    snapshot.itemsByItem.computeIfAbsent(sample.getItem(), ignored -> new ArrayList<>())
                        .add(stored);

                    if (needContainerFluids && isFilledFluidContainer(sample)) {
                        IAEFluidStack contained = getContainedFluid(sample);
                        if (contained != null && contained.getStackSize() > 0L) {
                            snapshot.addFluid(
                                contained.getFluidStack(),
                                saturatedMultiply(contained.getStackSize(), amount),
                                false);
                        }
                    }
                }
            }

            IMEMonitor<IAEFluidStack> fluidMonitor = storage.getFluidInventory();
            if (fluidMonitor != null) {
                for (IAEFluidStack aeStack : fluidMonitor.getStorageList()) {
                    if (aeStack.getStackSize() <= 0L) continue;
                    snapshot.addFluid(aeStack.getFluidStack(), aeStack.getStackSize(), true);
                }
            }
            return snapshot;
        }

        private static boolean isFilledFluidContainer(ItemStack stack) {
            return stack.getItem() instanceof IFluidContainerItem || FluidContainerRegistry.isFilledContainer(stack);
        }

        private void addFluid(FluidStack sample, long amount, boolean nativeFluid) {
            if (sample == null || sample.getFluid() == null || amount <= 0L) return;
            StoredFluid stored = new StoredFluid(sample, amount);
            allFluidsByFluid.computeIfAbsent(sample.getFluid(), ignored -> new ArrayList<>())
                .add(stored);
            if (nativeFluid) {
                nativeFluidsByFluid.computeIfAbsent(sample.getFluid(), ignored -> new ArrayList<>())
                    .add(stored);
            }
        }

        private void addItemsFor(ItemStack required, Collection<StoredItem> output) {
            if (required == null || required.getItem() == null) return;
            List<StoredItem> stored = itemsByItem.get(required.getItem());
            if (stored != null) output.addAll(stored);
        }

        private void addFluidsFor(FluidStack required, Collection<StoredFluid> output) {
            if (required == null || required.getFluid() == null) return;
            List<StoredFluid> stored = allFluidsByFluid.get(required.getFluid());
            if (stored != null) output.addAll(stored);
        }

        private long reserveNativeFluidUnits(FluidStack required, long unitAmount, long maxUnits,
            Map<StoredFluid, Long> reservations) {
            if (required == null || required.getFluid() == null || unitAmount <= 0L || maxUnits <= 0L) return 0L;
            List<StoredFluid> stored = nativeFluidsByFluid.get(required.getFluid());
            if (stored == null) return 0L;

            long available = 0L;
            for (StoredFluid fluid : stored) {
                if (!required.isFluidEqual(fluid.sample)) continue;
                long reserved = reservations.getOrDefault(fluid, 0L);
                available = saturatedAdd(available, Math.max(0L, fluid.amount - reserved));
            }
            long units = Math.min(maxUnits, available / unitAmount);
            long toReserve = saturatedMultiply(units, unitAmount);
            for (StoredFluid fluid : stored) {
                if (toReserve <= 0L || !required.isFluidEqual(fluid.sample)) continue;
                long reserved = reservations.getOrDefault(fluid, 0L);
                long use = Math.min(toReserve, Math.max(0L, fluid.amount - reserved));
                if (use > 0L) reservations.put(fluid, reserved + use);
                toReserve -= use;
            }
            return units;
        }
    }

    private static final class StoredItem {

        private final ItemStack sample;
        private final long amount;

        private StoredItem(ItemStack sample, long amount) {
            this.sample = sample;
            this.amount = amount;
        }

        private ItemStack asItemStack() {
            ItemStack result = sample.copy();
            result.stackSize = clampToPositiveInt(amount);
            return result;
        }
    }

    private static final class StoredFluid {

        private final FluidStack sample;
        private final long amount;

        private StoredFluid(FluidStack sample, long amount) {
            this.sample = sample.copy();
            this.sample.amount = 1;
            this.amount = amount;
        }

        private FluidStack asFluidStack() {
            FluidStack result = sample.copy();
            result.amount = clampToPositiveInt(amount);
            return result;
        }
    }
}

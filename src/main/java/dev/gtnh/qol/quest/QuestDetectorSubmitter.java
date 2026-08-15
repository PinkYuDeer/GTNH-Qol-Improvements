package dev.gtnh.qol.quest;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.party.IParty;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.ItemComparison;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.questing.QuestDatabase;
import betterquesting.storage.QuestSettings;
import bq_standard.tasks.TaskFluid;
import bq_standard.tasks.TaskRetrieval;
import dev.gtnh.qol.config.QolConfig;

/** Handles explicit BetterQuesting submissions backed by networks containing a bound quest detector. */
public final class QuestDetectorSubmitter {

    private static final Logger LOGGER = LogManager.getLogger("GTNH-QoL-QuestDetector");
    private static final Set<TileQuestDetector> DETECTORS = Collections.newSetFromMap(new WeakHashMap<>());

    private QuestDetectorSubmitter() {}

    static void register(TileQuestDetector detector) {
        if (detector == null || detector.getWorldObj() == null || detector.getWorldObj().isRemote) return;
        synchronized (DETECTORS) {
            DETECTORS.add(detector);
        }
    }

    static void unregister(TileQuestDetector detector) {
        synchronized (DETECTORS) {
            DETECTORS.remove(detector);
        }
    }

    public static void submit(IQuest quest, EntityPlayer player) {
        if (!isEligible(quest, player)) return;

        ParticipantInfo participant = new ParticipantInfo(player);
        List<NetworkAccess> networks = findNetworks(participant);
        if (networks.isEmpty()) return;

        UUID questId = QuestDatabase.INSTANCE.lookupKey(quest);
        if (questId == null) return;
        Map.Entry<UUID, IQuest> questEntry = new AbstractMap.SimpleImmutableEntry<>(questId, quest);

        for (DBEntry<ITask> taskEntry : quest.getTasks()
            .getEntries()) {
            ITask task = taskEntry.getValue();
            try {
                if (task instanceof TaskRetrieval retrieval && retrieval.consume
                    && !retrieval.isComplete(participant.UUID)) {
                    submitItems(retrieval, participant, questEntry, networks);
                } else if (task instanceof TaskFluid fluid && fluid.consume && !fluid.isComplete(participant.UUID)) {
                    submitFluids(fluid, participant, questEntry, networks);
                }
            } catch (RuntimeException error) {
                LOGGER.warn(
                    "Unable to submit BetterQuesting task {} from an ME network",
                    task.getClass()
                        .getName(),
                    error);
            }
        }
    }

    private static boolean isEligible(IQuest quest, EntityPlayer player) {
        if (!QolConfig.questDetector || quest == null
            || player == null
            || player.worldObj == null
            || player.worldObj.isRemote) return false;

        QuestCache cache = (QuestCache) player.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());
        if (cache == null) return false;

        UUID playerId = QuestingAPI.getQuestingUUID(player);
        if (quest.isComplete(playerId) && (quest.getProperty(NativeProps.REPEAT_TIME) < 0 || quest.getRewards()
            .size() <= 0)) return false;
        if (!quest.canSubmit(player)) return false;
        return quest.isUnlocked(playerId) || QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE);
    }

    private static List<NetworkAccess> findNetworks(ParticipantInfo participant) {
        List<TileQuestDetector> detectors;
        synchronized (DETECTORS) {
            detectors = new ArrayList<>(DETECTORS);
        }

        Set<IGrid> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<NetworkAccess> result = new ArrayList<>();
        for (TileQuestDetector detector : detectors) {
            if (!isUsableBy(detector, participant)) continue;
            try {
                IGrid grid = detector.getProxy()
                    .getGrid();
                if (!seen.add(grid)) continue;
                result.add(
                    new NetworkAccess(
                        detector,
                        detector.getProxy()
                            .getStorage()));
            } catch (GridAccessException ignored) {}
        }
        return result;
    }

    private static boolean isUsableBy(TileQuestDetector detector, ParticipantInfo participant) {
        if (detector == null || detector.isInvalid()
            || detector.getWorldObj() == null
            || detector.getWorldObj().isRemote
            || !detector.hasOwner()
            || !detector.isNetworkActive()) return false;

        if (detector.getOwnerPartyId() >= 0) {
            DBEntry<IParty> party = participant.PARTY_INSTANCE;
            return party != null && party.getID() == detector.getOwnerPartyId();
        }
        return detector.getOwnerId()
            .equals(participant.UUID);
    }

    private static void submitItems(TaskRetrieval task, ParticipantInfo participant, Map.Entry<UUID, IQuest> questEntry,
        List<NetworkAccess> networks) {
        for (NetworkAccess network : networks) {
            IMEMonitor<IAEItemStack> inventory = network.storage.getItemInventory();
            if (inventory == null) continue;

            for (IAEItemStack candidate : new ArrayList<>(network.getItems())) {
                ItemStack sample = candidate.getItemStack();
                if (sample == null || sample.getItem() == null) continue;
                sample.stackSize = 1;

                long needed = getMissingItems(task, participant.UUID, sample);
                if (needed <= 0L) continue;
                long requested = Math.min(Math.min(needed, candidate.getStackSize()), Integer.MAX_VALUE);
                if (requested <= 0L) continue;

                IAEItemStack request = candidate.copy();
                request.setStackSize(requested);
                IAEItemStack extracted = inventory
                    .extractItems(request, Actionable.MODULATE, new PlayerSource(participant.PLAYER, network.detector));
                if (extracted == null || extracted.getStackSize() <= 0L) continue;
                candidate.setStackSize(Math.max(0L, candidate.getStackSize() - extracted.getStackSize()));

                ItemStack input = extracted.getItemStack();
                input.stackSize = (int) Math.min(Integer.MAX_VALUE, extracted.getStackSize());
                ItemStack remainder = task.submitItem(participant.UUID, questEntry, input);
                if (remainder != null && remainder.stackSize > 0) {
                    restoreItem(network, participant.PLAYER, remainder, remainder.stackSize);
                }
            }
        }
    }

    private static long getMissingItems(TaskRetrieval task, UUID playerId, ItemStack sample) {
        int[] progress = task.getUsersProgress(playerId);
        long missing = 0L;
        for (int i = 0; i < task.requiredItems.size(); i++) {
            BigItemStack required = task.requiredItems.get(i);
            if (!matches(task, required, sample)) continue;
            missing = saturatedAdd(missing, Math.max(0L, (long) required.stackSize - progress[i]));
        }
        return missing;
    }

    private static boolean matches(TaskRetrieval task, BigItemStack required, ItemStack sample) {
        return ItemComparison.StackMatch(required.getBaseStack(), sample, !task.ignoreNBT, task.partialMatch)
            || ItemComparison.OreDictionaryMatch(
                required.getOreIngredient(),
                required.GetTagCompound(),
                sample,
                !task.ignoreNBT,
                task.partialMatch);
    }

    private static void submitFluids(TaskFluid task, ParticipantInfo participant, Map.Entry<UUID, IQuest> questEntry,
        List<NetworkAccess> networks) {
        for (NetworkAccess network : networks) {
            submitNativeFluids(task, participant, questEntry, network);
        }
        for (NetworkAccess network : networks) {
            submitFluidContainers(task, participant, questEntry, network);
        }
    }

    private static void submitNativeFluids(TaskFluid task, ParticipantInfo participant,
        Map.Entry<UUID, IQuest> questEntry, NetworkAccess network) {
        IMEMonitor<IAEFluidStack> inventory = network.storage.getFluidInventory();
        if (inventory == null) return;

        for (IAEFluidStack candidate : new ArrayList<>(network.getFluids())) {
            FluidStack sample = candidate.getFluidStack();
            long needed = getMissingFluid(task, participant.UUID, sample);
            if (needed <= 0L) continue;
            long requested = Math.min(Math.min(needed, candidate.getStackSize()), Integer.MAX_VALUE);
            if (requested <= 0L) continue;

            IAEFluidStack request = candidate.copy();
            request.setStackSize(requested);
            IAEFluidStack extracted = inventory
                .extractItems(request, Actionable.MODULATE, new PlayerSource(participant.PLAYER, network.detector));
            if (extracted == null || extracted.getStackSize() <= 0L) continue;
            candidate.setStackSize(Math.max(0L, candidate.getStackSize() - extracted.getStackSize()));

            FluidStack input = extracted.getFluidStack();
            input.amount = (int) Math.min(Integer.MAX_VALUE, extracted.getStackSize());
            FluidStack remainder = task.submitFluid(participant.UUID, questEntry, input);
            if (remainder != null && remainder.amount > 0) restoreFluid(network, remainder);
        }
    }

    private static void submitFluidContainers(TaskFluid task, ParticipantInfo participant,
        Map.Entry<UUID, IQuest> questEntry, NetworkAccess network) {
        IMEMonitor<IAEItemStack> inventory = network.storage.getItemInventory();
        if (inventory == null) return;

        for (IAEItemStack candidate : new ArrayList<>(network.getItems())) {
            ItemStack sample = candidate.getItemStack();
            if (sample == null || sample.getItem() == null) continue;
            sample.stackSize = 1;
            if (!task.canAcceptItem(participant.UUID, questEntry, sample)) continue;

            FluidStack contained = getContainedFluid(sample);
            ItemStack drained = getDrainedContainer(sample);
            if (contained == null || contained.amount <= 0) continue;

            long needed = getMissingFluid(task, participant.UUID, contained);
            if (needed <= 0L) continue;
            long containerCount = Math.min(candidate.getStackSize(), divideCeil(needed, contained.amount));
            long maxPerBatch = Math.max(1L, Integer.MAX_VALUE / (long) contained.amount);

            while (containerCount > 0L && getMissingFluid(task, participant.UUID, contained) > 0L) {
                long batch = Math.min(containerCount, maxPerBatch);
                IAEItemStack request = candidate.copy();
                request.setStackSize(batch);
                IAEItemStack extracted = inventory
                    .extractItems(request, Actionable.MODULATE, new PlayerSource(participant.PLAYER, network.detector));
                if (extracted == null || extracted.getStackSize() <= 0L) break;

                long extractedCount = extracted.getStackSize();
                candidate.setStackSize(Math.max(0L, candidate.getStackSize() - extractedCount));
                FluidStack submitted = contained.copy();
                submitted.amount = (int) Math.min(Integer.MAX_VALUE, extractedCount * (long) contained.amount);
                task.submitFluid(participant.UUID, questEntry, submitted);
                if (drained != null) restoreItem(network, participant.PLAYER, drained, extractedCount);
                containerCount -= extractedCount;
            }
        }
    }

    private static FluidStack getContainedFluid(ItemStack container) {
        ItemStack single = container.copy();
        single.stackSize = 1;
        if (single.getItem() instanceof IFluidContainerItem fluidContainer) {
            FluidStack fluid = fluidContainer.getFluid(single);
            return fluid == null ? null : fluid.copy();
        }
        FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(single);
        return fluid == null ? null : fluid.copy();
    }

    private static ItemStack getDrainedContainer(ItemStack container) {
        ItemStack single = container.copy();
        single.stackSize = 1;
        if (single.getItem() instanceof IFluidContainerItem fluidContainer) {
            fluidContainer.drain(single, Integer.MAX_VALUE, true);
            return single;
        }
        return FluidContainerRegistry.drainFluidContainer(single);
    }

    private static long getMissingFluid(TaskFluid task, UUID playerId, FluidStack sample) {
        if (sample == null || sample.getFluid() == null) return 0L;
        int[] progress = task.getUsersProgress(playerId);
        long missing = 0L;
        for (int i = 0; i < task.requiredFluids.size(); i++) {
            FluidStack required = task.requiredFluids.get(i)
                .copy();
            FluidStack candidate = sample.copy();
            if (task.ignoreNbt) {
                required.tag = null;
                candidate.tag = null;
            }
            if (!required.isFluidEqual(candidate)) continue;
            missing = saturatedAdd(missing, Math.max(0L, (long) required.amount - progress[i]));
        }
        return missing;
    }

    private static void restoreItem(NetworkAccess network, EntityPlayer player, ItemStack template, long amount) {
        if (template == null || template.getItem() == null || amount <= 0L) return;
        IAEItemStack stack = AEApi.instance()
            .storage()
            .createItemStack(template);
        if (stack == null) return;
        stack.setStackSize(amount);
        IAEItemStack leftover = network.storage.getItemInventory()
            .injectItems(stack, Actionable.MODULATE, new MachineSource(network.detector));
        long leftoverAmount = leftover == null ? 0L : Math.max(0L, leftover.getStackSize());
        network.addItem(template, Math.max(0L, amount - leftoverAmount));
        if (leftoverAmount <= 0L) return;
        giveItemToPlayer(player, leftover.getItemStack(), leftover.getStackSize());
    }

    private static void restoreFluid(NetworkAccess network, FluidStack fluid) {
        IAEFluidStack stack = AEApi.instance()
            .storage()
            .createFluidStack(fluid);
        if (stack == null) return;
        IAEFluidStack leftover = network.storage.getFluidInventory()
            .injectItems(stack, Actionable.MODULATE, new MachineSource(network.detector));
        long leftoverAmount = leftover == null ? 0L : Math.max(0L, leftover.getStackSize());
        network.addFluid(fluid, Math.max(0L, fluid.amount - leftoverAmount));
        if (leftover != null && leftover.getStackSize() > 0L) {
            LOGGER.error(
                "Unable to restore {} mB of {} after a BetterQuesting submission",
                leftover.getStackSize(),
                fluid.getLocalizedName());
        }
    }

    private static void giveItemToPlayer(EntityPlayer player, ItemStack template, long amount) {
        while (amount > 0L) {
            ItemStack stack = template.copy();
            stack.stackSize = (int) Math.min(amount, stack.getMaxStackSize());
            amount -= stack.stackSize;
            if (!player.inventory.addItemStackToInventory(stack)) {
                player.dropPlayerItemWithRandomChoice(stack, false);
            }
        }
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static long divideCeil(long amount, long unit) {
        return amount <= 0L ? 0L : 1L + (amount - 1L) / unit;
    }

    private static final class NetworkAccess {

        private final TileQuestDetector detector;
        private final IStorageGrid storage;
        private List<IAEItemStack> items;
        private List<IAEFluidStack> fluids;

        private NetworkAccess(TileQuestDetector detector, IStorageGrid storage) {
            this.detector = detector;
            this.storage = storage;
        }

        private List<IAEItemStack> getItems() {
            if (items != null) return items;
            items = new ArrayList<>();
            IMEMonitor<IAEItemStack> inventory = storage.getItemInventory();
            if (inventory == null) return items;
            for (IAEItemStack stack : inventory.getStorageList()) {
                if (stack.getStackSize() > 0L) items.add(stack.copy());
            }
            return items;
        }

        private List<IAEFluidStack> getFluids() {
            if (fluids != null) return fluids;
            fluids = new ArrayList<>();
            IMEMonitor<IAEFluidStack> inventory = storage.getFluidInventory();
            if (inventory == null) return fluids;
            for (IAEFluidStack stack : inventory.getStorageList()) {
                if (stack.getStackSize() > 0L) fluids.add(stack.copy());
            }
            return fluids;
        }

        private void addItem(ItemStack template, long amount) {
            if (items == null || amount <= 0L) return;
            IAEItemStack added = AEApi.instance()
                .storage()
                .createItemStack(template);
            if (added == null) return;
            for (IAEItemStack current : items) {
                if (!current.isSameType(added)) continue;
                current.incStackSize(amount);
                return;
            }
            added.setStackSize(amount);
            items.add(added);
        }

        private void addFluid(FluidStack template, long amount) {
            if (fluids == null || amount <= 0L) return;
            IAEFluidStack added = AEApi.instance()
                .storage()
                .createFluidStack(template);
            if (added == null) return;
            for (IAEFluidStack current : fluids) {
                if (!current.isSameType(added)) continue;
                current.incStackSize(amount);
                return;
            }
            added.setStackSize(amount);
            fluids.add(added);
        }
    }
}

package dev.gtnh.qol.quest;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.GridFlags;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStackWatcherHost;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.party.IParty;
import betterquesting.api2.storage.DBEntry;
import betterquesting.questing.party.PartyManager;
import dev.gtnh.qol.config.QolConfig;

public final class TileQuestDetector extends AENetworkTile implements IStackWatcherHost {

    private static final long DIRTY_SCAN_INTERVAL = 20L;
    private static final long HEARTBEAT_SCAN_INTERVAL = 200L;

    private UUID ownerId;
    private int ownerPartyId = -1;
    private String ownerName = "";
    private IStackWatcher watcher;
    private Set<IAEStack> watcherInterests = Collections.emptySet();
    private boolean inventoryDirty = true;
    private long lastScanTick = Long.MIN_VALUE / 2L;
    private boolean renderedActive;

    public TileQuestDetector() {
        getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        getProxy().setIdlePowerUsage(1.0D);
        getProxy().setValidSides(EnumSet.allOf(ForgeDirection.class));
    }

    public void bindOwner(EntityPlayer player) {
        // AE2 security identifies in-world nodes by the player ID stored on their grid node. Custom blocks do not pass
        // through AEBaseItemBlock, so mirror its placement hook before the proxy creates and joins the node.
        getProxy().setOwner(player);
        ownerId = QuestingAPI.getQuestingUUID(player);
        DBEntry<IParty> party = PartyManager.INSTANCE.getParty(ownerId);
        ownerPartyId = party == null ? -1 : party.getID();
        ownerName = player.getCommandSenderName();
        inventoryDirty = true;
        markDirty();
    }

    @Override
    public void validate() {
        super.validate();
        QuestDetectorSubmitter.register(this);
    }

    @Override
    public void invalidate() {
        QuestDetectorSubmitter.unregister(this);
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        QuestDetectorSubmitter.unregister(this);
        super.onChunkUnload();
    }

    public boolean hasOwner() {
        return ownerId != null;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public int getOwnerPartyId() {
        return ownerPartyId;
    }

    public String getOwnerName() {
        if (ownerName != null && !ownerName.isEmpty()) return ownerName;
        return ownerId == null ? "?" : ownerId.toString();
    }

    public boolean isNetworkActive() {
        return getProxy().isActive();
    }

    public void requestScan() {
        inventoryDirty = true;
        lastScanTick = Long.MIN_VALUE / 2L;
    }

    @TileEvent(TileEventType.TICK)
    public void tickQuestDetector() {
        if (worldObj == null || worldObj.isRemote) return;

        updateRenderedState();
        if (!QolConfig.questDetector || ownerId == null || !getProxy().isActive()) return;

        long now = worldObj.getTotalWorldTime();
        long elapsed = now - lastScanTick;
        if ((!inventoryDirty || elapsed < DIRTY_SCAN_INTERVAL) && elapsed < HEARTBEAT_SCAN_INTERVAL) return;

        lastScanTick = now;
        if (QuestDetectorScanner.scan(this, now)) inventoryDirty = false;
    }

    @MENetworkEventSubscribe
    public void onPowerStatusChange(MENetworkPowerStatusChange event) {
        inventoryDirty = true;
        updateRenderedState();
    }

    @MENetworkEventSubscribe
    public void onChannelsChanged(MENetworkChannelsChanged event) {
        inventoryDirty = true;
        updateRenderedState();
    }

    private void updateRenderedState() {
        if (worldObj == null || worldObj.isRemote) return;
        boolean active = getProxy().isActive();
        if (active == renderedActive && ((worldObj.getBlockMetadata(xCoord, yCoord, zCoord) & 1) != 0) == active)
            return;
        renderedActive = active;
        worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, active ? 1 : 0, 3);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readQuestDetectorNbt(NBTTagCompound data) {
        if (data.hasKey("questOwnerMost") && data.hasKey("questOwnerLeast")) {
            ownerId = new UUID(data.getLong("questOwnerMost"), data.getLong("questOwnerLeast"));
        }
        ownerName = data.getString("questOwnerName");
        ownerPartyId = data.hasKey("questOwnerParty") ? data.getInteger("questOwnerParty") : -1;
        inventoryDirty = true;
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeQuestDetectorNbt(NBTTagCompound data) {
        if (ownerId != null) {
            data.setLong("questOwnerMost", ownerId.getMostSignificantBits());
            data.setLong("questOwnerLeast", ownerId.getLeastSignificantBits());
        }
        data.setString("questOwnerName", getOwnerName());
        data.setInteger("questOwnerParty", ownerPartyId);
    }

    @Override
    public void updateWatcher(IStackWatcher newWatcher) {
        watcher = newWatcher;
        watcherInterests = Collections.emptySet();
        inventoryDirty = true;
    }

    @Override
    public void onStackChange(IItemList list, IAEStack fullStack, IAEStack diffStack, BaseActionSource source,
        StorageChannel channel) {
        inventoryDirty = true;
    }

    public void setWatcherInterests(Collection<? extends IAEStack> interests) {
        if (watcher == null) return;
        Set<IAEStack> normalized = new HashSet<>();
        for (IAEStack interest : interests) {
            if (interest == null) continue;
            IAEStack copy = interest.copy();
            copy.setStackSize(1L);
            normalized.add(copy);
        }
        if (normalized.equals(watcherInterests)) return;

        watcher.clear();
        watcher.addAll(normalized);
        watcherInterests = normalized;
    }

    @Override
    public boolean canBeRotated() {
        return false;
    }
}

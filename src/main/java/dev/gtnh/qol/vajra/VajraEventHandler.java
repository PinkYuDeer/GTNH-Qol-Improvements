package dev.gtnh.qol.vajra;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;
import appeng.api.util.IOrientable;
import appeng.core.CommonHelper;
import appeng.parts.PartPlacement;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.gtnh.qol.config.QolConfig;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.SoundResource;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaPipeEntity;
import gregtech.api.metatileentity.implementations.MTECable;
import gregtech.api.objects.GTItemStack;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import ic2.api.tile.IWrenchable;
import xonin.backhand.api.core.BackhandUtils;

public final class VajraEventHandler {

    private static final int WRENCH_ENERGY_COST = 1_000;
    private static final int MAX_PLACEMENT_AGE = 4;

    private final Map<Integer, List<PendingPlacement>> pendingPlacements = new HashMap<Integer, List<PendingPlacement>>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        EntityPlayer player = event.getPlayer();
        if (!QolConfig.vajraOffhandReplacement || event.isCanceled()
            || event.world.isRemote
            || player == null
            || !isVajra(player.getHeldItem())) {
            return;
        }

        ItemStack offhand = BackhandUtils.getOffhandItem(player);
        if (offhand == null || !(offhand.getItem() instanceof ItemBlock)) {
            return;
        }

        // Replacing a block that owns persistent state can overwrite inventories,
        // machine settings or network data. The convenience replacement is only
        // intended for ordinary building blocks.
        if (event.world.getTileEntity(event.x, event.y, event.z) != null) {
            return;
        }

        HitData hit = getHitData(player, event.x, event.y, event.z);
        int dimension = event.world.provider.dimensionId;
        List<PendingPlacement> queue = pendingPlacements.get(dimension);
        if (queue == null) {
            queue = new ArrayList<PendingPlacement>();
            pendingPlacements.put(dimension, queue);
        }
        queue.add(new PendingPlacement(player.getUniqueID(), event.x, event.y, event.z, hit.side, hit.x, hit.y, hit.z));
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }

        List<PendingPlacement> queue = pendingPlacements.get(event.world.provider.dimensionId);
        if (queue == null || queue.isEmpty()) {
            return;
        }

        Iterator<PendingPlacement> iterator = queue.iterator();
        while (iterator.hasNext()) {
            PendingPlacement pending = iterator.next();
            pending.age++;
            if (tryPlace(event.world, pending) || pending.age >= MAX_PLACEMENT_AGE) {
                iterator.remove();
            }
        }
        if (queue.isEmpty()) {
            pendingPlacements.remove(event.world.provider.dimensionId);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.entityPlayer == null
            || !isVajra(event.entityPlayer.getHeldItem())) {
            return;
        }

        ItemStack vajra = event.entityPlayer.getHeldItem();
        if (!QolConfig.vajraToolFunctions) {
            unregisterAsGtWrench(vajra);
            return;
        }

        // ToolVajra.onItemUse deliberately harvests the clicked block. Block clicks must
        // never reach that method: sneaking in air remains untouched and is therefore the
        // only way ToolVajra.onItemRightClick can toggle silk touch.
        event.useItem = cpw.mods.fml.common.eventhandler.Event.Result.DENY;

        World world = event.world;
        TileEntity tile = world.getTileEntity(event.x, event.y, event.z);
        Block block = world.getBlock(event.x, event.y, event.z);

        // The client-side handler cancels ToolVajra's local onItemUse and sends one precise
        // tool message itself. This path remains as a safe fallback for vanilla packets.
        if (world.isRemote) {
            return;
        }

        HitData hit = getHitData(event.entityPlayer, event.x, event.y, event.z);
        handlePreciseToolClick(event.entityPlayer, event.x, event.y, event.z, event.face, hit.x, hit.y, hit.z);
        if (isWrenchTarget(tile, block, world, event.x, event.y, event.z)
            || event.entityPlayer.isSneaking() && PartPlacement.getExistingHost(tile) != null) {
            consumeBlockClick(event);
        }
    }

    /** Applies one validated tool action using the exact client-side GT 3x3 hit position. */
    public static void handlePreciseToolClick(EntityPlayer player, int x, int y, int z, int face, float hitX,
        float hitY, float hitZ) {
        if (player == null || player.worldObj == null
            || player.worldObj.isRemote
            || !QolConfig.vajraToolFunctions
            || !isVajra(player.getHeldItem())
            || face < 0
            || face > 5
            || player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) > 64.0D
            || !player.worldObj.blockExists(x, y, z)) {
            return;
        }

        World world = player.worldObj;
        TileEntity tile = world.getTileEntity(x, y, z);
        Block block = world.getBlock(x, y, z);
        IPartHost aePartHost = player.isSneaking() ? PartPlacement.getExistingHost(tile) : null;
        if (aePartHost == null && !isWrenchTarget(tile, block, world, x, y, z)) {
            return;
        }

        ItemStack vajra = player.getHeldItem();
        hitX = clamp(hitX);
        hitY = clamp(hitY);
        hitZ = clamp(hitZ);
        if (aePartHost != null) {
            dismantleAePart(player, world, x, y, z, hitX, hitY, hitZ, aePartHost);
            return;
        }
        ForgeDirection clickedSide = ForgeDirection.getOrientation(face);
        ForgeDirection wrenchingSide = GTUtility.determineWrenchingSide(clickedSide, hitX, hitY, hitZ);
        if (tile instanceof IGregTechTileEntity) {
            IGregTechTileEntity gtTile = (IGregTechTileEntity) tile;
            IMetaTileEntity meta = gtTile.getMetaTileEntity();
            if (meta == null) {
                return;
            }
            if (meta instanceof MTECable) {
                // A combined tool cannot be placed in both GT tool lists: BaseMetaPipeEntity
                // checks the wrench list first. Route cables through the original cutter hook.
                // Temporarily ignore sneaking so this combined tool always toggles precisely
                // the selected grid side instead of invoking GT's multi-cable BFS mode.
                boolean wasSneaking = player.isSneaking();
                boolean handled;
                if (wasSneaking) player.setSneaking(false);
                try {
                    handled = meta.onWireCutterRightClick(clickedSide, wrenchingSide, player, hitX, hitY, hitZ, vajra);
                } finally {
                    if (wasSneaking) player.setSneaking(true);
                }
                if (handled) {
                    synchronizeCablePair(world, x, y, z, wrenchingSide, (MTECable) meta);
                    GTUtility.sendSoundToPlayers(
                        world,
                        SoundResource.GTCEU_OP_WIRECUTTER,
                        1.0F,
                        1.0F,
                        x + 0.5D,
                        y + 0.5D,
                        z + 0.5D);
                }
                return;
            }

            // Let the original GT base-tile implementation handle machines and other pipes.
            // Temporarily advertising the stack as a wrench preserves special machine-facing
            // logic, native charge costs, sounds, covers and the GT 3x3 side calculation.
            GTItemStack wrenchKey = new GTItemStack(vajra, true);
            boolean alreadyRegistered = GregTechAPI.sWrenchList.contains(wrenchKey);
            if (!alreadyRegistered) {
                GregTechAPI.sWrenchList.add(wrenchKey);
            }
            try {
                gtTile.onRightclick(player, clickedSide, hitX, hitY, hitZ);
            } finally {
                if (!alreadyRegistered) {
                    GregTechAPI.sWrenchList.remove(wrenchKey);
                }
            }
            return;
        }

        boolean handled = handleGenericWrench(tile, block, world, x, y, z, wrenchingSide, player, vajra);
        if (handled) {
            if (tile != null) tile.markDirty();
            world.markBlockForUpdate(x, y, z);
            GTUtility
                .sendSoundToPlayers(world, SoundResource.GTCEU_OP_WRENCH, 1.0F, 1.0F, x + 0.5D, y + 0.5D, z + 0.5D);
        }
    }

    /** Uses AE2's native wrench path so the precisely selected part or facade is dropped. */
    private static boolean dismantleAePart(EntityPlayer player, World world, int x, int y, int z, float hitX,
        float hitY, float hitZ, IPartHost host) {
        if (!canSpendEnergy(player.getHeldItem(), player)) {
            return false;
        }

        SelectedPart selected;
        CommonHelper.proxy.updateRenderMode(player);
        try {
            selected = host.selectPart(Vec3.createVectorHelper(hitX, hitY, hitZ));
        } finally {
            CommonHelper.proxy.updateRenderMode(null);
        }
        if (selected == null || selected.part == null && selected.facade == null
            || !PartPlacement.wrenchLogic(player, world, x, y, z, host, selected)) {
            return false;
        }

        spendEnergy(player.getHeldItem(), player);
        return true;
    }

    /** Keep both cable endpoints and both client connection masks in the same state. */
    private static void synchronizeCablePair(World world, int x, int y, int z, ForgeDirection side, MTECable cable) {
        TileEntity clickedTile = world.getTileEntity(x, y, z);
        TileEntity neighbourTile = world.getTileEntity(x + side.offsetX, y + side.offsetY, z + side.offsetZ);
        MTECable neighbourCable = null;
        if (neighbourTile instanceof IGregTechTileEntity neighbourGt
            && neighbourGt.getMetaTileEntity() instanceof MTECable) {
            neighbourCable = (MTECable) neighbourGt.getMetaTileEntity();
            ForgeDirection opposite = side.getOpposite();
            boolean connected = cable.isConnectedAtSide(side);
            if (connected && !neighbourCable.isConnectedAtSide(opposite)) {
                if (neighbourCable.connect(opposite) <= 0 && !neighbourCable.isConnectedAtSide(opposite)) {
                    // Do not leave a visually and electrically invalid half-connection when
                    // the adjacent cable rejects the reciprocal connection.
                    cable.disconnect(side);
                }
            } else if (!connected && neighbourCable.isConnectedAtSide(opposite)) {
                neighbourCable.disconnect(opposite);
            }
        }

        syncCableTile(world, x, y, z, clickedTile, cable);
        if (neighbourCable != null) {
            syncCableTile(world, x + side.offsetX, y + side.offsetY, z + side.offsetZ, neighbourTile, neighbourCable);
        }
    }

    private static void syncCableTile(World world, int x, int y, int z, TileEntity tile, MTECable cable) {
        cable.markDirty();
        tile.markDirty();
        if (tile instanceof BaseMetaPipeEntity pipe) {
            pipe.updateConnections();
            pipe.issueTextureUpdate();
            pipe.doEnetUpdate();
        } else if (tile instanceof IGregTechTileEntity gtTile) {
            gtTile.issueTextureUpdate();
        }
        world.markBlockForUpdate(x, y, z);
    }

    private static void consumeBlockClick(PlayerInteractEvent event) {
        event.useBlock = cpw.mods.fml.common.eventhandler.Event.Result.DENY;
        event.useItem = cpw.mods.fml.common.eventhandler.Event.Result.DENY;
        event.setCanceled(true);
    }

    /** Make GT's original machine/pipe logic and block-overlay renderer recognise this stack. */
    public static void registerAsGtWrench(ItemStack stack) {
        if (isVajra(stack)) {
            GregTechAPI.sWrenchList.add(new GTItemStack(stack, true));
        }
    }

    public static void unregisterAsGtWrench(ItemStack stack) {
        if (isVajra(stack)) {
            GregTechAPI.sWrenchList.remove(new GTItemStack(stack, true));
        }
    }

    /** Enables GT's full-block pipe ray trace without putting Vajra in wrench-first routing. */
    public static void registerAsGtWireCutter(ItemStack stack) {
        if (isVajra(stack)) {
            GregTechAPI.sWireCutterList.add(new GTItemStack(stack, true));
        }
    }

    public static void unregisterAsGtWireCutter(ItemStack stack) {
        if (isVajra(stack)) {
            GregTechAPI.sWireCutterList.remove(new GTItemStack(stack, true));
        }
    }

    private static boolean tryPlace(World world, PendingPlacement pending) {
        if (!world.isAirBlock(pending.x, pending.y, pending.z)) {
            return true;
        }

        EntityPlayer player = findPlayer(world, pending.playerId);
        if (player == null || player.isDead || !isVajra(player.getHeldItem())) {
            return false;
        }

        ItemStack offhand = BackhandUtils.getOffhandItem(player);
        if (offhand == null || !(offhand.getItem() instanceof ItemBlock)) {
            return true;
        }

        final ItemStack placementStack = offhand;
        boolean placed = BackhandUtils.useOffhandItem(
            player,
            false,
            () -> placementStack.tryPlaceItemIntoWorld(
                player,
                world,
                pending.x,
                pending.y,
                pending.z,
                pending.side.ordinal(),
                pending.hitX,
                pending.hitY,
                pending.hitZ));
        if (placementStack.stackSize <= 0) {
            BackhandUtils.setPlayerOffhandItem(player, null);
        }
        player.inventory.markDirty();
        return placed || !world.isAirBlock(pending.x, pending.y, pending.z);
    }

    @SuppressWarnings("unchecked")
    private static EntityPlayer findPlayer(World world, UUID id) {
        for (EntityPlayer player : (List<EntityPlayer>) world.playerEntities) {
            if (id.equals(player.getUniqueID())) {
                return player;
            }
        }
        return null;
    }

    private static boolean handleGenericWrench(TileEntity tile, Block block, World world, int x, int y, int z,
        ForgeDirection direction, EntityPlayer player, ItemStack vajra) {
        if (!canSpendEnergy(vajra, player)) {
            return false;
        }

        boolean handled = false;
        if (tile instanceof IOrientable) {
            handled = rotateOrientable((IOrientable) tile, direction, player.isSneaking());
        } else if (tile instanceof IWrenchable) {
            IWrenchable wrenchable = (IWrenchable) tile;
            if (wrenchable.wrenchCanSetFacing(player, direction.ordinal())) {
                wrenchable.setFacing((short) direction.ordinal());
                handled = true;
            }
        } else if (block != null) {
            ForgeDirection[] rotations = block.getValidRotations(world, x, y, z);
            if (rotations != null) {
                for (ForgeDirection rotation : rotations) {
                    if (rotation == direction) {
                        handled = block.rotateBlock(world, x, y, z, direction);
                        break;
                    }
                }
            }
        }

        if (handled) {
            spendEnergy(vajra, player);
        }
        return handled;
    }

    private static boolean rotateOrientable(IOrientable orientable, ForgeDirection direction, boolean sneaking) {
        if (!orientable.canBeRotated()) {
            return false;
        }

        ForgeDirection front = orientable.getForward();
        ForgeDirection up = orientable.getUp();
        if (front == ForgeDirection.UNKNOWN) {
            front = direction.offsetY == 0 ? ForgeDirection.UP : ForgeDirection.NORTH;
        }
        if (up == ForgeDirection.UNKNOWN || up == front || up == front.getOpposite()) {
            up = front.offsetY == 0 ? ForgeDirection.UP : ForgeDirection.NORTH;
        }
        if (sneaking) {
            up = up.getRotation(front);
        } else {
            front = direction;
            if (up == front || up == front.getOpposite()) {
                up = front.offsetY == 0 ? ForgeDirection.UP : ForgeDirection.NORTH;
            }
        }
        orientable.setOrientation(front, up);
        return true;
    }

    private static boolean canSpendEnergy(ItemStack stack, EntityPlayer player) {
        return player.capabilities.isCreativeMode || !GTModHandler.isElectricItem(stack)
            || GTModHandler.canUseElectricItem(stack, WRENCH_ENERGY_COST);
    }

    private static void spendEnergy(ItemStack stack, EntityPlayer player) {
        if (!player.capabilities.isCreativeMode) {
            GTModHandler.damageOrDechargeItem(stack, 1, WRENCH_ENERGY_COST, player);
        }
    }

    private static boolean isWrenchTarget(TileEntity tile, Block block, World world, int x, int y, int z) {
        if (tile instanceof IGregTechTileEntity || tile instanceof IOrientable || tile instanceof IWrenchable) {
            return true;
        }
        if (block == null) {
            return false;
        }
        ForgeDirection[] rotations = block.getValidRotations(world, x, y, z);
        return rotations != null && rotations.length > 0;
    }

    public static boolean isVajra(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        Class<?> type = stack.getItem()
            .getClass();
        while (type != null) {
            String name = type.getName();
            if ("gravisuite.ItemVajra".equals(name) || "gregtech.common.tools.ToolVajra".equals(name)) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static HitData getHitData(EntityPlayer player, int x, int y, int z) {
        MovingObjectPosition target = player.rayTrace(8.0D, 1.0F);
        if (target != null && target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && target.blockX == x
            && target.blockY == y
            && target.blockZ == z) {
            Vec3 hit = target.hitVec;
            return new HitData(
                ForgeDirection.getOrientation(target.sideHit),
                clamp((float) (hit.xCoord - x)),
                clamp((float) (hit.yCoord - y)),
                clamp((float) (hit.zCoord - z)));
        }
        return new HitData(ForgeDirection.UP, 0.5F, 0.5F, 0.5F);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static final class HitData {

        private final ForgeDirection side;
        private final float x;
        private final float y;
        private final float z;

        private HitData(ForgeDirection side, float x, float y, float z) {
            this.side = side;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class PendingPlacement {

        private final UUID playerId;
        private final int x;
        private final int y;
        private final int z;
        private final ForgeDirection side;
        private final float hitX;
        private final float hitY;
        private final float hitZ;
        private int age;

        private PendingPlacement(UUID playerId, int x, int y, int z, ForgeDirection side, float hitX, float hitY,
            float hitZ) {
            this.playerId = playerId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.side = side;
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitZ = hitZ;
        }
    }
}

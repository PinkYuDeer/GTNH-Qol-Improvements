package dev.gtnh.qol.client.vajra;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import appeng.api.util.IOrientable;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.gtnh.qol.config.QolConfig;
import dev.gtnh.qol.network.QolNetwork;
import dev.gtnh.qol.vajra.VajraEventHandler;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTECable;
import gregtech.client.BlockOverlayRenderer;
import ic2.api.tile.IWrenchable;

/** Connects the Vajra to GT's own 3x3 wrench/cutter target overlay. */
public final class VajraOverlayHandler {

    private static final int HELD_BREAK_INTERVAL_TICKS = 5;
    private static final Method DRAW_GRID = findDrawGrid();
    private static final Field BLOCK_HIT_DELAY = findBlockHitDelay();

    private WorldClient observedWorld;
    private int observedX;
    private int observedY;
    private int observedZ;
    private boolean observedSolidBlock;
    private boolean suppressFallbackAirUse;
    private ItemStack registeredFullBlockTool;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDrawBlockHighlight(DrawBlockHighlightEvent event) {
        if (event.target == null || !VajraEventHandler.isVajra(event.currentItem)) {
            return;
        }

        if (!QolConfig.vajraToolFunctions) {
            VajraEventHandler.unregisterAsGtWrench(event.currentItem);
            return;
        }

        TileEntity tile = event.player.worldObj
            .getTileEntity(event.target.blockX, event.target.blockY, event.target.blockZ);
        Block block = event.player.worldObj.getBlock(event.target.blockX, event.target.blockY, event.target.blockZ);
        boolean cable = false;
        boolean wrenchTarget = tile instanceof IGregTechTileEntity || tile instanceof IOrientable
            || tile instanceof IWrenchable;
        if (tile instanceof IGregTechTileEntity gtTile) {
            IMetaTileEntity meta = gtTile.getMetaTileEntity();
            cable = meta instanceof MTECable;
        } else if (!wrenchTarget && block != null) {
            ForgeDirection[] rotations = block.getValidRotations(
                event.player.worldObj,
                event.target.blockX,
                event.target.blockY,
                event.target.blockZ);
            wrenchTarget = rotations != null && rotations.length > 0;
        }
        if (!wrenchTarget || DRAW_GRID == null) {
            return;
        }

        try {
            // Cables use cutter colours/semantics; every other supported target uses the
            // native wrench variant. The private renderer preserves GT's exact nine zones.
            DRAW_GRID.invoke(null, event, false, !cable, !cable && event.player.isSneaking());
        } catch (ReflectiveOperationException ignored) {}
    }

    /**
     * Minecraft 1.7.10 checks only cancellation on the client interaction event; its
     * useItem result is ignored there. Send the precise ray hit to our server handler, then
     * cancel the local path before ToolVajra.onItemUse can harvest the clicked block.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent event) {
        if (!event.world.isRemote || event.entityPlayer == null
            || !VajraEventHandler.isVajra(event.entityPlayer.getHeldItem())
            || !QolConfig.vajraToolFunctions) {
            return;
        }
        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR && suppressFallbackAirUse) {
            // Minecraft treats a canceled block click as unhandled and immediately attempts
            // RIGHT_CLICK_AIR. Suppress only that synthetic fallback; a genuine air click on
            // a later input remains the Vajra's silk-touch toggle.
            suppressFallbackAirUse = false;
            event.setCanceled(true);
            return;
        }
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        float hitX = 0.5F;
        float hitY = 0.5F;
        float hitZ = 0.5F;
        MovingObjectPosition target = minecraft.objectMouseOver;
        if (target != null && target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && target.blockX == event.x
            && target.blockY == event.y
            && target.blockZ == event.z) {
            Vec3 hit = target.hitVec;
            hitX = clamp((float) (hit.xCoord - event.x));
            hitY = clamp((float) (hit.yCoord - event.y));
            hitZ = clamp((float) (hit.zCoord - event.z));
        }
        QolNetwork.vajraToolClick(event.x, event.y, event.z, event.face, hitX, hitY, hitZ);
        suppressFallbackAirUse = true;
        event.setCanceled(true);
    }

    /**
     * Instant survival mining omits creative mode's blockHitDelay. Observe an actual block
     * disappearing during the client tick and apply that native five-tick delay afterward.
     * A fresh click still calls clickBlock directly, so rapid point-clicking is unaffected.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event.phase == TickEvent.Phase.START) {
            updateFullBlockRayTraceRegistration(minecraft);
            observeMiningTarget(minecraft);
            return;
        }
        if (observedSolidBlock && isAttackKeyDown()
            && minecraft.theWorld == observedWorld
            && minecraft.thePlayer != null
            && VajraEventHandler.isVajra(minecraft.thePlayer.getHeldItem())
            && observedWorld.isAirBlock(observedX, observedY, observedZ)) {
            setHeldBreakDelay(minecraft.playerController);
        }
        observedSolidBlock = false;
        observedWorld = null;
        suppressFallbackAirUse = false;
    }

    private void updateFullBlockRayTraceRegistration(Minecraft minecraft) {
        ItemStack held = minecraft != null && minecraft.thePlayer != null ? minecraft.thePlayer.getHeldItem() : null;
        boolean shouldRegister = QolConfig.vajraToolFunctions && VajraEventHandler.isVajra(held);
        if (registeredFullBlockTool != null && (!shouldRegister || registeredFullBlockTool.getItem() != held.getItem()
            || registeredFullBlockTool.getItemDamage() != held.getItemDamage())) {
            VajraEventHandler.unregisterAsGtWireCutter(registeredFullBlockTool);
            registeredFullBlockTool = null;
        }
        if (shouldRegister && registeredFullBlockTool == null) {
            registeredFullBlockTool = held.copy();
            VajraEventHandler.registerAsGtWireCutter(registeredFullBlockTool);
        }
    }

    private void observeMiningTarget(Minecraft minecraft) {
        observedSolidBlock = false;
        observedWorld = null;
        if (minecraft == null || minecraft.theWorld == null
            || minecraft.thePlayer == null
            || !isAttackKeyDown()
            || !VajraEventHandler.isVajra(minecraft.thePlayer.getHeldItem())) {
            return;
        }
        MovingObjectPosition target = minecraft.objectMouseOver;
        if (target == null || target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
            || minecraft.theWorld.isAirBlock(target.blockX, target.blockY, target.blockZ)) {
            return;
        }
        observedWorld = minecraft.theWorld;
        observedX = target.blockX;
        observedY = target.blockY;
        observedZ = target.blockZ;
        observedSolidBlock = true;
    }

    private static boolean isAttackKeyDown() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.gameSettings == null) {
            return false;
        }
        int keyCode = minecraft.gameSettings.keyBindAttack.getKeyCode();
        return keyCode < 0 ? Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static void setHeldBreakDelay(PlayerControllerMP controller) {
        if (BLOCK_HIT_DELAY == null || controller == null) {
            return;
        }
        try {
            BLOCK_HIT_DELAY.setInt(controller, HELD_BREAK_INTERVAL_TICKS);
        } catch (IllegalAccessException ignored) {}
    }

    private static Field findBlockHitDelay() {
        for (String name : new String[] { "blockHitDelay", "field_78781_i" }) {
            try {
                Field field = PlayerControllerMP.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static Method findDrawGrid() {
        try {
            Method method = BlockOverlayRenderer.class.getDeclaredMethod(
                "drawGrid",
                DrawBlockHighlightEvent.class,
                boolean.class,
                boolean.class,
                boolean.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}

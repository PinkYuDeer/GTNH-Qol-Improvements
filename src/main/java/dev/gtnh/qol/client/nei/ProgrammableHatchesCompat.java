package dev.gtnh.qol.client.nei;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import baubles.api.BaublesApi;
import cpw.mods.fml.common.Loader;

/** Optional, reflection-only bridge to Programmable Hatches. */
final class ProgrammableHatchesCompat {

    private static final String MOD_ID = "programmablehatches";
    private static final String TOOLKIT_CLASS_NAME = "reobf.proghatches.item.ItemProgrammingToolkit";
    private static final String CIRCUIT_CLASS_NAME = "reobf.proghatches.item.ItemProgrammingCircuit";

    private static boolean initialized;
    private static Class<?> toolkitClass;
    private static Method wrapMethod;

    private ProgrammableHatchesCompat() {}

    /**
     * AE2Things uses ItemProgrammingToolkit.holding(). Check the inventories
     * directly as well so opening NEI immediately after equipping or moving the
     * toolkit cannot miss PH's ten-tick client-side holding cache.
     */
    static int activeToolkitMode() {
        if (!initialize()) return 0;
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return 0;
        int inventoryMode = findActiveToolkitMode(player.inventory);
        if (inventoryMode > 0) return inventoryMode;
        try {
            return findActiveToolkitMode(BaublesApi.getBaubles(player));
        } catch (RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    static ItemStack wrapVirtualItem(ItemStack nonConsumed) {
        if (nonConsumed == null || !initialize()) return null;
        try {
            ItemStack copy = nonConsumed.copy();
            copy.stackSize = 1;
            Object wrapped = wrapMethod.invoke(null, copy);
            return wrapped instanceof ItemStack stack ? stack : null;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    static ItemStack wrapEmptyVirtualItem() {
        if (!initialize()) return null;
        try {
            Object wrapped = wrapMethod.invoke(null, new Object[] { null });
            return wrapped instanceof ItemStack stack ? stack : null;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static int findActiveToolkitMode(IInventory inventory) {
        if (inventory == null) return 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            // PH's holding() treats metadata zero as disabled. Preserve that
            // mode semantic while recognizing both player and Baubles slots.
            if (stack != null && stack.getItem() != null
                && stack.getItemDamage() > 0
                && toolkitClass.isInstance(stack.getItem())) {
                return stack.getItemDamage();
            }
        }
        return 0;
    }

    private static synchronized boolean initialize() {
        if (initialized) return toolkitClass != null && wrapMethod != null;
        initialized = true;
        if (!Loader.isModLoaded(MOD_ID)) return false;
        try {
            toolkitClass = Class.forName(TOOLKIT_CLASS_NAME);
            Class<?> circuitClass = Class.forName(CIRCUIT_CLASS_NAME);
            wrapMethod = circuitClass.getMethod("wrap", ItemStack.class);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            toolkitClass = null;
            wrapMethod = null;
            return false;
        }
    }
}

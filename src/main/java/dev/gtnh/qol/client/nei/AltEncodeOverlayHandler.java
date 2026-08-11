package dev.gtnh.qol.client.nei;

import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import com.glodblock.github.util.Util;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEStack;
import appeng.core.AELog;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;
import dev.gtnh.qol.client.terminal.GuiQuickEncodingTerminal;
import dev.gtnh.qol.config.QolConfig;
import dev.gtnh.qol.terminal.RecipeTransferPayload;
import gregtech.api.enums.ItemList;
import gregtech.common.config.Gregtech;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.nei.GTNEIDefaultHandler.FixedPositionedStack;

public final class AltEncodeOverlayHandler implements IOverlayHandler {

    private static final int CRAFTING_RECIPE_X = 25;
    private static final int CRAFTING_RECIPE_Y = 6;
    private static final int RECIPE_SLOT_SIZE = 18;

    @Override
    public boolean requireShiftForOverlayRecipe() {
        return false;
    }

    @Override
    public void overlayRecipe(GuiContainer gui, IRecipeHandler recipe, int recipeIndex, boolean shift) {
        if (!(gui instanceof GuiQuickEncodingTerminal terminal)) return;

        try {
            boolean crafting = isCraftingRecipe(recipe);
            IAEStack<?>[] inputs = crafting ? collectCraftingInputs(recipe, recipeIndex)
                : collectProcessingInputs(recipe, recipeIndex);
            IAEStack<?>[] outputs = crafting ? new IAEStack<?>[RecipeTransferPayload.SLOT_COUNT]
                : collectProcessingOutputs(recipe, recipeIndex);
            int inputCount = countStacks(inputs);
            int outputCount = countStacks(outputs);
            int processingGridSize = !crafting && (inputCount > 9 || outputCount > 3) ? 4 : 3;
            boolean inverted = !crafting && processingGridSize == 4 && inputCount <= 4 && outputCount > 4;
            boolean encode = QolConfig.dualTerminal && isAltDown();

            terminal.transferRecipe(
                new RecipeTransferPayload(crafting, encode, processingGridSize, inverted, inputs, outputs),
                interfaceSearchText(recipe, recipeIndex, crafting));
        } catch (RuntimeException | LinkageError failure) {
            AELog.warn(failure, "Failed to transfer an NEI recipe to the quick encoding terminal");
        }
    }

    private static boolean isCraftingRecipe(IRecipeHandler recipe) {
        String identifier = recipe.getOverlayIdentifier();
        if (identifier == null) return false;
        identifier = identifier.toLowerCase(Locale.ROOT);
        return identifier.equals("crafting") || identifier.equals("crafting2x2");
    }

    /** Matches AE2Things: crafting patterns target molecular assemblers, while processing uses the NEI machine name. */
    private static String interfaceSearchText(IRecipeHandler recipe, int recipeIndex, boolean crafting) {
        if (!crafting) return gtProcessingSearchText(recipe, recipeIndex);
        ItemStack assembler = AEApi.instance()
            .definitions()
            .blocks()
            .molecularAssembler()
            .maybeStack(1)
            .orNull();
        return assembler == null ? recipe.getRecipeName() : Platform.getItemDisplayName(assembler);
    }

    /**
     * Mirrors AE2Things' virtual-input detection. Circuit metadata is appended
     * as a plain number, while other non-consumed inputs retain GTNH's current
     * interface suffix format.
     */
    private static String gtProcessingSearchText(IRecipeHandler recipe, int recipeIndex) {
        String recipeName = recipe.getRecipeName();
        if (!QolConfig.terminalGtRecipeSearchSuffix || !(recipe instanceof GTNEIDefaultHandler)) {
            return recipeName;
        }

        Integer circuit = null;
        List<ItemStack> nonConsumed = new ArrayList<ItemStack>();
        for (PositionedStack positioned : safeList(recipe.getIngredientStacks(recipeIndex))) {
            if (!isNonConsumedInput(positioned)) continue;
            ItemStack item = selectedItem(positioned);
            if (item == null) continue;
            if (ItemList.Circuit_Integrated.isStackEqual(item, true, true)) {
                if (item.getItemDamage() > 0) circuit = item.getItemDamage();
                continue;
            }
            nonConsumed.add(item);
        }

        StringBuilder search = new StringBuilder(recipeName);
        if (circuit != null) {
            // AE2Things uses "%s %s" here. Do not use GT's ghost-circuit
            // display suffix because that wraps the number in square brackets.
            search.append(' ')
                .append(circuit);
        }
        if (!nonConsumed.isEmpty()) {
            String names = nonConsumed.stream()
                .map(ItemStack::getDisplayName)
                .collect(Collectors.joining(", "));
            appendFormatted(search, Gregtech.machines.itemSlotsSuffixFormat, names);
        }
        return search.toString();
    }

    private static boolean isNonConsumedInput(PositionedStack positioned) {
        if (positioned == null) return false;
        if (positioned instanceof FixedPositionedStack fixed) {
            return fixed.realStackSize == 0;
        }
        ItemStack item = selectedItem(positioned);
        return item != null && item.stackSize == 0;
    }

    private static ItemStack selectedItem(PositionedStack positioned) {
        if (positioned == null) return null;
        if (positioned.item != null) return positioned.item;
        if (positioned.items == null) return null;
        for (ItemStack alternative : positioned.items) {
            if (alternative != null) return alternative;
        }
        return null;
    }

    private static void appendFormatted(StringBuilder target, String format, Object value) {
        try {
            target.append(String.format(format, value));
        } catch (IllegalFormatException ignored) {}
    }

    private static IAEStack<?>[] collectCraftingInputs(IRecipeHandler recipe, int recipeIndex) {
        IAEStack<?>[] result = new IAEStack<?>[RecipeTransferPayload.SLOT_COUNT];
        int fallbackSlot = 0;
        for (PositionedStack positioned : safeList(recipe.getIngredientStacks(recipeIndex))) {
            if (positioned == null) continue;
            int column = (positioned.relx - CRAFTING_RECIPE_X) / RECIPE_SLOT_SIZE;
            int row = (positioned.rely - CRAFTING_RECIPE_Y) / RECIPE_SLOT_SIZE;
            int slot = column >= 0 && column < 3 && row >= 0 && row < 3 ? column + row * 3 : fallbackSlot;
            fallbackSlot = Math.max(fallbackSlot, slot + 1);
            if (slot >= 9) continue;
            IAEStack<?> stack = toAEStack(positioned, false);
            if (stack != null) result[slot] = stack.setStackSize(1);
        }
        return result;
    }

    private static IAEStack<?>[] collectProcessingInputs(IRecipeHandler recipe, int recipeIndex) {
        IAEStack<?>[] result = new IAEStack<?>[RecipeTransferPayload.SLOT_COUNT];
        int slot = 0;
        for (PositionedStack positioned : safeList(recipe.getIngredientStacks(recipeIndex))) {
            IAEStack<?> stack = toAEStack(positioned, true);
            if (stack == null) continue;
            if (slot >= result.length) break;
            result[slot++] = stack;
        }
        return result;
    }

    private static IAEStack<?>[] collectProcessingOutputs(IRecipeHandler recipe, int recipeIndex) {
        IAEStack<?>[] result = new IAEStack<?>[RecipeTransferPayload.SLOT_COUNT];
        int slot = addOutput(result, 0, recipe.getResultStack(recipeIndex));
        String identifier = recipe.getOverlayIdentifier();
        boolean includeOther = !"smelting".equals(identifier) && !"brewing".equals(identifier);
        if (includeOther) {
            for (PositionedStack positioned : safeList(recipe.getOtherStacks(recipeIndex))) {
                slot = addOutput(result, slot, positioned);
                if (slot >= result.length) break;
            }
        }
        return result;
    }

    private static int addOutput(IAEStack<?>[] outputs, int slot, PositionedStack positioned) {
        IAEStack<?> stack = toAEStack(positioned, true);
        if (stack != null && slot < outputs.length) outputs[slot++] = stack;
        return slot;
    }

    private static IAEStack<?> toAEStack(PositionedStack positioned, boolean allowFluid) {
        if (positioned == null) return null;
        ItemStack item = positioned.item;
        if (item == null && positioned.items != null) {
            for (ItemStack alternative : positioned.items) {
                if (alternative != null) {
                    item = alternative;
                    break;
                }
            }
        }
        if (item == null) return null;
        ItemStack copy = item.copy();
        IAEStack<?> fluid = allowFluid ? Util.getAEFluidFromItem(copy) : null;
        if (fluid != null) return fluid;

        IAEStack<?> result = AEItemStack.create(copy);
        // GT's NEI renderer changes large and non-consumable stacks to size one
        // for display, while retaining the recipe amount on its positioned stack.
        if (allowFluid && positioned instanceof FixedPositionedStack fixed && fixed.realStackSize > 0) {
            result.setStackSize(fixed.realStackSize);
        }
        return result;
    }

    private static int countStacks(IAEStack<?>[] stacks) {
        int count = 0;
        for (IAEStack<?> stack : stacks) {
            if (stack != null) count++;
        }
        return count;
    }

    private static List<PositionedStack> safeList(List<PositionedStack> stacks) {
        return stacks == null ? java.util.Collections.emptyList() : stacks;
    }

    private static boolean isAltDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }
}

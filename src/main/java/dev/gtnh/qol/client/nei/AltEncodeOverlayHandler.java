package dev.gtnh.qol.client.nei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import com.glodblock.github.common.item.ItemFluidDrop;
import com.glodblock.github.common.item.ItemFluidPacket;
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
import gregtech.common.items.ItemFluidDisplay;
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
            int toolkitMode = crafting ? 0 : ProgrammableHatchesCompat.activeToolkitMode();
            boolean virtualizeNonConsumed = toolkitMode > 0;
            RecipeInputs collectedInputs = crafting ? collectCraftingInputs(recipe, recipeIndex)
                : collectProcessingInputs(recipe, recipeIndex, toolkitMode);
            IAEStack<?>[] outputs = crafting ? new IAEStack<?>[RecipeTransferPayload.SLOT_COUNT]
                : collectProcessingOutputs(recipe, recipeIndex);
            int inputCount = countStacks(collectedInputs.stacks);
            int outputCount = countStacks(outputs);
            int processingGridSize = !crafting && (inputCount > 9 || outputCount > 3) ? 4 : 3;
            boolean inverted = !crafting && processingGridSize == 4 && inputCount <= 4 && outputCount > 4;
            boolean encode = QolConfig.dualTerminal && isAltDown();

            terminal.transferRecipe(
                new RecipeTransferPayload(
                    crafting,
                    encode,
                    processingGridSize,
                    inverted,
                    collectedInputs.stacks,
                    outputs),
                interfaceSearchText(recipe, recipeIndex, crafting, virtualizeNonConsumed),
                collectedInputs.alternatives);
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
    private static String interfaceSearchText(IRecipeHandler recipe, int recipeIndex, boolean crafting,
        boolean virtualizeNonConsumed) {
        if (!crafting) return gtProcessingSearchText(recipe, recipeIndex, virtualizeNonConsumed);
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
    private static String gtProcessingSearchText(IRecipeHandler recipe, int recipeIndex,
        boolean virtualizeNonConsumed) {
        String recipeName = recipe.getRecipeName();
        if (virtualizeNonConsumed || !QolConfig.terminalGtRecipeSearchSuffix
            || !(recipe instanceof GTNEIDefaultHandler)) {
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

    private static RecipeInputs collectCraftingInputs(IRecipeHandler recipe, int recipeIndex) {
        RecipeInputs result = new RecipeInputs();
        int fallbackSlot = 0;
        for (PositionedStack positioned : safeList(recipe.getIngredientStacks(recipeIndex))) {
            if (positioned == null) continue;
            int column = (positioned.relx - CRAFTING_RECIPE_X) / RECIPE_SLOT_SIZE;
            int row = (positioned.rely - CRAFTING_RECIPE_Y) / RECIPE_SLOT_SIZE;
            int slot = column >= 0 && column < 3 && row >= 0 && row < 3 ? column + row * 3 : fallbackSlot;
            fallbackSlot = Math.max(fallbackSlot, slot + 1);
            if (slot >= 9) continue;
            IAEStack<?> stack = toAEStack(positioned, false);
            if (stack != null) result.stacks[slot] = stack.setStackSize(1);
            result.alternatives[slot] = collectAlternatives(positioned, false, false);
        }
        return result;
    }

    private static RecipeInputs collectProcessingInputs(IRecipeHandler recipe, int recipeIndex, int toolkitMode) {
        RecipeInputs result = new RecipeInputs();
        boolean virtualizeNonConsumed = toolkitMode > 0;
        List<RecipeInput> virtualInputs = new ArrayList<>();
        List<RecipeInput> regularInputs = new ArrayList<>();
        for (PositionedStack positioned : safeList(recipe.getIngredientStacks(recipeIndex))) {
            IAEStack<?> stack;
            if (isNonConsumedInput(positioned)) {
                // GT exposes molds, lenses, integrated circuits, and similar
                // catalysts as zero-sized NEI inputs. They describe the target
                // machine but must not become consumable pattern ingredients.
                // An active Programmable Hatches toolkit replaces them with
                // the mod's virtual-item wrapper, matching AE2Things.
                ItemStack nonConsumed = selectedItem(positioned);
                stack = virtualizeNonConsumed ? toAEStack(ProgrammableHatchesCompat.wrapVirtualItem(nonConsumed))
                    : null;
                if (stack != null) {
                    virtualInputs.add(new RecipeInput(stack, collectAlternatives(positioned, true, true)));
                }
            } else {
                stack = toAEStack(positioned, true);
                if (stack != null) {
                    regularInputs.add(new RecipeInput(stack, collectAlternatives(positioned, true, false)));
                }
            }
        }

        // PH mode 2 is "Add empty progcircuit if no NC inputs". Its encoding
        // hook prepends wrap(null), which resets a programmable input hatch
        // instead of allowing the previous circuit to leak into this recipe.
        if (toolkitMode == 2 && virtualInputs.isEmpty()) {
            IAEStack<?> emptyVirtual = toAEStack(ProgrammableHatchesCompat.wrapEmptyVirtualItem());
            if (emptyVirtual != null) virtualInputs.add(new RecipeInput(emptyVirtual, Collections.emptyList()));
        }

        // PH writes every converted NC input before normal ingredients. Keep
        // the same stable ordering so consecutively dispatched patterns cannot
        // consume regular inputs before their programming state is applied.
        int slot = appendInputs(result, virtualInputs, 0);
        appendInputs(result, regularInputs, slot);
        return result;
    }

    private static int appendInputs(RecipeInputs target, List<RecipeInput> inputs, int slot) {
        for (RecipeInput input : inputs) {
            if (slot >= target.stacks.length) break;
            target.stacks[slot] = input.stack;
            target.alternatives[slot] = input.alternatives;
            slot++;
        }
        return slot;
    }

    private static List<IAEStack<?>> collectAlternatives(PositionedStack positioned, boolean allowFluid,
        boolean virtualize) {
        if (positioned == null || positioned.items == null || positioned.items.length == 0) {
            return Collections.emptyList();
        }
        List<IAEStack<?>> alternatives = new ArrayList<>();
        for (ItemStack item : positioned.items) {
            if (item == null) continue;
            ItemStack candidate = virtualize ? ProgrammableHatchesCompat.wrapVirtualItem(item) : item;
            IAEStack<?> alternative = virtualize ? toAEStack(candidate) : toAEStack(positioned, candidate, allowFluid);
            if (alternative == null || containsSameType(alternatives, alternative)) continue;
            alternatives.add(alternative);
        }
        return alternatives;
    }

    private static boolean containsSameType(List<IAEStack<?>> alternatives, IAEStack<?> candidate) {
        for (IAEStack<?> alternative : alternatives) {
            if (alternative.isSameType(candidate)) return true;
        }
        return false;
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
        ItemStack item = selectedItem(positioned);
        return toAEStack(positioned, item, allowFluid);
    }

    private static IAEStack<?> toAEStack(PositionedStack positioned, ItemStack item, boolean allowFluid) {
        if (item == null) return null;
        ItemStack copy = item.copy();
        // A filled cell or another real fluid container is still an item
        // ingredient. Converting every container through getAEFluidFromItem
        // breaks machines such as the single-block mixer, which can accept a
        // cell but cannot accept a second fluid input. Only NEI's synthetic
        // fluid representations should become an AE fluid stack.
        IAEStack<?> fluid = allowFluid && isFluidDisplay(copy) ? Util.getAEFluidFromItem(copy) : null;
        if (fluid != null) return fluid;

        IAEStack<?> result = AEItemStack.create(copy);
        // GT's NEI renderer changes large and non-consumable stacks to size one
        // for display, while retaining the recipe amount on its positioned stack.
        if (allowFluid && positioned instanceof FixedPositionedStack fixed && fixed.realStackSize > 0) {
            result.setStackSize(fixed.realStackSize);
        }
        return result;
    }

    private static boolean isFluidDisplay(ItemStack stack) {
        return stack.getItem() instanceof ItemFluidDisplay || stack.getItem() instanceof ItemFluidDrop
            || stack.getItem() instanceof ItemFluidPacket;
    }

    private static IAEStack<?> toAEStack(ItemStack item) {
        if (item == null) return null;
        ItemStack copy = item.copy();
        copy.stackSize = Math.max(1, copy.stackSize);
        return AEItemStack.create(copy);
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

    private static final class RecipeInputs {

        private final IAEStack<?>[] stacks = new IAEStack<?>[RecipeTransferPayload.SLOT_COUNT];
        @SuppressWarnings("unchecked")
        private final List<IAEStack<?>>[] alternatives = new List[RecipeTransferPayload.SLOT_COUNT];

        private RecipeInputs() {
            java.util.Arrays.fill(alternatives, Collections.emptyList());
        }
    }

    private static final class RecipeInput {

        private final IAEStack<?> stack;
        private final List<IAEStack<?>> alternatives;

        private RecipeInput(IAEStack<?> stack, List<IAEStack<?>> alternatives) {
            this.stack = stack;
            this.alternatives = alternatives;
        }
    }
}

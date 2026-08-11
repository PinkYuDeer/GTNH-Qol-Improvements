package dev.gtnh.qol.client.nei;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;

import appeng.integration.modules.NEIHelpers.TerminalCraftingSlotFinder;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.api.API;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiOverlayButton;
import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.GuiRecipeButton.UpdateRecipeButtonsEvent;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.util.NEIMouseUtils;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.gtnh.qol.client.terminal.GuiQuickEncodingTerminal;

public final class NeiIntegration {

    private static final AltEncodeOverlayHandler TRANSFER_HANDLER = new AltEncodeOverlayHandler();
    private static final Set<String> REGISTERED_IDENTIFIERS = new HashSet<>();
    private static final RecipeButtonHandler RECIPE_BUTTON_HANDLER = new RecipeButtonHandler();
    private static final BookmarkCraftHandler BOOKMARK_CRAFT_HANDLER = new BookmarkCraftHandler();
    private static boolean recipeButtonHandlerRegistered;
    private static boolean bookmarkCraftHandlerRegistered;

    private NeiIntegration() {}

    public static void register() {
        if (!recipeButtonHandlerRegistered) {
            recipeButtonHandlerRegistered = true;
            MinecraftForge.EVENT_BUS.register(RECIPE_BUTTON_HANDLER);
        }
        if (!bookmarkCraftHandlerRegistered) {
            bookmarkCraftHandlerRegistered = true;
            BookmarkCraftHandler.registerFirst(BOOKMARK_CRAFT_HANDLER);
        }
        registerIdentifier("crafting", true);
        registerIdentifier("crafting2x2", true);
        registerIdentifier("smelting", false);
        registerIdentifier("brewing", false);
    }

    /** Called after all NEI plugins have contributed their machine recipe handlers. */
    public static void registerKnownHandlers() {
        // This is the authoritative list used by AE2 Fluid Craft and AE2Things.
        // In particular, several GT processing handlers are not present in
        // GuiCraftingRecipe.craftinghandlers when our ordinary FML init runs.
        registerFluidCraftHandlers();
        registerHandlers(GuiCraftingRecipe.craftinghandlers);
        registerHandlers(GuiCraftingRecipe.serialCraftingHandlers);
    }

    private static void registerFluidCraftHandlers() {
        try {
            Object supported = Class.forName("com.glodblock.github.nei.recipes.FluidRecipe")
                .getMethod("getSupportRecipes")
                .invoke(null);
            if (!(supported instanceof Iterable<?>identifiers)) return;
            for (Object identifier : identifiers) {
                if (identifier instanceof String value) registerIdentifier(value, false);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
    }

    private static void registerHandlers(List<? extends IRecipeHandler> handlers) {
        if (handlers == null) return;
        for (IRecipeHandler handler : handlers) {
            try {
                registerIdentifier(handler.getOverlayIdentifier(), false);
            } catch (RuntimeException | LinkageError ignored) {}
        }
    }

    private static void registerIdentifier(String identifier, boolean craftingOverlay) {
        if (identifier == null || identifier.isEmpty() || !REGISTERED_IDENTIFIERS.add(identifier)) return;
        if (craftingOverlay) {
            API.registerGuiOverlay(GuiQuickEncodingTerminal.class, identifier, new TerminalCraftingSlotFinder());
        }
        if (!API.hasGuiOverlayHandler(GuiQuickEncodingTerminal.class, identifier)) {
            API.registerGuiOverlayHandler(GuiQuickEncodingTerminal.class, TRANSFER_HANDLER, identifier);
        }
    }

    /**
     * NEI disables its stock plus button before invoking an overlay handler when
     * it cannot match a machine recipe to a vanilla crafting grid. A pattern
     * terminal does not have that restriction: it stores the recipe itself and
     * this terminal can select 3x3, 4x4, or inverted 4x4 after inspecting it.
     */
    public static final class RecipeButtonHandler {

        @SubscribeEvent
        public void replaceOverlayButton(UpdateRecipeButtonsEvent.Post event) {
            for (int i = 0; i < event.buttonList.size(); i++) {
                GuiRecipeButton button = event.buttonList.get(i);
                if (button instanceof GuiOverlayButton overlay
                    && overlay.firstGui instanceof GuiQuickEncodingTerminal) {
                    event.buttonList.set(i, new QuickEncodingOverlayButton(overlay));
                }
            }
        }
    }

    private static final class QuickEncodingOverlayButton extends GuiOverlayButton {

        private QuickEncodingOverlayButton(GuiOverlayButton original) {
            super(original.firstGui, original.handlerRef, original.xPosition, original.yPosition);
            // Encoding fake slots never require ingredients to be present in the
            // player's inventory. Mark this as a valid fill target regardless of
            // the currently selected encoder layout.
            canFillCraftingGrid = true;
            hasOverlay = true;
            requireShiftForOverlayRecipe = false;
            missedMaterialsTooltipLineHandler = null;
            enabled = true;
        }

        @Override
        protected List<ItemOverlayState> ingredientsOverlay() {
            // The terminal records ingredients; it does not consume them while
            // filling the pattern, so NEI's missing-material overlay is misleading.
            return java.util.Collections.emptyList();
        }

        @Override
        public Map<String, String> handleHotkeys(int mouseX, int mouseY, Map<String, String> hotkeys) {
            Map<String, String> result = super.handleHotkeys(mouseX, mouseY, hotkeys);
            result.put(
                NEIClientUtils.getKeyName(NEIClientUtils.ALT_HASH, NEIMouseUtils.MOUSE_BTN_LMB),
                StatCollector.translateToLocal("gtnh_qol_improvements.nei.encode_search_upload"));
            return result;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            if (!(firstGui instanceof GuiQuickEncodingTerminal terminal)) return;
            firstGui.mc.displayGuiScreen(firstGui);
            // Call our transfer path directly. This also supports recipe handlers
            // registered after load-complete and avoids NEI's crafting-grid gate.
            IOverlayHandler handler = TRANSFER_HANDLER;
            handler.overlayRecipe(terminal, handlerRef.handler, handlerRef.recipeIndex, false);
        }
    }
}

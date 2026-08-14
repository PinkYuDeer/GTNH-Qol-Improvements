package dev.gtnh.qol.client.terminal;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.api.config.ActionItems;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.PatternBeSubstitution;
import appeng.api.config.PatternSlotConfig;
import appeng.api.config.PinsRows;
import appeng.api.config.Settings;
import appeng.api.config.StringOrder;
import appeng.api.config.TerminalStyle;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.IInterfaceTerminalPostUpdate;
import appeng.client.gui.ScreenColor;
import appeng.client.gui.implementations.GuiInterfaceTerminal;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.slots.VirtualMEMonitorableSlot;
import appeng.client.gui.slots.VirtualMEPatternSlot;
import appeng.client.gui.slots.VirtualMEPinSlot;
import appeng.client.gui.slots.VirtualMESlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiSimpleImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.ItemRepo;
import appeng.client.texture.ExtraBlockTextures;
import appeng.container.AEBaseContainer;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.AEConfig;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate.PacketEntry;
import appeng.core.sync.packets.PacketMonitorableAction;
import appeng.helpers.MonitorableAction;
import appeng.util.Platform;
import dev.gtnh.qol.GTNHQolImprovements;
import dev.gtnh.qol.config.QolConfig;
import dev.gtnh.qol.network.QolNetwork;
import dev.gtnh.qol.terminal.ContainerQuickEncodingTerminal;
import dev.gtnh.qol.terminal.InterfacePatternTarget;
import dev.gtnh.qol.terminal.RecipeTransferPayload;
import dev.gtnh.qol.terminal.StorageFluidRequest;

/**
 * AE2Things-style single page: ME inventory on the left, the native interface
 * terminal in the centre, and the native pattern encoder on the right.
 */
public final class GuiQuickEncodingTerminal extends GuiPatternTerm implements IInterfaceTerminalPostUpdate {

    private static final int ITEM_PANEL_WIDTH = 101;
    private static final int BUTTON_COLUMN_WIDTH = 18;
    private static final int ITEM_PANEL_X = -ITEM_PANEL_WIDTH - BUTTON_COLUMN_WIDTH;
    private static final int BUTTON_COLUMN_X = -BUTTON_COLUMN_WIDTH;
    private static final int PATTERN_PANEL_X = 209;
    private static final int PATTERN_PANEL_WIDTH = 101;
    private static final int PATTERN_PANEL_TOP = -12;
    private static final int PATTERN_PANEL_TEXTURE_HEIGHT = 196;
    private static final int VIEW_CELL_PANEL_GAP = 4;
    private static final int VIEW_CELL_PANEL_HEIGHT = 24;
    private static final int VIEW_CELL_SLOT_X = 5;
    private static final int VIEW_CELL_SLOT_Y = 4;
    private static final int PATTERN_PANEL_HEIGHT = PATTERN_PANEL_TEXTURE_HEIGHT + VIEW_CELL_PANEL_GAP
        + VIEW_CELL_PANEL_HEIGHT
        - PATTERN_PANEL_TOP;
    private static final int PATTERN_PROCESSING_SCROLL_X = 9;
    private static final int PATTERN_PROCESSING_SCROLL_HEIGHT = 70;
    private static final int PATTERN_PROCESSING_GRID_X = 19;
    private static final int PATTERN_PROCESSING_GRID_Y = 11;
    private static final int PATTERN_INVERTED_GRID_Y = 50;
    private static final int PATTERN_NORMAL_SMALL_ROW_Y = 108;
    private static final int PATTERN_INVERTED_SMALL_ROW_Y = 11;
    private static final int PATTERN_CRAFTING_GRID_X = 24;
    private static final int PATTERN_CRAFTING_GRID_Y = 20;
    private static final int PATTERN_CRAFTING_OUTPUT_Y = 98;
    private static final int PATTERN_PROCESSING_4X4_OUTPUT_Y_OFFSET = -1;
    private static final int PATTERN_BLANK_SLOT_X = 20;
    private static final int PATTERN_ENCODE_BUTTON_X = 42;
    private static final int PATTERN_ENCODED_SLOT_X = 64;
    private static final int PATTERN_PATTERN_ROW_Y = 140;
    private static final int PATTERN_TAB_Y = 167;
    private static final int ITEM_COLUMNS = 4;
    private static final int DEFAULT_ITEM_ROWS = 4;
    private static final int DEFAULT_STORAGE_BUTTONS_PER_COLUMN = 4;
    private static final int GL_CLIENT_ALL_ATTRIB_BITS = 0xFFFFFFFF;

    @SuppressWarnings("unchecked")
    private final List<IAEStack<?>>[] recipeInputAlternatives = new List[RecipeTransferPayload.SLOT_COUNT];

    private static final ResourceLocation ITEM_PANEL = new ResourceLocation(
        GTNHQolImprovements.MOD_ID,
        "textures/gui/widget/items.png");
    private static final ResourceLocation VIEW_CELL_PANEL = new ResourceLocation(
        GTNHQolImprovements.MOD_ID,
        "textures/gui/widget/view_cells.png");
    private static final ResourceLocation PATTERN_PANEL = new ResourceLocation(
        GTNHQolImprovements.MOD_ID,
        "textures/gui/widget/encoding.png");
    private static final ResourceLocation PATTERN_PANEL_3X3 = new ResourceLocation(
        GTNHQolImprovements.MOD_ID,
        "textures/gui/widget/encoding3.png");
    private static final ResourceLocation PATTERN_PANEL_4X4 = new ResourceLocation(
        GTNHQolImprovements.MOD_ID,
        "textures/gui/widget/encoding4.png");
    private static final ResourceLocation PATTERN_PANEL_4X4_INVERTED = new ResourceLocation(
        GTNHQolImprovements.MOD_ID,
        "textures/gui/widget/encoding4_inverted.png");

    private static final Field ITEM_REPO = findField(GuiMEMonitorable.class, "repo");
    private static final Field ITEM_REPO_LIST = findField(ItemRepo.class, "list");
    private static final Field PATTERN_GUI_CRAFTING_MODE = findField(GuiPatternTerm.class, "craftingMode");

    private final ContainerQuickEncodingTerminal patternContainer;
    private final EmbeddedInterfaceTerminal interfaceTerminal;
    private final List<GuiButton> interfaceButtons = new ArrayList<>();
    private final List<GuiButton> storageButtons = new ArrayList<>();
    private final List<GuiButton> patternButtons = new ArrayList<>();
    private final GuiScrollbar processingScrollBar = new GuiScrollbar();

    private ItemRepo itemRepo;
    private int itemRows = DEFAULT_ITEM_ROWS;
    private int pinDisplayRows;
    private int visibleItemSlots = DEFAULT_ITEM_ROWS * ITEM_COLUMNS;
    private int itemPanelHeight = panelHeight(DEFAULT_ITEM_ROWS);
    private int itemPanelY;
    private VirtualMEPinSlot[] visiblePinSlots = new VirtualMEPinSlot[0];
    private TerminalStyle configuredTerminalStyle;
    private GuiImgButton invertButton;
    private GuiImgButton pinStateButton;
    private ModeButton craftingModeButton;
    private ModeButton processing4ModeButton;
    private ModeButton processing3ModeButton;
    private RightEncodingButton encodeButton;
    private GuiSimpleImgButton searchMappingButton;
    private GuiButton craftingStatusButton;
    private boolean initializedOnce;
    private boolean pendingPinRefresh;
    private String pendingInterfaceSearch;
    private String activeInterfaceSearchMappingKey;
    private String activeInterfaceSearchDefault;
    private int pendingTargetSelectionTicks;
    private boolean pendingAutoPlace;
    private long suppressExtensionClickUntil;
    private int suppressExtensionButton = -1;

    public GuiQuickEncodingTerminal(InventoryPlayer inventoryPlayer, ITerminalHost host) {
        this(inventoryPlayer, host, new ContainerQuickEncodingTerminal(inventoryPlayer, host));
    }

    private GuiQuickEncodingTerminal(InventoryPlayer inventoryPlayer, ITerminalHost host,
        ContainerQuickEncodingTerminal container) {
        super(inventoryPlayer, host, container);
        patternContainer = container;
        interfaceTerminal = new EmbeddedInterfaceTerminal(container);
        processingScrollBar.setHeight(PATTERN_PROCESSING_SCROLL_HEIGHT)
            .setWidth(7)
            .setLeft(PATTERN_PANEL_X + PATTERN_PROCESSING_SCROLL_X);
        processingScrollBar.setRange(0, 1, 1);
        processingScrollBar.setTexture("appliedenergistics2", "guis/pattern3.png", 242, 0);
    }

    @Override
    public void initGui() {
        super.initGui();

        int oldLeft = guiLeft;
        int oldTop = guiTop;
        List<GuiButton> nativeButtons = new ArrayList<>(buttonList);

        interfaceTerminal.initialize(mc, width, height);
        guiLeft = interfaceTerminal.left();
        guiTop = interfaceTerminal.top();
        xSize = interfaceTerminal.guiWidth();
        ySize = interfaceTerminal.guiHeight();
        updateItemGeometry();

        buttonList.clear();
        classifyNativeButtons(nativeButtons, oldLeft, oldTop);
        buttonList.addAll(storageButtons);
        buttonList.addAll(patternButtons);
        if (craftingStatusButton != null) buttonList.add(craftingStatusButton);

        interfaceButtons.clear();
        for (GuiButton button : interfaceTerminal.buttons()) {
            // The embedded terminal's tab buttons include Crafting Status and
            // "return to original GUI". They do not belong in the compact
            // settings column and were the source of the odd long button.
            if (button instanceof GuiImgButton) interfaceButtons.add(button);
        }
        buttonList.addAll(interfaceButtons);

        craftingModeButton = createModeButton(Blocks.crafting_table, "gtnh_qol_improvements.terminal.mode.crafting");
        processing4ModeButton = patternContainer.supportsExtendedProcessing()
            ? createModeButton(Blocks.dispenser, "gtnh_qol_improvements.terminal.mode.processing_4x4")
            : null;
        processing3ModeButton = createModeButton(Blocks.furnace, "gtnh_qol_improvements.terminal.mode.processing_3x3");
        encodeButton = new RightEncodingButton();
        searchMappingButton = new GuiSimpleImgButton(
            0,
            0,
            70,
            StatCollector.translateToLocal("gtnh_qol_improvements.terminal.edit_search_mapping"));
        patternButtons.add(craftingModeButton);
        if (processing4ModeButton != null) patternButtons.add(processing4ModeButton);
        patternButtons.add(processing3ModeButton);
        patternButtons.add(encodeButton);
        patternButtons.add(searchMappingButton);
        buttonList.add(craftingModeButton);
        if (processing4ModeButton != null) buttonList.add(processing4ModeButton);
        buttonList.add(processing3ModeButton);
        buttonList.add(encodeButton);
        buttonList.add(searchMappingButton);

        invertButton = new GuiImgButton(
            guiLeft + PATTERN_PANEL_X + 87,
            guiTop + 20,
            Settings.ACTIONS,
            patternContainer.invertedSync.get() ? PatternSlotConfig.C_4_16 : PatternSlotConfig.C_16_4);
        invertButton.setHalfSize(true);
        patternButtons.add(invertButton);
        buttonList.add(invertButton);

        configureItemPanel();
        registerCraftableIngredientOverlays();
        layoutButtons();
        layoutPatternSlots();
        layoutContainerSlots();
        pendingPinRefresh = true;

        // Returning from NEI or another GUI calls initGui again on the same
        // screen. Focus only the first initialization so recipe hotkeys such as
        // R and A are not consumed when the player comes back.
        searchField.setFocused(false);
        if (initializedOnce) {
            interfaceTerminal.clearSearchFocus();
        } else {
            initializedOnce = true;
            interfaceTerminal.focusNameSearch();
        }
    }

    private void classifyNativeButtons(List<GuiButton> buttons, int oldLeft, int oldTop) {
        storageButtons.clear();
        patternButtons.clear();
        pinStateButton = null;
        craftingStatusButton = null;

        for (GuiButton button : buttons) {
            if (button instanceof GuiImgButton image && image.getSetting() == Settings.CRAFTING_STATUS) {
                craftingStatusButton = button;
            } else if (button instanceof GuiTabButton && button.yPosition <= oldTop + 20) {
                // AE2 can render Crafting Status either as an image button or
                // as a tab. Keep the native instance so its tooltip, texture
                // and PacketSwitchGuis behaviour remain resource-pack aware.
                craftingStatusButton = button;
            } else if (button.xPosition < oldLeft) {
                storageButtons.add(button);
            } else if (button instanceof GuiTabButton && button.yPosition > oldTop + 20) {
                // Replaced below with three always-visible, directly selectable
                // mode buttons: crafting, 4x4 processing and 3x3 processing.
                continue;
            } else if (button instanceof GuiImgButton image && isPatternButton(image)) {
                // Keep AE2's native Encode Pattern button, but discard its
                // native slot-direction button. The explicit action below is
                // required for this combined container's client/server sync.
                Enum<?> value = image.getCurrentValue();
                if (value != ActionItems.ENCODE && !(value instanceof PatternSlotConfig)) patternButtons.add(button);
            } else if (button instanceof GuiImgButton image) {
                // The pin-state control is placed on the right by AE2, while all
                // other monitor controls start on the left. They still belong
                // to the same ME Storage control group on this combined GUI.
                if (image.getCurrentValue() == ActionItems.PINS) pinStateButton = image;
                storageButtons.add(button);
            }
        }
    }

    private ModeButton createModeButton(net.minecraft.block.Block icon, String translationKey) {
        return new ModeButton(new ItemStack(icon), StatCollector.translateToLocal(translationKey), itemRender);
    }

    private static boolean isPatternButton(GuiImgButton button) {
        Enum<?> value = button.getCurrentValue();
        return value == ActionItems.ENCODE || value == ActionItems.CLOSE
            || value == ActionItems.DOUBLE
            || value instanceof ItemSubstitution
            || value instanceof PatternBeSubstitution
            || value instanceof PatternSlotConfig;
    }

    private void configureItemPanel() {
        itemRepo = readItemRepo();
        if (itemRepo != null) itemRepo.setRowSize(ITEM_COLUMNS);
        ensureItemPanelSlots();

        layoutPinSlots();
        if (monitorableSlots != null) {
            int visiblePinSlotCount = pinDisplayRows * ITEM_COLUMNS;
            for (int i = 0; i < monitorableSlots.length; i++) {
                VirtualMEMonitorableSlot slot = monitorableSlots[i];
                boolean visible = i < visibleItemSlots;
                slot.setHidden(!visible);
                if (visible) {
                    int panelIndex = visiblePinSlotCount + i;
                    slot.setX(ITEM_PANEL_X + 5 + panelIndex % ITEM_COLUMNS * 18);
                    slot.setY(itemPanelY + 18 + panelIndex / ITEM_COLUMNS * 18);
                }
            }
        }

        searchField.x = guiLeft + ITEM_PANEL_X + 3;
        searchField.y = guiTop + itemPanelY + 4;
        updateItemScrollBar();
        getScrollBar().setVisible(false);
    }

    private void ensureItemPanelSlots() {
        if (itemRepo == null || monitorableSlots == null) return;
        int requiredSlots = visibleItemSlots;
        if (monitorableSlots.length >= requiredSlots) return;

        int oldLength = monitorableSlots.length;
        monitorableSlots = Arrays.copyOf(monitorableSlots, requiredSlots);
        for (int i = oldLength; i < requiredSlots; i++) {
            VirtualMEMonitorableSlot slot = new VirtualMEMonitorableSlot(
                0,
                0,
                itemRepo,
                i,
                type -> typeFilters == null || typeFilters.isEnabled(type));
            monitorableSlots[i] = slot;
            registerVirtualSlots(slot);
        }
    }

    /**
     * Draw AE2's encoded-pattern badge over recipe ingredients that are
     * already craftable on the connected network. Keep this as a separate
     * render-only virtual slot so the recipe stack synchronized by the
     * container never receives transient network state.
     */
    private void registerCraftableIngredientOverlays() {
        if (craftingSlots == null) return;
        for (VirtualMEPatternSlot slot : craftingSlots) {
            registerVirtualSlots(new CraftableIngredientOverlaySlot(slot));
        }
    }

    private boolean isCraftableOnNetwork(IAEStack<?> ingredient) {
        if (ingredient == null || itemRepo == null || ITEM_REPO_LIST == null) return false;
        try {
            @SuppressWarnings("unchecked")
            IItemList<IAEStack<?>> networkStacks = (IItemList<IAEStack<?>>) ITEM_REPO_LIST.get(itemRepo);
            IAEStack<?> networkStack = networkStacks == null ? null : networkStacks.findPrecise(ingredient);
            return networkStack != null && networkStack.isCraftable();
        } catch (IllegalAccessException ignored) {
            return false;
        }
    }

    /**
     * The server still allocates pins in AE2's native groups of nine, while this
     * terminal exposes independently configured visual rows of four. Only the
     * requested prefix of each crafting/player section is visible; surplus
     * native capacity remains hidden and available to AE2's original backend.
     */
    private void layoutPinSlots() {
        if (pinSlots == null || pinSlots.length == 0) {
            visiblePinSlots = new VirtualMEPinSlot[0];
            return;
        }

        int craftingLimit = Math.max(0, patternContainer.craftingPinRowsSync.get()) * ITEM_COLUMNS;
        int playerLimit = Math.max(0, patternContainer.playerPinRowsSync.get()) * ITEM_COLUMNS;
        int craftingIndex = 0;
        int playerIndex = 0;
        List<VirtualMEPinSlot> visible = new ArrayList<>();
        for (VirtualMEPinSlot slot : pinSlots) {
            int sectionIndex = slot.isCraftingSlot() ? craftingIndex++ : playerIndex++;
            int sectionLimit = slot.isCraftingSlot() ? craftingLimit : playerLimit;
            boolean show = sectionIndex < sectionLimit;
            slot.setHidden(!show);
            if (!show) continue;

            int panelIndex = visible.size();
            slot.setX(ITEM_PANEL_X + 5 + panelIndex % ITEM_COLUMNS * 18);
            slot.setY(itemPanelY + 18 + panelIndex / ITEM_COLUMNS * 18);
            visible.add(slot);
        }
        visiblePinSlots = visible.toArray(new VirtualMEPinSlot[visible.size()]);
    }

    private int countPinDisplayRows() {
        return Math.max(0, patternContainer.craftingPinRowsSync.get())
            + Math.max(0, patternContainer.playerPinRowsSync.get());
    }

    private void updateItemScrollBar() {
        int size = itemRepo == null ? 0 : itemRepo.size();
        getScrollBar().setLeft(ITEM_PANEL_X + ITEM_PANEL_WIDTH - 20)
            // Pin slots stay fixed, but AE2's scrollbar track covers the full
            // storage grid. Starting it below the pins made scroll zero look
            // like a partially scrolled position.
            .setTop(itemPanelY + 18)
            .setHeight(itemRows * 18 - 2)
            .setRange(
                0,
                Math.max(0, (size - visibleItemSlots + ITEM_COLUMNS - 1) / ITEM_COLUMNS),
                Math.max(1, itemRows / 6));
    }

    private void layoutButtons() {
        if (craftingStatusButton != null) {
            if (craftingStatusButton instanceof GuiTabButton) {
                // GuiTabButton draws a 25 px frame despite reporting a 22 px
                // width. AE2's vanilla frame also contains a four-pixel top
                // connector, so keep the native offsets to make both edges
                // meet the interface terminal background cleanly.
                craftingStatusButton.xPosition = guiLeft + xSize - 25;
                craftingStatusButton.yPosition = guiTop - 4;
            } else {
                craftingStatusButton.xPosition = guiLeft + xSize - 18;
                craftingStatusButton.yPosition = guiTop;
            }
        }

        // Interface and storage have independent controls. Interface controls
        // keep their original column beside the central terminal.
        int interfaceColumnX = guiLeft + BUTTON_COLUMN_X;
        int y = guiTop;
        for (GuiButton button : interfaceButtons) {
            button.xPosition = interfaceColumnX;
            button.yPosition = y;
            y += 18;
        }

        // Storage controls start immediately to the left of ME Storage. Keep
        // no more than four in a vertical strip; additional controls form new
        // strips further to the left, as in AE2Things' item panel.
        int storageColumnX = guiLeft + ITEM_PANEL_X - BUTTON_COLUMN_WIDTH;
        int storageTop = guiTop + itemPanelY;
        int storageButtonsPerColumn = configuredTerminalStyle == TerminalStyle.TALL
            ? Math.max(DEFAULT_STORAGE_BUTTONS_PER_COLUMN, itemPanelHeight / 18)
            : DEFAULT_STORAGE_BUTTONS_PER_COLUMN;
        for (int i = 0; i < storageButtons.size(); i++) {
            GuiButton button = storageButtons.get(i);
            int column = i / storageButtonsPerColumn;
            int row = i % storageButtonsPerColumn;
            button.xPosition = storageColumnX - column * BUTTON_COLUMN_WIDTH;
            button.yPosition = storageTop + row * 18;
        }

        int right = guiLeft + PATTERN_PANEL_X;
        int panelTop = guiTop + patternPanelY();
        for (GuiButton button : patternButtons) {
            if (button == searchMappingButton) {
                button.xPosition = right + 2;
                button.yPosition = panelTop + PATTERN_PATTERN_ROW_Y;
                button.visible = activeInterfaceSearchMappingKey != null;
                button.enabled = button.visible;
                continue;
            }
            if (button instanceof GuiTabButton) {
                button.xPosition = right + (patternContainer.supportsExtendedProcessing()
                    ? button == craftingModeButton ? 13 : button == processing3ModeButton ? 38 : 63
                    : button == craftingModeButton ? 25 : 58);
                button.yPosition = panelTop + PATTERN_TAB_Y;
                continue;
            }
            GuiImgButton image = (GuiImgButton) button;
            Enum<?> value = image.getCurrentValue();
            boolean centralGrid = patternContainer.isCraftingMode()
                || patternContainer.processingGridSizeSync.get() == 3;
            int controlX = 13;
            int controlY = centralGrid ? 75 : patternContainer.invertedSync.get() ? 29 : 84;
            if (value == ActionItems.ENCODE) {
                button.xPosition = right + PATTERN_ENCODE_BUTTON_X;
                button.yPosition = panelTop + PATTERN_PATTERN_ROW_Y;
            } else if (value == ActionItems.CLOSE) {
                button.xPosition = right + controlX;
                button.yPosition = panelTop + controlY;
            } else if (value == ActionItems.DOUBLE) {
                button.xPosition = right + controlX + 10;
                button.yPosition = panelTop + controlY;
            } else if (value instanceof ItemSubstitution) {
                button.xPosition = right + controlX + 20;
                button.yPosition = panelTop + controlY;
            } else if (value instanceof PatternBeSubstitution) {
                button.xPosition = right + controlX + 66;
                button.yPosition = panelTop + controlY;
            }
        }
        if (invertButton != null) {
            invertButton.xPosition = right + 59;
            invertButton.yPosition = panelTop
                + (patternContainer.isCraftingMode() || patternContainer.processingGridSizeSync.get() == 3 ? 75
                    : patternContainer.invertedSync.get() ? 29 : 84);
        }
    }

    private void layoutPatternSlots() {
        boolean crafting = patternContainer.isCraftingMode();
        synchronizeNativeCraftingMode(crafting);
        boolean compactProcessing = !crafting && patternContainer.processingGridSizeSync.get() == 3;
        boolean inverted = patternContainer.invertedSync.get();
        int activePage = patternContainer.activePageSync.get();
        int panelY = patternPanelY();
        if (craftingSlots != null) {
            for (int i = 0; i < craftingSlots.length; i++) {
                VirtualMEPatternSlot slot = craftingSlots[i];
                if (crafting) {
                    slot.setHidden(i >= 9);
                    if (i < 9) {
                        slot.setX(PATTERN_PANEL_X + PATTERN_CRAFTING_GRID_X + i % 3 * 18);
                        slot.setY(panelY + PATTERN_CRAFTING_GRID_Y + i / 3 * 18);
                    }
                } else if (compactProcessing) {
                    slot.setHidden(i >= 9);
                    if (i < 9) {
                        slot.setX(PATTERN_PANEL_X + PATTERN_CRAFTING_GRID_X + i % 3 * 18);
                        slot.setY(panelY + PATTERN_CRAFTING_GRID_Y + i / 3 * 18);
                    }
                } else {
                    int page = i / 16;
                    int withinPage = i % 16;
                    int x = withinPage % 4;
                    int y = withinPage / 4;
                    slot.setHidden(inverted ? y != activePage || page > 0 : page != activePage);
                    slot.setX(PATTERN_PANEL_X + PATTERN_PROCESSING_GRID_X + x * 18);
                    slot.setY(panelY + (inverted ? PATTERN_INVERTED_SMALL_ROW_Y : PATTERN_PROCESSING_GRID_Y + y * 18));
                }
                slot.setShowAmount(!crafting);
            }
        }
        if (outputSlots != null) {
            for (int i = 0; i < outputSlots.length; i++) {
                VirtualMEPatternSlot slot = outputSlots[i];
                int page = i / 16;
                int withinPage = i % 16;
                int x = withinPage % 4;
                int y = withinPage / 4;
                if (compactProcessing) {
                    slot.setHidden(i >= 3);
                    if (i < 3) {
                        slot.setX(PATTERN_PANEL_X + PATTERN_CRAFTING_GRID_X + i * 18);
                        slot.setY(panelY + PATTERN_CRAFTING_OUTPUT_Y);
                    }
                } else {
                    slot.setHidden(crafting || (!inverted ? y != activePage || page != 0 : page != activePage));
                    slot.setX(PATTERN_PANEL_X + PATTERN_PROCESSING_GRID_X + x * 18);
                    slot.setY(
                        panelY + (inverted ? PATTERN_INVERTED_GRID_Y + y * 18 : PATTERN_NORMAL_SMALL_ROW_Y)
                            + PATTERN_PROCESSING_4X4_OUTPUT_Y_OFFSET);
                }
            }
        }

        if (craftingModeButton != null) craftingModeButton.setSelected(crafting);
        if (processing4ModeButton != null) processing4ModeButton.setSelected(!crafting && !compactProcessing);
        if (processing3ModeButton != null) processing3ModeButton.setSelected(compactProcessing);
        for (GuiButton button : patternButtons) {
            if (!(button instanceof GuiImgButton image)) continue;
            Enum<?> value = image.getCurrentValue();
            if (value == ActionItems.DOUBLE) button.visible = !crafting;
            if (value instanceof PatternSlotConfig) button.visible = !crafting && !compactProcessing;
            if (value == ItemSubstitution.ENABLED) button.visible = patternContainer.substituteSync.get();
            if (value == ItemSubstitution.DISABLED) button.visible = !patternContainer.substituteSync.get();
            if (value == PatternBeSubstitution.ENABLED) button.visible = patternContainer.beSubstituteSync.get();
            if (value == PatternBeSubstitution.DISABLED) button.visible = !patternContainer.beSubstituteSync.get();
        }
        if (invertButton != null) {
            invertButton.set(inverted ? PatternSlotConfig.C_4_16 : PatternSlotConfig.C_16_4);
            invertButton.visible = !crafting && !compactProcessing;
        }
        processingScrollBar.setTop(panelY + (inverted ? PATTERN_INVERTED_GRID_Y : PATTERN_PROCESSING_GRID_Y));
        processingScrollBar.setCurrentScroll(activePage);
    }

    private void synchronizeNativeCraftingMode(boolean crafting) {
        if (PATTERN_GUI_CRAFTING_MODE == null) return;
        try {
            PATTERN_GUI_CRAFTING_MODE.setBoolean(this, crafting);
        } catch (IllegalAccessException ignored) {}
    }

    public void transferRecipe(RecipeTransferPayload payload, String interfaceSearchMappingKey,
        String defaultInterfaceSearch, List<IAEStack<?>>[] inputAlternatives) {
        if (!patternContainer.supportsExtendedProcessing() && !payload.isCrafting()
            && payload.getProcessingGridSize() != 3) {
            mc.thePlayer.addChatMessage(
                new net.minecraft.util.ChatComponentTranslation("gtnh_qol_improvements.terminal.requires_4x4"));
            return;
        }
        // NEI invokes the overlay while its recipe screen is current, then
        // reinitializes this terminal when returning. Defer all interface-list
        // work until drawScreen runs on the restored terminal; otherwise AE2's
        // init replaces both the search value and the selected entry.
        interfaceTerminal.clearHighlight();
        activeInterfaceSearchMappingKey = interfaceSearchMappingKey == null || interfaceSearchMappingKey.isEmpty()
            || defaultInterfaceSearch == null
            || defaultInterfaceSearch.isEmpty() ? null : interfaceSearchMappingKey;
        activeInterfaceSearchDefault = activeInterfaceSearchMappingKey == null ? null : defaultInterfaceSearch;
        pendingInterfaceSearch = activeInterfaceSearchMappingKey == null ? ""
            : InterfaceSearchMappings.resolve(activeInterfaceSearchMappingKey, activeInterfaceSearchDefault);
        pendingTargetSelectionTicks = 0;
        pendingAutoPlace = payload.shouldEncode();
        rememberRecipeAlternatives(inputAlternatives);
        patternContainer.requestRecipeTransfer(payload);
        layoutButtons();
        layoutPatternSlots();
    }

    void refreshInterfaceSearchMapping(String mappingKey, String defaultValue) {
        if (mappingKey == null || !mappingKey.equals(activeInterfaceSearchMappingKey)) return;
        activeInterfaceSearchDefault = defaultValue;
        pendingInterfaceSearch = InterfaceSearchMappings.resolve(mappingKey, defaultValue);
        pendingTargetSelectionTicks = 0;
        pendingAutoPlace = false;
        interfaceTerminal.clearHighlight();
        layoutButtons();
    }

    private void rememberRecipeAlternatives(List<IAEStack<?>>[] alternatives) {
        for (int slot = 0; slot < recipeInputAlternatives.length; slot++) {
            List<IAEStack<?>> copied = new ArrayList<>();
            if (alternatives != null && slot < alternatives.length && alternatives[slot] != null) {
                for (IAEStack<?> alternative : alternatives[slot]) {
                    if (alternative != null) copied.add(alternative.copy());
                }
            }
            recipeInputAlternatives[slot] = copied;
        }
    }

    private void layoutContainerSlots() {
        int panelY = patternPanelY();
        for (Object value : inventorySlots.inventorySlots) {
            if (value instanceof AppEngSlot slot && slot.isPlayerSide()) {
                // GuiInterfaceTerminal changes height according to GUI scale. Its
                // own container starts the player inventory at x=14 and applies
                // this exact dynamic y transform; mirror that on our shared
                // pattern container so the click boxes stay on the texture.
                slot.xDisplayPosition = 14 + slot.getX();
                slot.yDisplayPosition = ySize + slot.getY() - 82;
            } else if (value instanceof SlotRestrictedInput slot
                && slot.getItemType() == SlotRestrictedInput.PlacableItemType.BLANK_PATTERN) {
                    slot.xDisplayPosition = PATTERN_PANEL_X + PATTERN_BLANK_SLOT_X;
                    slot.yDisplayPosition = panelY + PATTERN_PATTERN_ROW_Y;
                } else if (value instanceof SlotRestrictedInput slot
                    && slot.getItemType() == SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN) {
                        slot.xDisplayPosition = PATTERN_PANEL_X + PATTERN_ENCODED_SLOT_X;
                        slot.yDisplayPosition = panelY + PATTERN_PATTERN_ROW_Y;
                    } else if (value instanceof SlotRestrictedInput slot
                        && slot.getItemType() == SlotRestrictedInput.PlacableItemType.VIEW_CELL) {
                            slot.xDisplayPosition = PATTERN_PANEL_X + VIEW_CELL_SLOT_X + slot.getSlotIndex() * 18;
                            slot.yDisplayPosition = viewCellPanelY() + VIEW_CELL_SLOT_Y;
                        } else if (value instanceof SlotPatternTerm slot) {
                            slot.xDisplayPosition = patternContainer.isCraftingMode() ? PATTERN_PANEL_X + 42 : -9000;
                            slot.yDisplayPosition = panelY + PATTERN_CRAFTING_OUTPUT_Y;
                        }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (pendingPinRefresh) {
            pendingPinRefresh = false;
            patternContainer.requestPinRefresh();
        }
        if (configuredTerminalStyle != currentTerminalStyle()) {
            setWorldAndResolution(mc, width, height);
        }
        synchronizeNativePinRows();
        interfaceTerminal.refreshButtons();
        applyPendingRecipeSearch();
        updateItemGeometry();
        configureItemPanel();
        layoutButtons();
        layoutPatternSlots();
        layoutContainerSlots();
        // GuiMEMonitorable may recalculate its own scrollbar immediately before
        // drawing. Suppress that native draw; draw it at our compact panel's
        // coordinates from drawFG instead.
        getScrollBar().setVisible(false);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void synchronizeNativePinRows() {
        int craftingGroups = (Math.max(0, patternContainer.craftingPinRowsSync.get()) * ITEM_COLUMNS + 8) / 9;
        int playerGroups = (Math.max(0, patternContainer.playerPinRowsSync.get()) * ITEM_COLUMNS + 8) / 9;
        // The custom four-wide row values commonly arrive after AE2 has already
        // built its initial nine-wide virtual pin array. Updating AE2's private
        // row state through its public hook rebuilds that array on the first
        // synchronized frame; subsequent calls are no-ops.
        setPinsRows(PinsRows.fromOrdinal(craftingGroups), PinsRows.fromOrdinal(playerGroups));
    }

    private void applyPendingRecipeSearch() {
        if (pendingInterfaceSearch != null) {
            interfaceTerminal.setAutomaticSearchText(pendingInterfaceSearch);
            pendingInterfaceSearch = null;
            pendingTargetSelectionTicks = 60;
            interfaceTerminal.clearSearchFocus();
            searchField.setFocused(false);
            return;
        }
        if (pendingTargetSelectionTicks <= 0) return;

        InterfacePatternTarget target = interfaceTerminal.highlightFirstEmptyPatternSlot();
        if (target == null) {
            pendingTargetSelectionTicks--;
            if (pendingTargetSelectionTicks == 0) pendingAutoPlace = false;
            return;
        }

        pendingTargetSelectionTicks = 0;
        if (pendingAutoPlace) patternContainer.requestPlaceEncodedPattern(target);
        pendingAutoPlace = false;
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        interfaceTerminal.drawCentralBackground(offsetX, offsetY, mouseX, mouseY);
        interfaceTerminal.drawHighlightedPatternSlot(offsetX, offsetY);

        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(ITEM_PANEL);
        int left = offsetX + ITEM_PANEL_X;
        int top = offsetY + itemPanelY;
        drawTexturedModalRect(left, top, 0, 0, ITEM_PANEL_WIDTH, 18);
        for (int row = 0; row < itemRows; row++) {
            drawTexturedModalRect(left, top + 18 + row * 18, 0, 18, ITEM_PANEL_WIDTH, 18);
        }
        drawTexturedModalRect(left, top + 18 + itemRows * 18, 0, 90, ITEM_PANEL_WIDTH, 6);
        searchField.drawTextBox();

        // MEGuiTextField changes the current GL color. Without restoring white,
        // the encoder is tinted dark until another widget happens to reset it.
        GL11.glColor4f(1, 1, 1, 1);
        boolean crafting = patternContainer.isCraftingMode();
        boolean compactProcessing = !crafting && patternContainer.processingGridSizeSync.get() == 3;
        boolean inverted = patternContainer.invertedSync.get();
        ResourceLocation texture = crafting ? PATTERN_PANEL
            : compactProcessing ? PATTERN_PANEL_3X3 : inverted ? PATTERN_PANEL_4X4_INVERTED : PATTERN_PANEL_4X4;
        mc.getTextureManager()
            .bindTexture(texture);
        int right = offsetX + PATTERN_PANEL_X;
        int patternTop = offsetY + patternPanelY();
        drawTexturedModalRect(right, patternTop, 0, 0, PATTERN_PANEL_WIDTH, PATTERN_PANEL_TEXTURE_HEIGHT);
        drawViewCellPanel(offsetX, offsetY);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        currentMouseX = mouseX;
        currentMouseY = mouseY;
        if (visiblePinSlots.length > 0) {
            VirtualMEPinSlot.drawSlotsBackground(visiblePinSlots, mc, zLevel);
        }
        updateItemScrollBar();
        getScrollBar().setVisible(true);
        getScrollBar().draw(this);
        getScrollBar().setVisible(false);
        interfaceTerminal.drawCentralForeground(offsetX, offsetY, mouseX, mouseY);
        interfaceTerminal.drawCentralScrollBar();
        if (!patternContainer.isCraftingMode() && patternContainer.processingGridSizeSync.get() == 4) {
            processingScrollBar.draw(this);
        }
        fontRendererObj.drawString(
            StatCollector.translateToLocal("gtnh_qol_improvements.terminal.storage"),
            ITEM_PANEL_X + 5,
            itemPanelY - 10,
            0x404040);
        fontRendererObj.drawString(
            StatCollector.translateToLocal("gtnh_qol_improvements.terminal.encoding"),
            PATTERN_PANEL_X + 8,
            patternPanelY() - 10,
            0x404040);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == pinStateButton) {
            int craftingRows = patternContainer.craftingPinRowsSync.get();
            int playerRows = patternContainer.playerPinRowsSync.get();
            int change = Mouse.isButtonDown(1) ? -1 : 1;
            if (isCtrlKeyDown()) {
                craftingRows += change;
            } else {
                playerRows += change;
            }
            patternContainer.requestPinRows(craftingRows, playerRows);
            updateItemGeometry();
            configureItemPanel();
            layoutButtons();
            return;
        }
        if (button == craftingModeButton) {
            patternContainer.craftingModeSync.set(true);
            layoutPatternSlots();
            return;
        }
        if (button == processing4ModeButton) {
            patternContainer.requestProcessingGridSize(4);
            patternContainer.craftingModeSync.set(false);
            layoutPatternSlots();
            return;
        }
        if (button == processing3ModeButton) {
            patternContainer.requestProcessingGridSize(3);
            patternContainer.requestInverted(false);
            patternContainer.activePageSync.set(0);
            patternContainer.craftingModeSync.set(false);
            layoutPatternSlots();
            return;
        }
        if (button == encodeButton) {
            if (isAltDown() && !isShiftKeyDown() && !isCtrlKeyDown() && interfaceTerminal.highlightedTarget() != null) {
                patternContainer.encodeAction.send();
                patternContainer.requestPlaceEncodedPattern(interfaceTerminal.highlightedTarget());
            } else if (isShiftKeyDown()) {
                patternContainer.encodeAndMoveToInventoryAction.send(isCtrlKeyDown());
            } else {
                patternContainer.encodeAction.send();
            }
            return;
        }
        if (button == searchMappingButton && activeInterfaceSearchMappingKey != null) {
            mc.displayGuiScreen(
                new GuiInterfaceSearchMapping(this, activeInterfaceSearchMappingKey, activeInterfaceSearchDefault));
            return;
        }
        if (interfaceButtons.contains(button)) {
            boolean changesTerminalStyle = button instanceof GuiImgButton image
                && image.getSetting() == Settings.TERMINAL_STYLE;
            interfaceTerminal.perform(button);
            if (changesTerminalStyle) setWorldAndResolution(mc, width, height);
            return;
        }
        if (button == invertButton) {
            patternContainer.requestInverted(!patternContainer.invertedSync.get());
            layoutPatternSlots();
            return;
        }
        super.actionPerformed(button);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        // This screen contains four independent search fields. Clear them all
        // first, then let the field under the cursor regain focus later in the
        // normal click dispatch. This also makes clicks in Encoding, ME Storage
        // and otherwise empty NEI space consistently release keyboard focus.
        searchField.setFocused(false);
        interfaceTerminal.clearSearchFocus();
        interfaceTerminal.cancelScrollDrag();

        if ((button == 0 || button == 1) && isInsideStoragePanel(mouseX, mouseY)) {
            // The storage panel is deliberately outside xSize. Mark the whole
            // visible panel as GUI space so clicking its search/background never
            // turns a held item into an ordinary "drop outside" action. Actual
            // virtual slots still flow through GuiMEMonitorable below.
            suppressExtensionVanillaClick(button);
        }
        // GuiPatternTerm checks virtual slots before real container slots. In
        // this composite layout the two pattern inventory slots sit outside the
        // native central GUI, so dispatch their real slot click before the
        // virtual-slot handler gets a chance to consume it.
        if (button == 0 || button == 1) {
            for (Object value : inventorySlots.inventorySlots) {
                if (!(value instanceof SlotRestrictedInput slot)) continue;
                if (slot.getItemType() != SlotRestrictedInput.PlacableItemType.BLANK_PATTERN
                    && slot.getItemType() != SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN) continue;
                int x = guiLeft + slot.xDisplayPosition;
                int y = guiTop + slot.yDisplayPosition;
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    int action = button | (isShiftKeyDown() ? 2 : 0);
                    if (slot.getItemType() == SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN) {
                        suppressExtensionVanillaClick(button);
                        patternContainer.takeEncodedPatternAction.send(action);
                        return;
                    }
                    suppressExtensionVanillaClick(button);
                    patternContainer.takeBlankPatternAction.send(action);
                    return;
                }
            }
        }
        if (!patternContainer.isCraftingMode() && patternContainer.processingGridSizeSync.get() == 4) {
            int oldPage = processingScrollBar.getCurrentScroll();
            processingScrollBar.click(this, mouseX - guiLeft, mouseY - guiTop);
            if (oldPage != processingScrollBar.getCurrentScroll()) {
                patternContainer.activePageSync.set(processingScrollBar.getCurrentScroll());
                layoutPatternSlots();
            }
        }
        if (mouseX >= guiLeft && mouseX < guiLeft + xSize && mouseY >= guiTop && mouseY < guiTop + ySize - 98) {
            InterfacePatternTarget target = interfaceTerminal.patternSlotAt(mouseX, mouseY);
            if (target != null && (button == 0 || button == 1) && !isCtrlKeyDown()) {
                patternContainer.requestInterfacePatternClick(target, isShiftKeyDown());
                return;
            }
            interfaceTerminal.click(mouseX, mouseY, button);
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        interfaceTerminal.dragScrollBar(mouseY, button);
        super.mouseClickMove(mouseX, mouseY, button, timeSinceLastClick);
    }

    @Override
    protected boolean handleVirtualSlotClick(VirtualMESlot slot, int mouseButton) {
        IAEStack<?> monitorTarget = slot instanceof VirtualMEMonitorableSlot ? slot.getAEStack() : null;
        if (monitorTarget instanceof IAEItemStack item) monitorTarget = Platform.convertStack(item);
        if (mouseButton == 0 && !isCtrlKeyDown()
            && !isShiftKeyDown()
            && monitorTarget != null
            && monitorTarget.isFluid()
            && mc.thePlayer.inventory.getItemStack() == null) {
            patternContainer.requestFillOneFluidUnit(new StorageFluidRequest(monitorTarget));
            suppressExtensionVanillaClick(mouseButton);
            return true;
        }
        if (mouseButton == 1 && !isCtrlKeyDown()
            && !isShiftKeyDown()
            && monitorTarget != null
            && monitorTarget.isFluid()
            && mc.thePlayer.inventory.getItemStack() == null) {
            patternContainer.requestStoreOneFluidUnit(new StorageFluidRequest(monitorTarget));
            suppressExtensionVanillaClick(mouseButton);
            return true;
        }
        if (QolConfig.middleClickOrdering && mouseButton == 1000101 && slot instanceof VirtualMEMonitorableSlot) {
            if (monitorTarget != null && monitorTarget.isFluid()) {
                QolNetwork.middleClickBookmark(monitorTarget, 1);
                suppressExtensionVanillaClick(mouseButton);
                return true;
            }
        }
        boolean handled = super.handleVirtualSlotClick(slot, mouseButton);
        if (slot instanceof VirtualMEPatternSlot) {
            // AEBaseGui applies the phantom stack but deliberately returns
            // false. Our encoding panel is outside the native GuiContainer
            // rectangle, so allowing the click to continue would also send an
            // outside-slot (-999) click and throw the held stack on the ground.
            suppressExtensionVanillaClick(mouseButton);
            return true;
        }
        if (handled && slot instanceof VirtualMEMonitorableSlot) suppressExtensionVanillaClick(mouseButton);
        return handled;
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotIdx, int clickedButton, int clickType) {
        boolean extensionDuplicate = slotIdx == -999 || slot instanceof SlotRestrictedInput restricted
            && (restricted.getItemType() == SlotRestrictedInput.PlacableItemType.BLANK_PATTERN
                || restricted.getItemType() == SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN);
        if (extensionDuplicate && clickedButton == suppressExtensionButton
            && System.currentTimeMillis() <= suppressExtensionClickUntil) {
            return;
        }
        super.handleMouseClick(slot, slotIdx, clickedButton, clickType);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state == 0) interfaceTerminal.cancelScrollDrag();
        if (state == suppressExtensionButton) {
            suppressExtensionButton = -1;
            suppressExtensionClickUntil = 0;
        }
    }

    private void suppressExtensionVanillaClick(int button) {
        suppressExtensionButton = button;
        suppressExtensionClickUntil = System.currentTimeMillis() + 1000;
    }

    private boolean isInsideStoragePanel(int mouseX, int mouseY) {
        int left = guiLeft + ITEM_PANEL_X;
        int top = guiTop + itemPanelY;
        return mouseX >= left && mouseX < left + ITEM_PANEL_WIDTH && mouseY >= top && mouseY < top + itemPanelHeight;
    }

    @Override
    protected boolean mouseWheelEvent(int mouseX, int mouseY, int wheel) {
        if (isShiftKeyDown() && cycleRecipeAlternative(wheel)) return true;
        if (handleStorageTransferWheel(wheel)) return true;
        if (!patternContainer.isCraftingMode() && patternContainer.processingGridSizeSync.get() == 4
            && processingScrollBar.contains(mouseX - guiLeft, mouseY - guiTop)) {
            int oldPage = processingScrollBar.getCurrentScroll();
            processingScrollBar.wheel(wheel);
            if (oldPage != processingScrollBar.getCurrentScroll()) {
                patternContainer.activePageSync.set(processingScrollBar.getCurrentScroll());
                layoutPatternSlots();
            }
            return true;
        }
        if (isInsideStoragePanel(mouseX, mouseY)) {
            getScrollBar().wheel(wheel);
            return true;
        }
        if (mouseX >= guiLeft && mouseX < guiLeft + xSize && mouseY >= guiTop && mouseY < guiTop + ySize) {
            // Consume the entire central panel. GuiMEMonitorable's fallback
            // otherwise sends an unhandled wheel event to the ME Storage
            // scrollbar even though that panel lives on the far left.
            interfaceTerminal.wheel(mouseX, mouseY, wheel);
            return true;
        }
        return super.mouseWheelEvent(mouseX, mouseY, wheel);
    }

    private boolean handleStorageTransferWheel(int wheel) {
        if (wheel == 0 || !isCtrlKeyDown() && !isShiftKeyDown()) return false;
        VirtualMESlot slot = getVirtualMESlotUnderMouse();
        if (!(slot instanceof VirtualMEMonitorableSlot)) return false;

        if (isCtrlKeyDown()) return handleStorageBackpackWheel(slot, wheel);

        // Native AE2 behavior: Shift + wheel moves one item per step between
        // the hovered ME entry and the stack held by the mouse cursor.
        MonitorableAction direction = wheel > 0 ? MonitorableAction.ROLL_DOWN : MonitorableAction.ROLL_UP;
        if (direction == MonitorableAction.ROLL_DOWN && mc.thePlayer.inventory.getItemStack() == null) return false;
        if (direction == MonitorableAction.ROLL_UP && !(slot.getAEStack() instanceof IAEItemStack)) return false;

        ((AEBaseContainer) inventorySlots).setTargetStack(slot.getAEStack());
        for (int step = 0; step < Math.abs(wheel); step++) {
            NetworkHandler.instance.sendToServer(new PacketMonitorableAction(direction, -1));
        }
        return true;
    }

    /**
     * Ctrl is our backpack-oriented companion to AE2's cursor-oriented Shift
     * action. Scrolling out withdraws one full stack straight into the player
     * inventory; scrolling in inserts one matching inventory item into ME.
     */
    private boolean handleStorageBackpackWheel(VirtualMESlot slot, int wheel) {
        if (!(slot.getAEStack() instanceof IAEItemStack target)) return false;

        if (wheel < 0) {
            ((AEBaseContainer) inventorySlots).setTargetStack(target);
            for (int step = 0; step < Math.abs(wheel); step++) {
                NetworkHandler.instance.sendToServer(new PacketMonitorableAction(MonitorableAction.SHIFT_CLICK, -1));
            }
            return true;
        }

        ItemStack wanted = target.getItemStack();
        if (wanted == null) return false;
        for (Object candidate : inventorySlots.inventorySlots) {
            if (!(candidate instanceof AppEngSlot playerSlot) || !playerSlot.isPlayerSide()) continue;
            ItemStack stored = playerSlot.getStack();
            if (stored == null || !Platform.isSameItemPrecise(wanted, stored)) continue;
            patternContainer.requestStoreOneFromInventory(playerSlot.slotNumber);
            return true;
        }
        return false;
    }

    private boolean cycleRecipeAlternative(int wheel) {
        VirtualMESlot hovered = getVirtualMESlotUnderMouse();
        if (!(hovered instanceof VirtualMEPatternSlot) || wheel == 0 || !isCraftingInputSlot(hovered)) return false;
        int slot = hovered.getSlotIndex();
        if (slot < 0 || slot >= recipeInputAlternatives.length) return false;
        IAEStack<?> current = hovered.getAEStack();
        if (current == null || !current.isItem()) return false;
        List<IAEStack<?>> alternatives = alternativesFor(slot, current);
        if (alternatives == null) return false;

        int currentIndex = -1;
        for (int index = 0; index < alternatives.size(); index++) {
            if (alternatives.get(index)
                .isSameType(current)) {
                currentIndex = index;
                break;
            }
        }
        if (currentIndex < 0) return false;
        int direction = wheel < 0 ? 1 : -1;
        int nextIndex = (currentIndex + direction + alternatives.size()) % alternatives.size();
        IAEStack<?> replacement = alternatives.get(nextIndex)
            .copy();
        if (!replacement.isItem()) return false;
        patternContainer.requestRecipeIngredientReplacement(current, replacement);
        return true;
    }

    private List<IAEStack<?>> alternativesFor(int preferredSlot, IAEStack<?> current) {
        List<IAEStack<?>> preferred = recipeInputAlternatives[preferredSlot];
        if (containsAlternative(preferred, current)) return preferred;
        // AE2Things searches every ingredient group for the hovered item. This
        // fallback also keeps cycling valid after changing between shaped and
        // compact processing layouts, where the visible slot index can move.
        for (List<IAEStack<?>> alternatives : recipeInputAlternatives) {
            if (containsAlternative(alternatives, current)) return alternatives;
        }
        return null;
    }

    private static boolean containsAlternative(List<IAEStack<?>> alternatives, IAEStack<?> current) {
        if (alternatives == null || alternatives.size() < 2) return false;
        for (IAEStack<?> alternative : alternatives) {
            if (alternative.isSameType(current)) return true;
        }
        return false;
    }

    private boolean isCraftingInputSlot(VirtualMESlot slot) {
        if (craftingSlots == null) return false;
        for (VirtualMEPatternSlot input : craftingSlots) {
            if (input == slot) return true;
        }
        return false;
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (interfaceTerminal.handleSearchKey(character, key)) return;
        super.keyTyped(character, key);
    }

    @Override
    public boolean isOverTextField(int mouseX, int mouseY) {
        return super.isOverTextField(mouseX, mouseY) || interfaceTerminal.isOverTextField(mouseX, mouseY);
    }

    @Override
    public ItemStack getHoveredStack() {
        ItemStack interfaceStack = interfaceTerminal.getHoveredStack();
        return interfaceStack == null ? super.getHoveredStack() : interfaceStack;
    }

    @Override
    public List<String> handleItemTooltip(ItemStack stack, int mouseX, int mouseY, List<String> lines) {
        lines = super.handleItemTooltip(stack, mouseX, mouseY, lines);
        VirtualMESlot hovered = getVirtualMESlotUnderMouse();
        if (hovered instanceof VirtualMEPatternSlot && isCraftingInputSlot(hovered)) {
            IAEStack<?> current = hovered.getAEStack();
            int slot = hovered.getSlotIndex();
            if (current != null && slot >= 0
                && slot < recipeInputAlternatives.length
                && alternativesFor(slot, current) != null) {
                lines.add(
                    EnumChatFormatting.YELLOW + "SHIFT + "
                        + StatCollector.translateToLocal("gtnh_qol_improvements.terminal.mouse_wheel")
                        + EnumChatFormatting.GRAY
                        + " - "
                        + StatCollector.translateToLocal("gtnh_qol_improvements.terminal.cycle_alternatives"));
            }
        }
        return lines;
    }

    @Override
    public void setTextFieldValue(String displayName, int mouseX, int mouseY, ItemStack stack) {
        if (interfaceTerminal.isOverTextField(mouseX, mouseY)) {
            interfaceTerminal.setSearchFieldValue(displayName, mouseX, mouseY, stack);
        } else {
            super.setTextFieldValue(displayName, mouseX, mouseY, stack);
        }
    }

    @Override
    public void postUpdate(List<PacketEntry> updates, int statusFlags) {
        interfaceTerminal.postUpdate(updates, statusFlags);
    }

    @Override
    public void postUpdate(List<IAEStack<?>> updates) {
        super.postUpdate(updates);
        updateItemScrollBar();
    }

    @Override
    public boolean hideItemPanelSlot(int x, int y, int width, int height) {
        if (intersects(x, y, width, height, guiLeft + BUTTON_COLUMN_X, guiTop, BUTTON_COLUMN_WIDTH, ySize)
            || intersects(
                x,
                y,
                width,
                height,
                guiLeft + ITEM_PANEL_X,
                guiTop + itemPanelY,
                ITEM_PANEL_WIDTH,
                itemPanelHeight)
            || intersects(
                x,
                y,
                width,
                height,
                guiLeft + PATTERN_PANEL_X,
                guiTop + patternPanelY() + PATTERN_PANEL_TOP,
                PATTERN_PANEL_WIDTH,
                PATTERN_PANEL_HEIGHT))
            return true;

        for (GuiButton button : storageButtons) {
            if (intersects(x, y, width, height, button.xPosition, button.yPosition, button.width, button.height))
                return true;
        }
        return false;
    }

    private void updateItemGeometry() {
        configuredTerminalStyle = currentTerminalStyle();
        pinDisplayRows = countPinDisplayRows();
        int availableRows = Math.max(DEFAULT_ITEM_ROWS, (ySize - 36) / 18);
        itemRows = configuredTerminalStyle == TerminalStyle.TALL ? Math.max(availableRows, pinDisplayRows + 1)
            : Math.max(DEFAULT_ITEM_ROWS, pinDisplayRows + 1);
        visibleItemSlots = Math.max(ITEM_COLUMNS, (itemRows - pinDisplayRows) * ITEM_COLUMNS);
        itemPanelHeight = panelHeight(itemRows);
        itemPanelY = ySize - itemPanelHeight;
    }

    private static int panelHeight(int rows) {
        return 24 + rows * 18;
    }

    private int patternPanelY() {
        return configuredTerminalStyle == TerminalStyle.TALL ? itemPanelY : 0;
    }

    private int viewCellPanelY() {
        return patternPanelY() + PATTERN_PANEL_TEXTURE_HEIGHT + VIEW_CELL_PANEL_GAP;
    }

    private void drawViewCellPanel(int offsetX, int offsetY) {
        int left = offsetX + PATTERN_PANEL_X;
        int top = offsetY + viewCellPanelY();
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(VIEW_CELL_PANEL);
        drawTexturedModalRect(left, top, 0, 0, PATTERN_PANEL_WIDTH, VIEW_CELL_PANEL_HEIGHT);
    }

    private static TerminalStyle currentTerminalStyle() {
        return (TerminalStyle) AEConfig.instance.settings.getSetting(Settings.TERMINAL_STYLE);
    }

    private static boolean isAltDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }

    private static boolean intersects(int x, int y, int width, int height, int panelX, int panelY, int panelWidth,
        int panelHeight) {
        return width > 0 && height > 0
            && x < panelX + panelWidth
            && x + width > panelX
            && y < panelY + panelHeight
            && y + height > panelY;
    }

    private ItemRepo readItemRepo() {
        if (ITEM_REPO == null) return null;
        try {
            return (ItemRepo) ITEM_REPO.get(this);
        } catch (IllegalAccessException ignored) {
            return null;
        }
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

    private final class CraftableIngredientOverlaySlot extends VirtualMESlot {

        private final VirtualMEPatternSlot ingredientSlot;

        private CraftableIngredientOverlaySlot(VirtualMEPatternSlot ingredientSlot) {
            super(0, 0, ingredientSlot.getSlotIndex());
            this.ingredientSlot = ingredientSlot;
        }

        @Override
        public IAEStack<?> getAEStack() {
            return ingredientSlot.getAEStack();
        }

        @Override
        public boolean isHovered(int mouseX, int mouseY) {
            return false;
        }

        @Override
        public boolean drawStackAndOverlay(Minecraft minecraft, int mouseX, int mouseY) {
            IAEStack<?> ingredient = ingredientSlot.getAEStack();
            if (ingredientSlot.isHidden() || !isCraftableOnNetwork(ingredient)) return false;

            // AEStack's native overlay draws the same small encoded-pattern
            // icon used by AE2's terminal item slots. Render from a copy so
            // craftability never leaks into the synchronized recipe inventory.
            IAEStack<?> overlay = ingredient.copy();
            if (overlay.getStackSize() <= 0) overlay.setStackSize(1);
            overlay.setCraftable(true);
            overlay.drawOverlayInGui(minecraft, ingredientSlot.getX(), ingredientSlot.getY(), false, false, true, true);
            return false;
        }
    }

    /** AE2's native Encode Pattern button, rotated so its arrow follows the horizontal pattern flow. */
    private static final class RightEncodingButton extends GuiImgButton {

        private RightEncodingButton() {
            super(0, 0, Settings.ACTIONS, ActionItems.ENCODE);
        }

        @Override
        public String getMessage() {
            return super.getMessage() + "\n"
                + StatCollector.translateToLocal("gtnh_qol_improvements.terminal.encode_alt_upload");
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
            if (!visible) return;
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushClientAttrib(GL_CLIENT_ALL_ATTRIB_BITS);
            try {
                field_146123_n = mouseX >= xPosition && mouseY >= yPosition
                    && mouseX < xPosition + width
                    && mouseY < yPosition + height;
                if (enabled) {
                    ScreenColor.setGuiColor();
                } else {
                    ScreenColor.setDimmedGuiColor();
                }
                minecraft.renderEngine.bindTexture(ExtraBlockTextures.GuiTexture("guis/states.png"));
                GL11.glEnable(GL11.GL_BLEND);
                OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                GL11.glPushMatrix();
                try {
                    GL11.glTranslatef(xPosition, yPosition, 0);

                    // Keep AE2's original frame unrotated so its upper/left shadow and
                    // lower/right highlight match every other GUI button.
                    drawTexturedModalRect(0, 0, 240, 240, 16, 16);
                    if (enabled) ScreenColor.resetGuiColor();

                    // ActionItems.ENCODE is icon 8 in states.png. Rotate only that
                    // glyph; the button's bevel and hover area remain unchanged.
                    GL11.glTranslatef(8, 8, 0);
                    GL11.glRotatef(-90, 0, 0, 1);
                    GL11.glTranslatef(-8, -8, 0);
                    drawTexturedModalRect(0, 0, 128, 0, 16, 16);
                } finally {
                    GL11.glPopMatrix();
                }
                mouseDragged(minecraft, mouseX, mouseY);
            } finally {
                ScreenColor.resetGuiColor();
                GL11.glPopClientAttrib();
                GL11.glPopAttrib();
            }
        }
    }

    /**
     * The stock AE2 tab ignores {@link #enabled}, so three adjacent mode tabs
     * otherwise look selected at the same time. Keep the raised AE2 tab for
     * the active mode and use AE2's recessed image-button frame for the two
     * directly selectable alternatives.
     */
    private static final class ModeButton extends GuiTabButton {

        private static final float ICON_SCALE = 0.75F;

        private final ItemStack icon;
        private final RenderItem itemRenderer;
        private boolean selected;

        private ModeButton(ItemStack icon, String message, RenderItem itemRenderer) {
            super(0, 0, icon, message, itemRenderer);
            this.icon = icon;
            this.itemRenderer = itemRenderer;
        }

        private void setSelected(boolean selected) {
            this.selected = selected;
            enabled = !selected;
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
            if (!visible) return;
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushClientAttrib(GL_CLIENT_ALL_ATTRIB_BITS);
            try {
                field_146123_n = mouseX >= xPosition && mouseY >= yPosition
                    && mouseX < xPosition + width
                    && mouseY < yPosition + height;
                ScreenColor.setGuiColor();
                minecraft.renderEngine.bindTexture(ExtraBlockTextures.GuiTexture("guis/states.png"));
                if (selected) {
                    drawTexturedModalRect(xPosition, yPosition, 208, 0, 25, 22);
                } else {
                    drawTexturedModalRect(xPosition + 3, yPosition + 3, 240, 240, 16, 16);
                }
                ScreenColor.resetGuiColor();

                drawCrispIcon(minecraft);
                mouseDragged(minecraft, mouseX, mouseY);
            } finally {
                RenderHelper.disableStandardItemLighting();
                itemRenderer.zLevel = 0.0F;
                zLevel = 0.0F;
                ScreenColor.resetGuiColor();
                GL11.glPopClientAttrib();
                GL11.glPopAttrib();
            }
        }

        private void drawCrispIcon(Minecraft minecraft) {
            minecraft.renderEngine.bindTexture(TextureMap.locationBlocksTexture);
            int previousMinFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            int previousMagFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

            GL11.glPushMatrix();
            try {
                zLevel = 100.0F;
                itemRenderer.zLevel = 100.0F;
                GL11.glEnable(GL11.GL_LIGHTING);
                GL11.glEnable(32826); // GL_RESCALE_NORMAL (LWJGL 2 exposes it through GL12).
                RenderHelper.enableGUIStandardItemLighting();
                float centerX = xPosition + 11.0F;
                float centerY = yPosition + 11.0F;
                GL11.glTranslatef(centerX, centerY, 0.0F);
                GL11.glScalef(ICON_SCALE, ICON_SCALE, 1.0F);
                GL11.glTranslatef(-centerX, -centerY, 0.0F);
                itemRenderer.renderItemAndEffectIntoGUI(
                    minecraft.fontRenderer,
                    minecraft.renderEngine,
                    icon,
                    xPosition + 3,
                    yPosition + 3);
            } finally {
                GL11.glPopMatrix();
                minecraft.renderEngine.bindTexture(TextureMap.locationBlocksTexture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, previousMinFilter);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, previousMagFilter);
            }
        }
    }

    private static final class EmbeddedInterfaceTerminal extends GuiInterfaceTerminal {

        private static final Field INPUT_SEARCH = findField(GuiInterfaceTerminal.class, "searchFieldInputs");
        private static final Field OUTPUT_SEARCH = findField(GuiInterfaceTerminal.class, "searchFieldOutputs");
        private static final Field NAME_SEARCH = findField(GuiInterfaceTerminal.class, "searchFieldNames");
        private static final Field MASTER_LIST = findField(GuiInterfaceTerminal.class, "masterList");
        private static final Field VIEW_HEIGHT = findField(GuiInterfaceTerminal.class, "viewHeight");

        private Object highlightedEntry;
        private InterfacePatternTarget highlightedTarget;
        private boolean scrollBarDragging;

        private EmbeddedInterfaceTerminal(Container container) {
            super(container);
        }

        private void initialize(Minecraft minecraft, int width, int height) {
            setWorldAndResolution(minecraft, width, height);
        }

        private List<GuiButton> buttons() {
            return new ArrayList<>(buttonList);
        }

        private int left() {
            return guiLeft;
        }

        private int top() {
            return guiTop;
        }

        private int guiWidth() {
            return xSize;
        }

        private int guiHeight() {
            return ySize;
        }

        private void drawCentralBackground(int x, int y, int mouseX, int mouseY) {
            super.drawBG(x, y, mouseX, mouseY);
        }

        private void drawHighlightedPatternSlot(int offsetX, int offsetY) {
            if (highlightedEntry == null || highlightedTarget == null) return;
            IInventory inventory = entryInventory(highlightedEntry);
            int targetSlot = highlightedTarget.getSlot();
            if (inventory == null || targetSlot < 0 || targetSlot >= inventory.getSizeInventory()) return;

            int rowSize = intField(highlightedEntry, "rowSize", 0);
            int displayY = intField(highlightedEntry, "dispY", -9999);
            int viewHeight = VIEW_HEIGHT == null ? 0 : intField(this, VIEW_HEIGHT, 0);
            if (rowSize <= 0 || displayY <= -9000 || viewHeight <= 0) return;

            int row = targetSlot / rowSize;
            int column = targetSlot % rowSize;
            int relativeY = displayY + row * 18 + 1;
            if (relativeY < 0 || relativeY + 16 > viewHeight) return;

            int x = offsetX + 10 + 174 - rowSize * 18 + column * 18 + 1;
            int y = offsetY + 52 + relativeY;
            drawRainbowBorder(x, y);
        }

        private void drawCentralForeground(int x, int y, int mouseX, int mouseY) {
            super.drawFG(x, y, mouseX, mouseY);
        }

        private void drawCentralScrollBar() {
            getScrollBar().draw(this);
        }

        private void click(int mouseX, int mouseY, int button) {
            // This GUI is a renderer/controller embedded in the real current
            // screen. Calling GuiInterfaceTerminal.mouseClicked would fall
            // through to GuiContainer, which assumes `this` is currentScreen
            // and dereferences a null drag state. Dispatch only the interface
            // terminal's own search fields and entry list here; the outer GUI
            // handles real container slots and buttons.
            for (Field search : new Field[] { INPUT_SEARCH, OUTPUT_SEARCH, NAME_SEARCH }) {
                MEGuiTextField field = textField(search);
                if (field != null) field.mouseClicked(mouseX, mouseY, button);
            }
            GuiScrollbar scrollbar = getScrollBar();
            int relativeX = mouseX - guiLeft;
            int relativeY = mouseY - guiTop;
            scrollBarDragging = button == 0 && scrollbar.contains(relativeX, relativeY);
            scrollbar.click(this, relativeX, relativeY);
            if (MASTER_LIST == null) return;
            try {
                Object masterList = MASTER_LIST.get(this);
                Method click = masterList.getClass()
                    .getMethod("mouseClicked", int.class, int.class, int.class);
                click.setAccessible(true);
                click.invoke(masterList, mouseX - guiLeft - 10, mouseY - guiTop - 52, button);
            } catch (ReflectiveOperationException ignored) {}
        }

        private boolean wheel(int mouseX, int mouseY, int wheel) {
            GuiScrollbar scrollbar = getScrollBar();
            int relativeY = mouseY - guiTop;
            boolean insideScrollBand = mouseX > guiLeft && mouseX <= guiLeft + xSize
                && relativeY > scrollbar.getTop()
                && relativeY <= scrollbar.getTop() + scrollbar.getHeight();
            if (!insideScrollBand) return false;
            if (super.mouseWheelEvent(mouseX, mouseY, wheel)) return true;
            scrollbar.wheel(wheel);
            return true;
        }

        private void dragScrollBar(int mouseY, int button) {
            if (scrollBarDragging && button == 0) getScrollBar().clickMove(mouseY - guiTop);
        }

        private void cancelScrollDrag() {
            scrollBarDragging = false;
        }

        private void perform(GuiButton button) {
            super.actionPerformed(button);
        }

        private boolean handleSearchKey(char character, int key) {
            // Never invoke GuiInterfaceTerminal.keyTyped here: this embedded renderer is
            // not Minecraft.currentScreen, and its GuiContainer close path is invalid.
            // Only the three native interface search fields receive text input.
            MEGuiTextField[] fields = { textField(INPUT_SEARCH), textField(OUTPUT_SEARCH), textField(NAME_SEARCH) };
            for (int i = 0; i < fields.length; i++) {
                MEGuiTextField field = fields[i];
                if (field == null || !field.isFocused()) continue;
                if (key == 1 || key == Minecraft.getMinecraft().gameSettings.keyBindInventory.getKeyCode()) {
                    return false;
                }
                if (character == '\t') {
                    field.setFocused(false);
                    for (int step = 1; step < fields.length; step++) {
                        MEGuiTextField next = fields[(i + step) % fields.length];
                        if (next != null) {
                            next.setFocused(true);
                            break;
                        }
                    }
                    return true;
                }
                String oldText = field.getText();
                boolean handled = field.textboxKeyTyped(character, key);
                if (!oldText.equals(field.getText())) clearHighlight();
                return handled;
            }
            return false;
        }

        private void setSearchFieldValue(String displayName, int mouseX, int mouseY, ItemStack stack) {
            String[] previous = searchTexts();
            super.setTextFieldValue(displayName, mouseX, mouseY, stack);
            if (!java.util.Arrays.equals(previous, searchTexts())) clearHighlight();
        }

        private String[] searchTexts() {
            MEGuiTextField[] fields = { textField(INPUT_SEARCH), textField(OUTPUT_SEARCH), textField(NAME_SEARCH) };
            String[] texts = new String[fields.length];
            for (int i = 0; i < fields.length; i++) texts[i] = fields[i] == null ? "" : fields[i].getText();
            return texts;
        }

        private void clearSearchFocus() {
            for (Field search : new Field[] { INPUT_SEARCH, OUTPUT_SEARCH, NAME_SEARCH }) {
                MEGuiTextField field = textField(search);
                if (field != null) field.setFocused(false);
            }
        }

        private void focusNameSearch() {
            clearSearchFocus();
            MEGuiTextField field = textField(NAME_SEARCH);
            if (field != null) field.setFocused(true);
        }

        private void setAutomaticSearchText(String text) {
            for (Field search : new Field[] { INPUT_SEARCH, OUTPUT_SEARCH }) {
                MEGuiTextField field = textField(search);
                if (field != null && !field.getText()
                    .isEmpty()) {
                    field.setText("");
                }
            }
            MEGuiTextField field = textField(NAME_SEARCH);
            String newText = text == null ? "" : text;
            if (field != null) {
                if (!field.getText()
                    .equals(newText)) clearHighlight();
                field.setText(newText);
            }
            getScrollBar().setCurrentScroll(0);
        }

        private InterfacePatternTarget highlightFirstEmptyPatternSlot() {
            clearHighlight();
            Object masterList = objectField(this, MASTER_LIST);
            if (masterList == null) return null;
            try {
                Method visibleSections = masterList.getClass()
                    .getDeclaredMethod("getVisibleSections");
                visibleSections.setAccessible(true);
                for (Object section : (List<?>) visibleSections.invoke(masterList)) {
                    Method visibleEntries = section.getClass()
                        .getDeclaredMethod("getVisible");
                    visibleEntries.setAccessible(true);
                    Iterator<?> entries = (Iterator<?>) visibleEntries.invoke(section);
                    while (entries.hasNext()) {
                        Object entry = entries.next();
                        IInventory inventory = entryInventory(entry);
                        int numSlots = intField(
                            entry,
                            "numSlots",
                            inventory == null ? 0 : inventory.getSizeInventory());
                        if (inventory == null) continue;
                        for (int slot = 0; slot < Math.min(numSlots, inventory.getSizeInventory()); slot++) {
                            if (inventory.getStackInSlot(slot) != null) continue;
                            highlightedEntry = entry;
                            highlightedTarget = new InterfacePatternTarget(longField(entry, "id", -1), slot);
                            return highlightedTarget.getEntryId() < 0 ? null : highlightedTarget;
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {}
            return null;
        }

        private InterfacePatternTarget highlightedTarget() {
            return highlightedTarget;
        }

        private InterfacePatternTarget patternSlotAt(int mouseX, int mouseY) {
            Object masterList = objectField(this, MASTER_LIST);
            if (masterList == null) return null;
            InterfacePatternTarget hovered = hoveredPatternTarget(masterList);
            if (hovered != null) return hovered;
            int relativeX = mouseX - guiLeft - 10;
            int relativeY = mouseY - guiTop - 52;
            int viewHeight = VIEW_HEIGHT == null ? 0 : intField(this, VIEW_HEIGHT, 0);
            if (relativeX < 0 || relativeX >= 174 || relativeY < 0 || relativeY >= viewHeight) return null;

            try {
                Method visibleSections = masterList.getClass()
                    .getDeclaredMethod("getVisibleSections");
                visibleSections.setAccessible(true);
                for (Object section : (List<?>) visibleSections.invoke(masterList)) {
                    Method visibleEntries = section.getClass()
                        .getDeclaredMethod("getVisible");
                    visibleEntries.setAccessible(true);
                    Iterator<?> entries = (Iterator<?>) visibleEntries.invoke(section);
                    while (entries.hasNext()) {
                        Object entry = entries.next();
                        int rowSize = intField(entry, "rowSize", 0);
                        int numSlots = intField(entry, "numSlots", 0);
                        int displayY = intField(entry, "dispY", -9999);
                        if (rowSize <= 0 || numSlots <= 0 || displayY <= -9000) continue;

                        int offsetX = relativeX - (174 - rowSize * 18) - 1;
                        int offsetY = relativeY - displayY - 1;
                        if (offsetX < 0 || offsetX >= rowSize * 18 || offsetY < 0) continue;
                        int slot = offsetY / 18 * rowSize + offsetX / 18;
                        if (slot < 0 || slot >= numSlots) continue;
                        long id = longField(entry, "id", -1);
                        return id < 0 ? null : new InterfacePatternTarget(id, slot);
                    }
                }
            } catch (ReflectiveOperationException ignored) {}
            return null;
        }

        /** Uses AE2's own per-frame hover result, so wrapped section titles and scrolling cannot skew the hit box. */
        private InterfacePatternTarget hoveredPatternTarget(Object masterList) {
            Object entry = objectField(masterList, findField(masterList.getClass(), "hoveredEntry"));
            if (entry == null) return null;
            int slot = intField(entry, "hoveredSlotIdx", -1);
            int numSlots = intField(entry, "numSlots", 0);
            if (slot < 0 || slot >= numSlots) return null;
            long id = longField(entry, "id", -1);
            return id < 0 ? null : new InterfacePatternTarget(id, slot);
        }

        private void clearHighlight() {
            highlightedEntry = null;
            highlightedTarget = null;
        }

        private static IInventory entryInventory(Object entry) {
            Field field = entry == null ? null : findField(entry.getClass(), "inv");
            Object value = objectField(entry, field);
            return value instanceof IInventory inventory ? inventory : null;
        }

        private static Object objectField(Object owner, Field field) {
            if (owner == null || field == null) return null;
            try {
                return field.get(owner);
            } catch (IllegalAccessException ignored) {
                return null;
            }
        }

        private static int intField(Object owner, String name, int fallback) {
            return intField(owner, owner == null ? null : findField(owner.getClass(), name), fallback);
        }

        private static int intField(Object owner, Field field, int fallback) {
            if (owner == null || field == null) return fallback;
            try {
                return field.getInt(owner);
            } catch (IllegalAccessException ignored) {
                return fallback;
            }
        }

        private static long longField(Object owner, String name, long fallback) {
            Field field = owner == null ? null : findField(owner.getClass(), name);
            if (field == null) return fallback;
            try {
                return field.getLong(owner);
            } catch (IllegalAccessException ignored) {
                return fallback;
            }
        }

        /** AE2Things' animated HSB border for the pending destination slot. */
        private static void drawRainbowBorder(int x, int y) {
            float hue = (System.currentTimeMillis() % 2000L) / 2000.0F;
            int color = 0x80000000 | Color.HSBtoRGB(hue, 1.0F, 1.0F) & 0x00FFFFFF;
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(0, 0, 250);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                drawRect(x - 1, y - 1, x + 17, y, color);
                drawRect(x - 1, y + 16, x + 17, y + 17, color);
                drawRect(x - 1, y, x, y + 16, color);
                drawRect(x + 16, y, x + 17, y + 16, color);
            } finally {
                GL11.glPopMatrix();
                GL11.glPopAttrib();
                GL11.glColor4f(1, 1, 1, 1);
            }
        }

        private MEGuiTextField textField(Field field) {
            if (field == null) return null;
            try {
                return (MEGuiTextField) field.get(this);
            } catch (IllegalAccessException ignored) {
                return null;
            }
        }

        private void refreshButtons() {
            setActionButton(
                "guiButtonAssemblersOnly",
                bool("onlyMolecularAssemblers") ? ActionItems.MOLECULAR_ASSEMBLEERS_ON
                    : ActionItems.MOLECULAR_ASSEMBLEERS_OFF);
            setActionButton(
                "guiButtonHideFull",
                AEConfig.instance.showOnlyInterfacesWithFreeSlotsInInterfaceTerminal
                    ? ActionItems.TOGGLE_SHOW_FULL_INTERFACES_OFF
                    : ActionItems.TOGGLE_SHOW_FULL_INTERFACES_ON);
            setActionButton(
                "guiButtonBrokenRecipes",
                bool("onlyBrokenRecipes") ? ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERN_OFF
                    : ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERN_ON);
            setActionButton(
                "guiButtonUseSubstitute",
                bool("onlySubstitute") ? ActionItems.TOGGLE_SHOW_ONLY_SUBSTITUTE_OFF
                    : ActionItems.TOGGLE_SHOW_ONLY_SUBSTITUTE_ON);
            setActionButton(
                "guiButtonShowHidden",
                bool("showHidden") ? ActionItems.TOGGLE_SHOW_HIDDEN_INTERFACES_ON
                    : ActionItems.TOGGLE_SHOW_HIDDEN_INTERFACES_OFF);
            setActionButton(
                "guiButtonSectionOrder",
                (StringOrder) AEConfig.instance.settings.getSetting(Settings.INTERFACE_TERMINAL_SECTION_ORDER));
            setActionButton(
                "terminalStyleBox",
                (TerminalStyle) AEConfig.instance.settings.getSetting(Settings.TERMINAL_STYLE));
        }

        private boolean bool(String fieldName) {
            Field field = findField(GuiInterfaceTerminal.class, fieldName);
            if (field == null) return false;
            try {
                return field.getBoolean(this);
            } catch (IllegalAccessException ignored) {
                return false;
            }
        }

        private void setActionButton(String fieldName, Enum<?> value) {
            Field field = findField(GuiInterfaceTerminal.class, fieldName);
            if (field == null) return;
            try {
                ((GuiImgButton) field.get(this)).set(value);
            } catch (IllegalAccessException ignored) {}
        }
    }
}

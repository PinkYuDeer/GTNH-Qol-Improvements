package dev.gtnh.qol.client.nei;

import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.glodblock.github.common.item.ItemFluidDrop;
import com.glodblock.github.common.item.ItemFluidPacket;
import com.glodblock.github.util.Util;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.util.item.AEItemStack;
import codechicken.nei.BookmarkPanel;
import codechicken.nei.ItemPanel;
import codechicken.nei.ItemsGrid.ItemsGridSlot;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.bookmark.BookmarksGridSlot;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.util.NEIMouseUtils;
import dev.gtnh.qol.config.QolConfig;
import dev.gtnh.qol.network.QolNetwork;

/** Makes NEI bookmarks, item-panel entries, and history entries orderable through an open AE terminal. */
final class BookmarkCraftHandler implements IContainerInputHandler, IContainerTooltipHandler {

    static void registerFirst(BookmarkCraftHandler handler) {
        // NEI's bookmark widget consumes its own clicks. Install this handler at
        // the front so middle-click is converted to an AE order first.
        GuiContainerManager.inputHandlers.remove(handler);
        GuiContainerManager.inputHandlers.addFirst(handler);
        GuiContainerManager.tooltipHandlers.remove(handler);
        // Run tooltip enrichment last so NEI's own bookmark hints cannot
        // overwrite the middle-click order entry with the same key.
        GuiContainerManager.tooltipHandlers.addLast(handler);
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mouseX, int mouseY, int button) {
        if (!QolConfig.middleClickOrdering || button != NEIMouseUtils.MOUSE_BTN_MMB
            || !(gui instanceof GuiMEMonitorable)) {
            return false;
        }
        OrderTarget target = orderTargetAt(mouseX, mouseY);
        if (target == null) {
            return false;
        }
        QolNetwork.middleClickBookmark(target.stack, target.amount);
        return true;
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mouseX, int mouseY, Map<String, String> hotkeys) {
        if (QolConfig.middleClickOrdering && gui instanceof GuiMEMonitorable && orderTargetAt(mouseX, mouseY) != null) {
            hotkeys.put(
                NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_MMB),
                StatCollector.translateToLocal("gtnh_qol_improvements.nei.middle_click_order"));
        }
        return hotkeys;
    }

    private static BookmarksGridSlot bookmarkSlotAt(int mouseX, int mouseY) {
        BookmarkPanel panel = LayoutManager.bookmarkPanel;
        if (panel == null) {
            return null;
        }
        return panel.getSlotMouseOver(mouseX, mouseY);
    }

    private static OrderTarget orderTargetAt(int mouseX, int mouseY) {
        BookmarksGridSlot bookmark = bookmarkSlotAt(mouseX, mouseY);
        if (bookmark != null) {
            return target(
                bookmark.getItemStack(),
                Math.max(
                    1,
                    bookmark.getBookmarkItem()
                        .getAmount()));
        }

        ItemPanel panel = LayoutManager.itemPanel;
        if (panel == null) return null;
        ItemsGridSlot slot = panel.getSlotMouseOver(mouseX, mouseY);
        if (slot == null && NEIClientConfig.showHistoryPanelWidget() && panel.historyPanel != null) {
            slot = panel.historyPanel.getSlotMouseOver(mouseX, mouseY);
        }
        return slot == null ? null : target(slot.getItemStack(), 1);
    }

    private static OrderTarget target(ItemStack source, long amount) {
        if (source == null || source.getItem() == null) return null;
        ItemStack stack = source.copy();
        stack.stackSize = 1;
        IAEStack<?> target = isFluidDisplay(stack) ? Util.getAEFluidFromItem(stack) : null;
        if (target == null) target = AEItemStack.create(stack);
        if (target == null) return null;
        target.setStackSize(1);
        return new OrderTarget(target, Math.max(1, amount));
    }

    private static boolean isFluidDisplay(ItemStack stack) {
        return stack.getItem() instanceof gregtech.common.items.ItemFluidDisplay
            || stack.getItem() instanceof ItemFluidDrop
            || stack.getItem() instanceof ItemFluidPacket;
    }

    private static final class OrderTarget {

        private final IAEStack<?> stack;
        private final long amount;

        private OrderTarget(IAEStack<?> stack, long amount) {
            this.stack = stack;
            this.amount = amount;
        }
    }

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyCode) {}

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return false;
    }

    @Override
    public void onMouseClicked(GuiContainer gui, int mouseX, int mouseY, int button) {}

    @Override
    public void onMouseUp(GuiContainer gui, int mouseX, int mouseY, int button) {}

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mouseX, int mouseY, int scroll) {
        return false;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mouseX, int mouseY, int scroll) {}

    @Override
    public void onMouseDragged(GuiContainer gui, int mouseX, int mouseY, int button, long heldTime) {}
}

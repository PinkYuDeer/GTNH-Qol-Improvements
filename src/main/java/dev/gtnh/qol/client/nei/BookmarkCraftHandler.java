package dev.gtnh.qol.client.nei;

import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import appeng.client.gui.implementations.GuiMEMonitorable;
import codechicken.nei.BookmarkPanel;
import codechicken.nei.LayoutManager;
import codechicken.nei.bookmark.BookmarksGridSlot;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.util.NEIMouseUtils;
import dev.gtnh.qol.config.QolConfig;
import dev.gtnh.qol.network.QolNetwork;

/** Makes NEI bookmarks behave like craftable entries in the quick terminal. */
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
        BookmarksGridSlot slot = bookmarkSlotAt(mouseX, mouseY);
        if (slot == null) {
            return false;
        }
        ItemStack bookmark = slot.getItemStack();
        if (bookmark == null) {
            return false;
        }
        long amount = Math.max(
            1,
            slot.getBookmarkItem()
                .getAmount());
        bookmark = bookmark.copy();
        bookmark.stackSize = 1;
        QolNetwork.middleClickBookmark(bookmark, amount);
        return true;
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mouseX, int mouseY, Map<String, String> hotkeys) {
        if (QolConfig.middleClickOrdering && gui instanceof GuiMEMonitorable
            && bookmarkSlotAt(mouseX, mouseY) != null) {
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

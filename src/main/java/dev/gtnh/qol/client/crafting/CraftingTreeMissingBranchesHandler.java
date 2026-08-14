package dev.gtnh.qol.client.crafting;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;

import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.client.gui.widgets.GuiCraftingTree;
import appeng.client.gui.widgets.GuiSimpleImgButton;
import appeng.core.AELog;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.resolvers.IgnoreMissingItemResolver;
import appeng.crafting.v2.resolvers.SimulateMissingItemResolver;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.gtnh.qol.config.QolConfig;

/** Adds a client-only missing-branch filter to AE2's native crafting tree. */
public final class CraftingTreeMissingBranchesHandler {

    private static final int ICON_FILTER_OFF = 84;
    private static final int ICON_FILTER_ON = 85;

    private static final Field DISPLAY_MODE = findField(GuiCraftConfirm.class, "displayMode");
    private static final Field CRAFTING_TREE = findField(GuiCraftConfirm.class, "craftingTree");
    private static final Field TREE_NODES = findField(GuiCraftingTree.class, "treeNodes");
    private static final Field TREE_WIDTH = findField(GuiCraftingTree.class, "treeWidth");
    private static final Field TREE_HEIGHT = findField(GuiCraftingTree.class, "treeHeight");

    private static final Class<?> NODE_CLASS = findClass("appeng.client.gui.widgets.GuiCraftingTree$Node");
    private static final Class<?> TASK_NODE_CLASS = findClass("appeng.client.gui.widgets.GuiCraftingTree$TaskNode");
    private static final Field NODE_VISIBLE = findField(NODE_CLASS, "visible");
    private static final Field NODE_X = findField(NODE_CLASS, "x");
    private static final Field NODE_Y = findField(NODE_CLASS, "y");
    private static final Field NODE_CHILDREN = findField(NODE_CLASS, "childNodes");
    private static final Field NODE_COLLAPSED = findField(NODE_CLASS, "childrenCollapsed");
    private static final Field TASK_RESOLVER = findField(TASK_NODE_CLASS, "resolver");

    private final Map<GuiCraftConfirm, ScreenState> states = new WeakHashMap<>();

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiCraftConfirm gui)) return;
        ScreenState state = states.computeIfAbsent(gui, ignored -> new ScreenState());
        state.button = new GuiSimpleImgButton(
            gui.getGuiLeft() + gui.getXSize() + 2,
            gui.getGuiTop() + 8,
            ICON_FILTER_OFF,
            tooltip(false));
        state.button.setVisibility(QolConfig.craftingTreeMissingBranches && isTreeMode(gui));
        updateButton(state);
        state.dirty = true;
        event.buttonList.add(state.button);
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.gui instanceof GuiCraftConfirm gui)) return;
        ScreenState state = states.get(gui);
        if (state == null || event.button != state.button) return;

        state.missingOnly = !state.missingOnly;
        state.dirty = true;
        updateButton(state);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.button < 0 || !(Minecraft.getMinecraft().currentScreen instanceof GuiCraftConfirm gui)) return;
        ScreenState state = states.get(gui);
        if (state != null && state.missingOnly) state.dirty = true;
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiCraftConfirm gui)) return;
        ScreenState state = states.computeIfAbsent(gui, ignored -> new ScreenState());
        boolean treeMode = isTreeMode(gui);
        boolean enabled = QolConfig.craftingTreeMissingBranches;

        if (state.button != null) {
            state.button.xPosition = gui.getGuiLeft() + gui.getXSize() + 2;
            state.button.yPosition = gui.getGuiTop() + 8;
            state.button.setVisibility(enabled && treeMode);
            updateButton(state);
        }

        GuiCraftingTree tree = craftingTree(gui);
        Object root = tree == null ? null : rootNode(tree);
        boolean shouldFilter = enabled && treeMode && state.missingOnly;
        if (root != state.lastRoot || shouldFilter != state.filterApplied) state.dirty = true;
        if (!state.dirty || tree == null || root == null) return;

        try {
            applyFilter(tree, root, shouldFilter);
            state.lastRoot = root;
            state.filterApplied = shouldFilter;
            state.dirty = false;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            state.missingOnly = false;
            state.filterApplied = false;
            state.dirty = false;
            updateButton(state);
            AELog.warn(failure, "Failed to filter the AE2 crafting tree to missing branches");
        }
    }

    private static void applyFilter(GuiCraftingTree tree, Object root, boolean missingOnly)
        throws ReflectiveOperationException {
        Set<Object> missingPath = Collections.newSetFromMap(new IdentityHashMap<>());
        if (missingOnly) containsMissing(root, missingPath);
        updateVisibility(root, true, true, missingOnly, missingPath);
        LayoutBounds bounds = layout(root, 0, 0);
        TREE_WIDTH.setInt(tree, bounds.maxX + 16);
        TREE_HEIGHT.setInt(tree, bounds.maxY + 16);
    }

    private static boolean containsMissing(Object node, Set<Object> missingPath) throws IllegalAccessException {
        boolean contains = isMissingTask(node);
        for (Object child : children(node)) contains |= containsMissing(child, missingPath);
        if (contains) missingPath.add(node);
        return contains;
    }

    private static boolean isMissingTask(Object node) throws IllegalAccessException {
        if (TASK_NODE_CLASS == null || !TASK_NODE_CLASS.isInstance(node)) return false;
        Object value = TASK_RESOLVER.get(node);
        if (!(value instanceof CraftingRequest.UsedResolverEntry resolver)) return false;
        return resolver.task instanceof SimulateMissingItemResolver.ConjureItemTask
            || resolver.task instanceof IgnoreMissingItemResolver.IgnoreMissingItemTask;
    }

    private static void updateVisibility(Object node, boolean parentVisible, boolean root, boolean missingOnly,
        Set<Object> missingPath) throws IllegalAccessException {
        boolean visible = parentVisible && (root || !missingOnly || missingPath.contains(node));
        NODE_VISIBLE.setBoolean(node, visible);
        boolean showChildren = visible && !NODE_COLLAPSED.getBoolean(node);
        for (Object child : children(node)) {
            updateVisibility(child, showChildren, false, missingOnly, missingPath);
        }
    }

    private static LayoutBounds layout(Object node, int x, int y) throws IllegalAccessException {
        NODE_X.setInt(node, x);
        NODE_Y.setInt(node, y);
        int maxX = x;
        int maxY = y;
        if (NODE_COLLAPSED.getBoolean(node)) return new LayoutBounds(maxX, maxY);

        int childX = x;
        // AE2 leaves two additional pixels between a resolver task and its
        // resulting request. Preserve that rhythm after hidden siblings have
        // been removed, or filtered trees slowly drift upward by depth.
        int childY = y + (TASK_NODE_CLASS != null && TASK_NODE_CLASS.isInstance(node) ? 28 : 26);
        for (Object child : children(node)) {
            if (!NODE_VISIBLE.getBoolean(child)) continue;
            LayoutBounds childBounds = layout(child, childX, childY);
            maxX = Math.max(maxX, childBounds.maxX);
            maxY = Math.max(maxY, childBounds.maxY);
            childX = childBounds.maxX + 24;
        }
        return new LayoutBounds(maxX, maxY);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> children(Object node) throws IllegalAccessException {
        Object value = NODE_CHILDREN.get(node);
        return value instanceof List<?> ? (List<Object>) value : Collections.emptyList();
    }

    private static Object rootNode(GuiCraftingTree tree) {
        if (TREE_NODES == null) return null;
        try {
            Object value = TREE_NODES.get(tree);
            if (!(value instanceof TreeMap<?, ?>nodes) || nodes.isEmpty()) return null;
            Object row = nodes.firstEntry()
                .getValue();
            return row instanceof ArrayList<?>list && !list.isEmpty() ? list.get(0) : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static GuiCraftingTree craftingTree(GuiCraftConfirm gui) {
        if (CRAFTING_TREE == null) return null;
        try {
            return (GuiCraftingTree) CRAFTING_TREE.get(gui);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static boolean isTreeMode(GuiCraftConfirm gui) {
        if (DISPLAY_MODE == null) return false;
        try {
            Object mode = DISPLAY_MODE.get(gui);
            return mode != null && "TREE".equals(mode.toString());
        } catch (IllegalAccessException ignored) {
            return false;
        }
    }

    private static void updateButton(ScreenState state) {
        if (state.button == null) return;
        state.button.setIconIndex(state.missingOnly ? ICON_FILTER_ON : ICON_FILTER_OFF);
        state.button.setTooltip(tooltip(state.missingOnly));
    }

    private static String tooltip(boolean enabled) {
        return StatCollector.translateToLocal("gtnh_qol_improvements.crafting_tree.missing_only") + "\n"
            + StatCollector.translateToLocal(
                enabled ? "gtnh_qol_improvements.crafting_tree.missing_only.enabled"
                    : "gtnh_qol_improvements.crafting_tree.missing_only.disabled");
    }

    private static Field findField(Class<?> owner, String name) {
        if (owner == null) return null;
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static final class ScreenState {

        private GuiSimpleImgButton button;
        private boolean missingOnly;
        private boolean filterApplied;
        private boolean dirty = true;
        private Object lastRoot;
    }

    private static final class LayoutBounds {

        private final int maxX;
        private final int maxY;

        private LayoutBounds(int maxX, int maxY) {
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }
}

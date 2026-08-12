package com.wachipayoxx.wbackpacks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wachipayoxx.wbackpacks.backpack.BackpackAccess;
import com.wachipayoxx.wbackpacks.network.MenuSlotQuickMovePayload;
import com.wachipayoxx.wbackpacks.network.RequestOpenBackpackPayload;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class BackpackWindowManager {
    private static final BackpackWindowManager INSTANCE = new BackpackWindowManager();
    private static final int MAX_OPEN_WINDOWS = 8;

    private final Map<String, BackpackWindow> windows = new HashMap<>();
    private final Set<String> restoreRequests = new HashSet<>();
    private final BackpackWindowStateStore state = new BackpackWindowStateStore();

    private BackpackWindow dragging;
    private ResizeSession resizing;
    private String activeBackpackId;
    private int dragOffsetX;
    private int dragOffsetY;
    private int nextZ = 1;

    private long resizeEwCursor;
    private long resizeNsCursor;
    private long resizeNwseCursor;
    private long resizeNeswCursor;
    private long activeCursor;

    public static BackpackWindowManager get() {
        return INSTANCE;
    }

    public void open(String id, int capacity) {
        BackpackWindow existing = windows.get(id);
        if (existing != null) {
            existing.setCapacity(capacity);
            existing.setOpen(true);
            activate(existing);
            restoreRequests.remove(id);
            state.save(existing);
            return;
        }
        if (openWindows().size() >= MAX_OPEN_WINDOWS) {
            restoreRequests.remove(id);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int cascade = openWindows().size() * 12;
        int fallbackX = 8 + cascade;
        int fallbackY = Math.max(8, (minecraft.getWindow().getGuiScaledHeight() - 90) - cascade);
        int defaultColumns = Math.min(9, Math.max(1, capacity));
        int defaultRows = Math.min(6, (Math.max(1, capacity) + defaultColumns - 1) / defaultColumns);

        int restoredVisibleColumns = state.columns(id, defaultColumns);
        int restoredLayoutColumns = state.layoutColumns(id, restoredVisibleColumns);
        int restoredVisibleRows = state.rows(id, defaultRows);
        BackpackWindow window = new BackpackWindow(
                id,
                capacity,
                state.x(id, fallbackX),
                state.y(id, fallbackY),
                Math.max(state.z(id, nextZ), nextZ++),
                state.scrollRow(id, restoredLayoutColumns),
                state.scrollColumn(id),
                restoredLayoutColumns,
                restoredVisibleColumns,
                restoredVisibleRows,
                state.minimized(id),
                state.containerMode(id));
        windows.put(id, window);
        activate(window);
        restoreRequests.remove(id);
        state.save(window);
    }

    public void render(AbstractContainerScreen<?> screen, GuiGraphics graphics, int mouseX, int mouseY) {
        requestPersistedWindows(screen);

        graphics.flush();
        RenderSystem.disableDepthTest();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 1000.0F);

        boolean containerAvailable = isContainerScreen(screen);
        List<BackpackWindow> ordered = openWindows();
        ordered.sort(Comparator.comparingInt(BackpackWindow::zIndex));
        for (BackpackWindow window : ordered) {
            window.clampToScreen(screen.width, screen.height);
        }

        for (int index = 0; index < ordered.size(); index++) {
            BackpackWindow window = ordered.get(index);
            for (RenderRect region : visibleRegions(ordered, index)) {
                graphics.enableScissor(region.left(), region.top(), region.right(), region.bottom());
                window.render(graphics, mouseX, mouseY, isActive(window), containerAvailable);
                graphics.flush();
                graphics.disableScissor();
            }
        }

        BackpackWindow hovered = topAt(mouseX, mouseY);
        if (hovered != null) {
            int slot = hovered.slotAt(mouseX, mouseY);
            ItemStack stack = hovered.stackInSlot(slot);
            if (!stack.isEmpty() && screen.getMenu().getCarried().isEmpty()) {
                graphics.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
            }
        }

        if (!screen.getMenu().getCarried().isEmpty() && hovered != null && !hovered.isMinimized()) {
            ItemStack carried = screen.getMenu().getCarried();
            graphics.renderItem(carried, mouseX - 8, mouseY - 8);
            graphics.renderItemDecorations(Minecraft.getInstance().font, carried, mouseX - 8, mouseY - 8);
        }

        graphics.pose().popPose();
        graphics.flush();
        RenderSystem.enableDepthTest();
        updateCursor(mouseX, mouseY);
    }

    public boolean mousePressed(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button, boolean shiftDown) {
        boolean containerAvailable = isContainerScreen(screen);

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            ResizeTarget resizeTarget = topResizeAt(mouseX, mouseY);
            if (resizeTarget != null) {
                BackpackWindow window = resizeTarget.window();
                activate(window);
                resizing = new ResizeSession(
                        window,
                        resizeTarget.edge(),
                        mouseX,
                        mouseY,
                        window.x(),
                        window.y(),
                        window.layoutColumns(),
                        window.visibleColumns(),
                        window.visibleRows());
                return true;
            }
        }

        BackpackWindow window = topAt(mouseX, mouseY);
        if (window == null) {
            return false;
        }
        activate(window);

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && window.closeContains(mouseX, mouseY)) {
            window.setOpen(false);
            state.save(window);
            if (isActive(window)) {
                selectTopActive();
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && window.interactionModeContains(mouseX, mouseY, containerAvailable)) {
            window.setContainerMode(!window.isContainerMode());
            state.save(window);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && window.minimizeContains(mouseX, mouseY)) {
            window.setMinimized(!window.isMinimized());
            state.save(window);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && window.titleContains(mouseX, mouseY)) {
            dragging = window;
            dragOffsetX = (int) mouseX - window.x();
            dragOffsetY = (int) mouseY - window.y();
            return true;
        }

        int slot = window.slotAt(mouseX, mouseY);
        if (slot >= 0 && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            window.clickSlot(slot, button, shiftDown, containerAvailable);
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return topAt(mouseX, mouseY) != null || topResizeAt(mouseX, mouseY) != null;
        }
        if (resizing != null) {
            resizing.window().resizeFrom(
                    resizing.edge(),
                    resizing.originalX(),
                    resizing.originalY(),
                    resizing.originalLayoutColumns(),
                    resizing.originalVisibleColumns(),
                    resizing.originalVisibleRows(),
                    mouseX - resizing.startMouseX(),
                    mouseY - resizing.startMouseY(),
                    screenWidth,
                    screenHeight);
            return true;
        }
        if (dragging == null) {
            return topAt(mouseX, mouseY) != null;
        }
        dragging.moveTo((int) mouseX - dragOffsetX, (int) mouseY - dragOffsetY);
        dragging.clampToScreen(screenWidth, screenHeight);
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (resizing != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            BackpackWindow finished = resizing.window();
            resizing = null;
            state.save(finished);
            return true;
        }
        if (dragging != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            BackpackWindow finished = dragging;
            dragging = null;
            state.save(finished);
            return true;
        }
        return topAt(mouseX, mouseY) != null || topResizeAt(mouseX, mouseY) != null;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta, boolean horizontal) {
        BackpackWindow window = topAt(mouseX, mouseY);
        if (window == null) {
            return false;
        }
        activate(window);
        window.scroll(delta, horizontal);
        state.save(window);
        return true;
    }

    public boolean tryQuickMoveFromMenu(AbstractContainerScreen<?> screen, Slot slot, int menuSlot) {
        Minecraft minecraft = Minecraft.getInstance();
        BackpackWindow active = activeWindow();
        if (minecraft.player == null || active == null || slot == null || !slot.hasItem()) {
            return false;
        }

        if (active.backpackStack().isEmpty()) {
            return false;
        }

        boolean playerSlot = slot.container == minecraft.player.getInventory();
        if (screen instanceof InventoryScreen) {
            if (!playerSlot || BackpackAccess.isBackpack(slot.getItem())) {
                return false;
            }
            activate(active);
            PacketDistributor.sendToServer(new MenuSlotQuickMovePayload(active.id(), menuSlot));
            return true;
        }

        if (!isContainerScreen(screen) || playerSlot || !active.isContainerMode()) {
            return false;
        }

        activate(active);
        PacketDistributor.sendToServer(new MenuSlotQuickMovePayload(active.id(), menuSlot));
        return true;
    }

    public boolean blocksPoint(double mouseX, double mouseY) {
        return topAt(mouseX, mouseY) != null || topResizeAt(mouseX, mouseY) != null;
    }

    public Collection<Rect2i> exclusionAreas() {
        List<Rect2i> areas = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof AbstractContainerScreen<?>)) {
            return areas;
        }
        for (BackpackWindow window : openWindows()) {
            areas.add(new Rect2i(window.x(), window.y(), window.width(), window.height()));
        }
        return areas;
    }

    public void resetCursor() {
        setCursor(0L);
    }

    private void activate(BackpackWindow window) {
        activeBackpackId = window.id();
        window.setZIndex(nextZ++);
        state.save(window);
    }

    private boolean isActive(BackpackWindow window) {
        return window != null && window.id().equals(activeBackpackId);
    }

    private BackpackWindow activeWindow() {
        BackpackWindow active = activeBackpackId == null ? null : windows.get(activeBackpackId);
        if (active != null && active.isOpen()) {
            return active;
        }
        selectTopActive();
        return activeBackpackId == null ? null : windows.get(activeBackpackId);
    }

    private void selectTopActive() {
        BackpackWindow top = openWindows().stream()
                .max(Comparator.comparingInt(BackpackWindow::zIndex))
                .orElse(null);
        activeBackpackId = top == null ? null : top.id();
    }

    private BackpackWindow topAt(double x, double y) {
        return openWindows().stream()
                .filter(window -> window.contains(x, y))
                .max(Comparator.comparingInt(BackpackWindow::zIndex))
                .orElse(null);
    }

    private ResizeTarget topResizeAt(double x, double y) {
        List<BackpackWindow> ordered = openWindows();
        ordered.sort(Comparator.comparingInt(BackpackWindow::zIndex).reversed());
        for (BackpackWindow window : ordered) {
            BackpackWindow.ResizeEdge edge = window.resizeEdgeAt(x, y);
            if (edge != BackpackWindow.ResizeEdge.NONE) {
                return new ResizeTarget(window, edge);
            }
            if (window.contains(x, y)) {
                return null;
            }
        }
        return null;
    }

    private List<RenderRect> visibleRegions(List<BackpackWindow> ordered, int windowIndex) {
        BackpackWindow window = ordered.get(windowIndex);
        List<RenderRect> regions = new ArrayList<>();
        regions.add(new RenderRect(window.x(), window.y(), window.x() + window.width(), window.y() + window.height()));

        for (int index = windowIndex + 1; index < ordered.size() && !regions.isEmpty(); index++) {
            BackpackWindow occluder = ordered.get(index);
            RenderRect covered = new RenderRect(
                    occluder.x(),
                    occluder.y(),
                    occluder.x() + occluder.width(),
                    occluder.y() + occluder.height());
            List<RenderRect> remaining = new ArrayList<>();
            for (RenderRect region : regions) {
                subtract(region, covered, remaining);
            }
            regions = remaining;
        }
        return regions;
    }

    private static void subtract(RenderRect source, RenderRect covered, List<RenderRect> output) {
        int intersectionLeft = Math.max(source.left(), covered.left());
        int intersectionTop = Math.max(source.top(), covered.top());
        int intersectionRight = Math.min(source.right(), covered.right());
        int intersectionBottom = Math.min(source.bottom(), covered.bottom());

        if (intersectionLeft >= intersectionRight || intersectionTop >= intersectionBottom) {
            output.add(source);
            return;
        }

        if (source.top() < intersectionTop) {
            output.add(new RenderRect(source.left(), source.top(), source.right(), intersectionTop));
        }
        if (intersectionBottom < source.bottom()) {
            output.add(new RenderRect(source.left(), intersectionBottom, source.right(), source.bottom()));
        }
        if (source.left() < intersectionLeft) {
            output.add(new RenderRect(source.left(), intersectionTop, intersectionLeft, intersectionBottom));
        }
        if (intersectionRight < source.right()) {
            output.add(new RenderRect(intersectionRight, intersectionTop, source.right(), intersectionBottom));
        }
    }

    private List<BackpackWindow> openWindows() {
        List<BackpackWindow> result = new ArrayList<>();
        for (BackpackWindow window : windows.values()) {
            if (window.isOpen()) {
                result.add(window);
            }
        }
        return result;
    }

    private void updateCursor(double mouseX, double mouseY) {
        ResizeTarget target = resizing == null ? topResizeAt(mouseX, mouseY) : null;
        BackpackWindow.ResizeEdge edge = resizing != null
                ? resizing.edge()
                : target == null ? BackpackWindow.ResizeEdge.NONE : target.edge();
        setCursor(cursorFor(edge));
    }

    private long cursorFor(BackpackWindow.ResizeEdge edge) {
        if (edge == BackpackWindow.ResizeEdge.NONE) {
            return 0L;
        }
        if (resizeEwCursor == 0L) {
            resizeEwCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_EW_CURSOR);
            resizeNsCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NS_CURSOR);
            resizeNwseCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR);
            resizeNeswCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NESW_CURSOR);
        }
        if (edge == BackpackWindow.ResizeEdge.LEFT || edge == BackpackWindow.ResizeEdge.RIGHT) {
            return resizeEwCursor;
        }
        if (edge == BackpackWindow.ResizeEdge.TOP || edge == BackpackWindow.ResizeEdge.BOTTOM) {
            return resizeNsCursor;
        }
        if (edge == BackpackWindow.ResizeEdge.TOP_LEFT || edge == BackpackWindow.ResizeEdge.BOTTOM_RIGHT) {
            return resizeNwseCursor;
        }
        return resizeNeswCursor;
    }

    private void setCursor(long cursor) {
        if (activeCursor == cursor) {
            return;
        }
        activeCursor = cursor;
        GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), cursor);
    }

    private void requestPersistedWindows(AbstractContainerScreen<?> screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || openWindows().size() >= MAX_OPEN_WINDOWS) {
            return;
        }

        List<Slot> slots = screen.getMenu().slots;
        for (int menuSlot = 0; menuSlot < slots.size(); menuSlot++) {
            Slot slot = slots.get(menuSlot);
            if (slot.container != minecraft.player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            String id = BackpackAccess.id(stack).orElse(null);
            if (id == null || !state.wasOpen(id) || windows.containsKey(id) || !restoreRequests.add(id)) {
                continue;
            }
            PacketDistributor.sendToServer(new RequestOpenBackpackPayload(menuSlot));
            if (openWindows().size() + restoreRequests.size() >= MAX_OPEN_WINDOWS) {
                return;
            }
        }
    }

    private static boolean isContainerScreen(AbstractContainerScreen<?> screen) {
        return !(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen);
    }

    private record RenderRect(int left, int top, int right, int bottom) {
    }

    private record ResizeTarget(BackpackWindow window, BackpackWindow.ResizeEdge edge) {
    }

    private record ResizeSession(
            BackpackWindow window,
            BackpackWindow.ResizeEdge edge,
            double startMouseX,
            double startMouseY,
            int originalX,
            int originalY,
            int originalLayoutColumns,
            int originalVisibleColumns,
            int originalVisibleRows) {
    }
}

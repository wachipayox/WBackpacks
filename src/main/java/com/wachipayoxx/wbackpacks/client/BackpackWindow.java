package com.wachipayoxx.wbackpacks.client;

import com.wachipayoxx.wbackpacks.backpack.BackpackAccess;
import com.wachipayoxx.wbackpacks.network.BackpackSlotClickPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

final class BackpackWindow {
    static final int TITLE_HEIGHT = 18;
    static final int RESIZE_MARGIN = 4;
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 5;
    private static final int TITLE_BUTTON_WIDTH = 14;

    enum FlowAxis {
        VERTICAL,
        HORIZONTAL
    }

    enum ResizeEdge {
        NONE(false, false, false, false),
        LEFT(true, false, false, false),
        RIGHT(false, true, false, false),
        TOP(false, false, true, false),
        BOTTOM(false, false, false, true),
        TOP_LEFT(true, false, true, false),
        TOP_RIGHT(false, true, true, false),
        BOTTOM_LEFT(true, false, false, true),
        BOTTOM_RIGHT(false, true, false, true);

        final boolean left;
        final boolean right;
        final boolean top;
        final boolean bottom;

        ResizeEdge(boolean left, boolean right, boolean top, boolean bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }

        boolean horizontal() {
            return left || right;
        }

        boolean vertical() {
            return top || bottom;
        }
    }

    private final String id;
    private int capacity;
    private int x;
    private int y;
    private int zIndex;
    private int visibleColumns;
    private int visibleRows;
    private int layoutColumns;
    private int scrollColumn;
    private int scrollRow;
    private FlowAxis flowAxis;
    private boolean open = true;
    private boolean minimized;

    BackpackWindow(
            String id,
            int capacity,
            int x,
            int y,
            int zIndex,
            int visibleColumns,
            int visibleRows,
            int layoutColumns,
            int scrollColumn,
            int scrollRow,
            FlowAxis flowAxis,
            boolean minimized) {
        this.id = id;
        this.capacity = clampCapacity(capacity);
        this.x = x;
        this.y = y;
        this.zIndex = zIndex;
        this.visibleColumns = visibleColumns <= 0 ? Math.min(9, this.capacity) : visibleColumns;
        this.visibleRows = visibleRows <= 0 ? Math.min(6, rowsFor(this.capacity, Math.max(1, this.visibleColumns))) : visibleRows;
        this.layoutColumns = layoutColumns <= 0 ? this.visibleColumns : layoutColumns;
        this.scrollColumn = Math.max(0, scrollColumn);
        this.scrollRow = Math.max(0, scrollRow);
        this.flowAxis = flowAxis == null ? FlowAxis.VERTICAL : flowAxis;
        this.minimized = minimized;
        clampLayout();
    }

    String id() { return id; }
    int x() { return x; }
    int y() { return y; }
    int zIndex() { return zIndex; }
    int visibleColumns() { return visibleColumns; }
    int visibleRows() { return visibleRows; }
    int layoutColumns() { return layoutColumns; }
    int scrollColumn() { return scrollColumn; }
    int scrollRow() { return scrollRow; }
    FlowAxis flowAxis() { return flowAxis; }
    boolean isOpen() { return open; }
    boolean isMinimized() { return minimized; }

    void setCapacity(int capacity) {
        this.capacity = clampCapacity(capacity);
        reflowForCurrentViewport();
        clampLayout();
    }

    void setOpen(boolean open) { this.open = open; }
    void setZIndex(int zIndex) { this.zIndex = zIndex; }
    void setMinimized(boolean minimized) { this.minimized = minimized; }
    void moveTo(int x, int y) { this.x = x; this.y = y; }

    int totalRows() {
        return rowsFor(capacity, layoutColumns);
    }

    int width() {
        return widthForColumns(visibleColumns);
    }

    int height() {
        return minimized ? TITLE_HEIGHT : heightForRows(visibleRows);
    }

    boolean contains(double mouseX, double mouseY) {
        return open && mouseX >= x && mouseX < x + width() && mouseY >= y && mouseY < y + height();
    }

    boolean titleContains(double mouseX, double mouseY) {
        return contains(mouseX, mouseY) && mouseY < y + TITLE_HEIGHT;
    }

    boolean closeContains(double mouseX, double mouseY) {
        return titleContains(mouseX, mouseY) && mouseX >= x + width() - TITLE_BUTTON_WIDTH;
    }

    boolean minimizeContains(double mouseX, double mouseY) {
        int right = x + width();
        return titleContains(mouseX, mouseY)
                && mouseX >= right - TITLE_BUTTON_WIDTH * 2
                && mouseX < right - TITLE_BUTTON_WIDTH;
    }

    ResizeEdge resizeEdgeAt(double mouseX, double mouseY) {
        if (!open || minimized) {
            return ResizeEdge.NONE;
        }
        boolean nearLeft = mouseX >= x - RESIZE_MARGIN && mouseX <= x + RESIZE_MARGIN;
        boolean nearRight = mouseX >= x + width() - RESIZE_MARGIN && mouseX <= x + width() + RESIZE_MARGIN;
        boolean nearTop = mouseY >= y - RESIZE_MARGIN && mouseY <= y + RESIZE_MARGIN;
        boolean nearBottom = mouseY >= y + height() - RESIZE_MARGIN && mouseY <= y + height() + RESIZE_MARGIN;
        boolean inHorizontalSpan = mouseX >= x - RESIZE_MARGIN && mouseX <= x + width() + RESIZE_MARGIN;
        boolean inVerticalSpan = mouseY >= y - RESIZE_MARGIN && mouseY <= y + height() + RESIZE_MARGIN;

        if (nearLeft && nearTop) return ResizeEdge.TOP_LEFT;
        if (nearRight && nearTop) return ResizeEdge.TOP_RIGHT;
        if (nearLeft && nearBottom) return ResizeEdge.BOTTOM_LEFT;
        if (nearRight && nearBottom) return ResizeEdge.BOTTOM_RIGHT;
        if (nearLeft && inVerticalSpan) return ResizeEdge.LEFT;
        if (nearRight && inVerticalSpan) return ResizeEdge.RIGHT;
        if (nearTop && inHorizontalSpan) return ResizeEdge.TOP;
        if (nearBottom && inHorizontalSpan) return ResizeEdge.BOTTOM;
        return ResizeEdge.NONE;
    }

    void resizeFrom(
            ResizeEdge edge,
            int originalX,
            int originalY,
            int originalColumns,
            int originalRows,
            int originalLayoutColumns,
            FlowAxis originalFlowAxis,
            double deltaX,
            double deltaY,
            int screenWidth,
            int screenHeight) {
        int columns = originalColumns;
        int rows = originalRows;

        int maxColumnsOnScreen = Math.max(1, (screenWidth - PADDING * 2) / SLOT_SIZE);
        int maxColumns = Math.max(1, Math.min(capacity, maxColumnsOnScreen));
        if (edge.right) {
            columns = originalColumns + roundedSlots(deltaX);
        } else if (edge.left) {
            columns = originalColumns - roundedSlots(deltaX);
        }
        columns = Math.max(1, Math.min(maxColumns, columns));

        int maxRowsOnScreen = Math.max(1, (screenHeight - TITLE_HEIGHT - PADDING * 2) / SLOT_SIZE);
        int maxRows = Math.max(1, Math.min(capacity, maxRowsOnScreen));
        if (edge.bottom) {
            rows = originalRows + roundedSlots(deltaY);
        } else if (edge.top) {
            rows = originalRows - roundedSlots(deltaY);
        }
        rows = Math.max(1, Math.min(maxRows, rows));

        int columnsRemoved = Math.max(0, originalColumns - columns);
        int rowsRemoved = Math.max(0, originalRows - rows);
        FlowAxis newFlowAxis = originalFlowAxis;
        if (columnsRemoved > 0 || rowsRemoved > 0) {
            if (columnsRemoved >= rowsRemoved && columnsRemoved > 0) {
                newFlowAxis = FlowAxis.VERTICAL;
            } else {
                newFlowAxis = FlowAxis.HORIZONTAL;
            }
        }

        int newLayoutColumns = newFlowAxis == FlowAxis.VERTICAL
                ? columns
                : Math.max(1, Math.min(capacity, ceilDiv(capacity, rows)));

        int oldWidth = widthForColumns(originalColumns);
        int oldHeight = heightForRows(originalRows);
        boolean layoutChanged = newLayoutColumns != layoutColumns || newFlowAxis != flowAxis;

        visibleColumns = columns;
        visibleRows = rows;
        layoutColumns = newLayoutColumns;
        flowAxis = newFlowAxis;
        clampLayout();
        if (layoutChanged) {
            scrollColumn = 0;
            scrollRow = 0;
        } else {
            clampScroll();
        }

        int newWidth = width();
        int newHeight = height();
        if (edge.left) {
            x = originalX + oldWidth - newWidth;
        } else {
            x = originalX;
        }
        if (edge.top) {
            y = originalY + oldHeight - newHeight;
        } else {
            y = originalY;
        }

        clampToScreen(screenWidth, screenHeight);
    }

    void clampToScreen(int screenWidth, int screenHeight) {
        int halfWidth = Math.max(1, width() / 2);
        int minX = -halfWidth;
        int maxX = Math.max(minX, screenWidth - halfWidth);
        int maxY = Math.max(0, screenHeight - height());
        x = Math.max(minX, Math.min(maxX, x));
        y = Math.max(0, Math.min(maxY, y));
    }

    void scroll(double delta, boolean horizontal) {
        if (delta == 0 || minimized) {
            return;
        }
        if (horizontal) {
            if (maxScrollColumn() <= 0) {
                return;
            }
            scrollColumn += delta > 0 ? -1 : 1;
        } else {
            if (maxScrollRow() <= 0) {
                return;
            }
            scrollRow += delta > 0 ? -1 : 1;
        }
        clampScroll();
    }

    int slotAt(double mouseX, double mouseY) {
        if (minimized) {
            return -1;
        }
        int gridX = x + PADDING;
        int gridY = y + TITLE_HEIGHT + PADDING;
        if (mouseX < gridX || mouseY < gridY) {
            return -1;
        }
        int col = ((int) mouseX - gridX) / SLOT_SIZE;
        int row = ((int) mouseY - gridY) / SLOT_SIZE;
        if (col < 0 || col >= visibleColumns || row < 0 || row >= visibleRows) {
            return -1;
        }

        int contentColumn = scrollColumn + col;
        int contentRow = scrollRow + row;
        if (contentColumn >= layoutColumns || contentRow >= totalRows()) {
            return -1;
        }
        int slot = contentRow * layoutColumns + contentColumn;
        return slot < capacity ? slot : -1;
    }

    void clickSlot(int slot, int button, boolean shift) {
        if (slot >= 0 && slot < capacity) {
            PacketDistributor.sendToServer(new BackpackSlotClickPayload(id, slot, button, shift));
        }
    }

    ItemStack backpackStack() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? ItemStack.EMPTY : BackpackAccess.findOwned(minecraft.player, id);
    }

    ItemStack stackInSlot(int slot) {
        ItemStack backpack = backpackStack();
        IItemHandler handler = backpack.isEmpty() ? null : BackpackAccess.handler(backpack);
        return handler == null || slot < 0 || slot >= handler.getSlots() ? ItemStack.EMPTY : handler.getStackInSlot(slot);
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        clampToScreen(graphics.guiWidth(), graphics.guiHeight());

        int right = x + width();
        int bottom = y + height();
        if (!minimized) {
            graphics.fill(x, y + TITLE_HEIGHT, right, bottom, 0xE8C6C6C6);
        }
        graphics.fill(x, y, right, y + TITLE_HEIGHT, 0xF03A3A3A);

        int minimizeLeft = right - TITLE_BUTTON_WIDTH * 2;
        int closeLeft = right - TITLE_BUTTON_WIDTH;
        graphics.fill(minimizeLeft, y + 1, closeLeft, y + TITLE_HEIGHT, minimizeContains(mouseX, mouseY) ? 0xFF666666 : 0xFF505050);
        graphics.fill(closeLeft, y + 1, right, y + TITLE_HEIGHT, closeContains(mouseX, mouseY) ? 0xFFE05050 : 0xFF555555);

        ItemStack backpack = backpackStack();
        Component title = backpack.isEmpty() ? Component.translatable("w_backpacks.window.missing") : backpack.getHoverName();
        drawClippedTitle(graphics, minecraft, title, minimizeLeft);
        graphics.drawString(minecraft.font, minimized ? "+" : "-", minimizeLeft + 4, y + 5, 0xFFFFFFFF, false);
        graphics.drawString(minecraft.font, "x", closeLeft + 4, y + 5, 0xFFFFFFFF, false);

        if (minimized) {
            return;
        }

        int gridX = x + PADDING;
        int gridY = y + TITLE_HEIGHT + PADDING;
        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < visibleColumns; col++) {
                int contentColumn = scrollColumn + col;
                int contentRow = scrollRow + row;
                if (contentColumn >= layoutColumns || contentRow >= totalRows()) {
                    continue;
                }
                int slot = contentRow * layoutColumns + contentColumn;
                if (slot >= capacity) {
                    continue;
                }
                int slotX = gridX + col * SLOT_SIZE;
                int slotY = gridY + row * SLOT_SIZE;
                graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0xFF8B8B8B);
                graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF202020);
                if (mouseX >= slotX + 1 && mouseX < slotX + 17 && mouseY >= slotY + 1 && mouseY < slotY + 17) {
                    graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0x80FFFFFF);
                }
                ItemStack stack = stackInSlot(slot);
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, slotX + 1, slotY + 1);
                    graphics.renderItemDecorations(minecraft.font, stack, slotX + 1, slotY + 1);
                }
            }
        }

        renderScrollIndicators(graphics, right, bottom, gridX, gridY);
    }

    private void drawClippedTitle(GuiGraphics graphics, Minecraft minecraft, Component title, int controlsLeft) {
        int availableWidth = controlsLeft - (x + 5) - 3;
        if (availableWidth <= 0) {
            return;
        }
        String text = title.getString();
        while (!text.isEmpty() && minecraft.font.width(text) > availableWidth) {
            text = text.substring(0, text.length() - 1);
        }
        if (!text.isEmpty()) {
            graphics.drawString(minecraft.font, text, x + 5, y + 5, 0xFFFFFFFF, false);
        }
    }

    private void renderScrollIndicators(GuiGraphics graphics, int right, int bottom, int gridX, int gridY) {
        int maxRow = maxScrollRow();
        if (maxRow > 0) {
            int trackHeight = visibleRows * SLOT_SIZE;
            int thumbHeight = Math.max(8, trackHeight * visibleRows / totalRows());
            int travel = Math.max(0, trackHeight - thumbHeight);
            int thumbY = gridY + travel * scrollRow / maxRow;
            graphics.fill(right - 3, gridY, right - 1, gridY + trackHeight, 0xFF555555);
            graphics.fill(right - 3, thumbY, right - 1, thumbY + thumbHeight, 0xFFE0E0E0);
        }

        int maxColumn = maxScrollColumn();
        if (maxColumn > 0) {
            int trackWidth = visibleColumns * SLOT_SIZE;
            int thumbWidth = Math.max(8, trackWidth * visibleColumns / layoutColumns);
            int travel = Math.max(0, trackWidth - thumbWidth);
            int thumbX = gridX + travel * scrollColumn / maxColumn;
            graphics.fill(gridX, bottom - 3, gridX + trackWidth, bottom - 1, 0xFF555555);
            graphics.fill(thumbX, bottom - 3, thumbX + thumbWidth, bottom - 1, 0xFFE0E0E0);
        }
    }

    private void reflowForCurrentViewport() {
        layoutColumns = flowAxis == FlowAxis.VERTICAL
                ? Math.max(1, Math.min(capacity, visibleColumns))
                : Math.max(1, Math.min(capacity, ceilDiv(capacity, Math.max(1, visibleRows))));
    }

    private int maxScrollColumn() {
        return Math.max(0, layoutColumns - visibleColumns);
    }

    private int maxScrollRow() {
        return Math.max(0, totalRows() - visibleRows);
    }

    private void clampLayout() {
        layoutColumns = Math.max(1, Math.min(capacity, layoutColumns));
        visibleColumns = Math.max(1, Math.min(layoutColumns, visibleColumns));
        visibleRows = Math.max(1, Math.min(totalRows(), visibleRows));
        clampScroll();
    }

    private void clampScroll() {
        scrollColumn = Math.max(0, Math.min(maxScrollColumn(), scrollColumn));
        scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow));
    }

    private static int clampCapacity(int capacity) {
        return Math.max(1, Math.min(256, capacity));
    }

    private static int roundedSlots(double pixels) {
        return (int) Math.round(pixels / SLOT_SIZE);
    }

    private static int widthForColumns(int columns) {
        return PADDING * 2 + Math.max(1, columns) * SLOT_SIZE;
    }

    private static int heightForRows(int rows) {
        return TITLE_HEIGHT + PADDING * 2 + Math.max(1, rows) * SLOT_SIZE;
    }

    private static int rowsFor(int capacity, int columns) {
        return ceilDiv(capacity, Math.max(1, columns));
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}

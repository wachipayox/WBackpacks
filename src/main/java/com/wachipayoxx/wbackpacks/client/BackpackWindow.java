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
    private static final int MIN_WINDOW_WIDTH = TITLE_BUTTON_WIDTH * 2;

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

    /** Number of columns in the logical slot grid. Can be wider than the viewport. */
    private int layoutColumns;
    /** Number of columns currently visible in the window. */
    private int visibleColumns;
    /** Number of rows currently visible in the window. */
    private int visibleRows;
    private int scrollRow;
    private int scrollColumn;

    private boolean open = true;
    private boolean minimized;

    BackpackWindow(
            String id,
            int capacity,
            int x,
            int y,
            int zIndex,
            int scrollRow,
            int scrollColumn,
            int layoutColumns,
            int visibleColumns,
            int visibleRows,
            boolean minimized) {
        this.id = id;
        this.capacity = clampCapacity(capacity);
        this.x = x;
        this.y = y;
        this.zIndex = zIndex;
        this.layoutColumns = clampColumns(layoutColumns <= 0 ? Math.min(9, this.capacity) : layoutColumns);
        this.visibleColumns = visibleColumns <= 0 ? this.layoutColumns : visibleColumns;
        this.visibleRows = visibleRows <= 0 ? Math.min(6, totalRows()) : visibleRows;
        this.scrollRow = Math.max(0, scrollRow);
        this.scrollColumn = Math.max(0, scrollColumn);
        this.minimized = minimized;
        clampLayout();
    }

    String id() { return id; }
    int x() { return x; }
    int y() { return y; }
    int zIndex() { return zIndex; }
    int layoutColumns() { return layoutColumns; }
    int visibleColumns() { return visibleColumns; }
    int visibleRows() { return visibleRows; }
    int scrollRow() { return scrollRow; }
    int scrollColumn() { return scrollColumn; }
    boolean isOpen() { return open; }
    boolean isMinimized() { return minimized; }

    void setCapacity(int capacity) {
        this.capacity = clampCapacity(capacity);
        clampLayout();
    }

    void setOpen(boolean open) { this.open = open; }
    void setZIndex(int zIndex) { this.zIndex = zIndex; }
    void setMinimized(boolean minimized) { this.minimized = minimized; }
    void moveTo(int x, int y) { this.x = x; this.y = y; }

    int totalRows() {
        return rowsForColumns(capacity, layoutColumns);
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
        return titleContains(mouseX, mouseY)
                && mouseX >= x + width() - TITLE_BUTTON_WIDTH * 2
                && mouseX < x + width() - TITLE_BUTTON_WIDTH;
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
            int originalLayoutColumns,
            int originalVisibleColumns,
            int originalVisibleRows,
            double deltaX,
            double deltaY,
            int screenWidth,
            int screenHeight) {
        int requestedColumns = originalVisibleColumns;
        int requestedRows = originalVisibleRows;

        if (edge.right) {
            requestedColumns = originalVisibleColumns + roundedSlots(deltaX);
        } else if (edge.left) {
            requestedColumns = originalVisibleColumns - roundedSlots(deltaX);
        }
        requestedColumns = Math.max(1, Math.min(capacity, requestedColumns));

        int maxRowsOnScreen = Math.max(1, (screenHeight - TITLE_HEIGHT - PADDING * 2) / SLOT_SIZE);
        if (edge.bottom) {
            requestedRows = originalVisibleRows + roundedSlots(deltaY);
        } else if (edge.top) {
            requestedRows = originalVisibleRows - roundedSlots(deltaY);
        }
        requestedRows = Math.max(1, Math.min(Math.min(capacity, maxRowsOnScreen), requestedRows));

        boolean horizontalChanged = edge.horizontal() && requestedColumns != originalVisibleColumns;
        boolean verticalChanged = edge.vertical() && requestedRows != originalVisibleRows;
        if (!horizontalChanged && !verticalChanged) {
            return;
        }

        boolean widthExpanded = horizontalChanged && requestedColumns > originalVisibleColumns;
        boolean heightExpanded = verticalChanged && requestedRows > originalVisibleRows;
        boolean verticalFlow = originalLayoutColumns == originalVisibleColumns;
        boolean horizontalFlow = originalLayoutColumns > originalVisibleColumns
                && rowsForColumns(capacity, originalLayoutColumns) <= originalVisibleRows;

        int oldWidth = widthForColumns(originalVisibleColumns);
        int oldHeight = heightForRows(originalVisibleRows);

        if (horizontalChanged && !verticalChanged) {
            if (widthExpanded && horizontalFlow) {
                // A short/wide layout that is being expanded sideways should stay short/wide.
                // Reveal more of the columns that already exist instead of turning them back into rows.
                layoutColumns = originalLayoutColumns;
                visibleColumns = Math.min(requestedColumns, layoutColumns);
                visibleRows = Math.min(originalVisibleRows, totalRows());
            } else {
                // Width is authoritative: reflow into exactly this many logical columns.
                // Any hidden content is therefore genuinely below the viewport.
                layoutColumns = requestedColumns;
                visibleColumns = requestedColumns;
                visibleRows = Math.min(originalVisibleRows, totalRows());
            }
        } else if (verticalChanged && !horizontalChanged) {
            if (heightExpanded && verticalFlow) {
                // A narrow/tall layout that is being expanded downward should stay narrow/tall.
                // Reveal more existing rows instead of reinterpreting them as off-screen columns.
                layoutColumns = originalLayoutColumns;
                visibleColumns = Math.min(originalVisibleColumns, layoutColumns);
                visibleRows = Math.min(requestedRows, totalRows());
            } else {
                // Height is authoritative: reflow into enough logical columns to keep the requested rows.
                // If those logical columns exceed the current viewport width, that is genuine horizontal overflow.
                layoutColumns = columnsForRows(capacity, requestedRows);
                visibleRows = Math.min(requestedRows, totalRows());
                visibleColumns = Math.min(originalVisibleColumns, layoutColumns);
            }
        } else if ((long) requestedColumns * requestedRows >= capacity) {
            // The requested rectangle can contain everything, so hug the real content and avoid dead space.
            layoutColumns = requestedColumns;
            visibleColumns = requestedColumns;
            visibleRows = Math.min(requestedRows, totalRows());
        } else if (Math.abs(deltaX) >= Math.abs(deltaY)) {
            // Corner resize dominated by width: vertical overflow.
            layoutColumns = requestedColumns;
            visibleColumns = requestedColumns;
            visibleRows = Math.min(requestedRows, totalRows());
        } else {
            // Corner resize dominated by height: horizontal overflow.
            layoutColumns = columnsForRows(capacity, requestedRows);
            visibleRows = Math.min(requestedRows, totalRows());
            visibleColumns = Math.min(requestedColumns, layoutColumns);
        }

        clampLayout();

        int newWidth = width();
        int newHeight = height();
        x = edge.left ? originalX + oldWidth - newWidth : originalX;
        y = edge.top ? originalY + oldHeight - newHeight : originalY;

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
            if (!hasHorizontalOverflow()) {
                return;
            }
            scrollColumn += delta > 0 ? -1 : 1;
        } else {
            if (!hasVerticalOverflow()) {
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
        int gridX = gridX();
        int gridY = y + TITLE_HEIGHT + PADDING;
        if (mouseX < gridX || mouseY < gridY) {
            return -1;
        }
        int col = ((int) mouseX - gridX) / SLOT_SIZE;
        int row = ((int) mouseY - gridY) / SLOT_SIZE;
        if (col < 0 || col >= visibleColumns || row < 0 || row >= visibleRows) {
            return -1;
        }

        int logicalColumn = scrollColumn + col;
        int logicalRow = scrollRow + row;
        if (logicalColumn >= layoutColumns || logicalRow >= totalRows()) {
            return -1;
        }
        int slot = logicalRow * layoutColumns + logicalColumn;
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
        graphics.fill(x, y, right, bottom, minimized ? 0xF03A3A3A : 0xE8C6C6C6);
        graphics.fill(x + 1, y + 1, right - 1, y + TITLE_HEIGHT, 0xF03A3A3A);

        int minimizeX = right - TITLE_BUTTON_WIDTH * 2;
        int closeX = right - TITLE_BUTTON_WIDTH;
        graphics.fill(minimizeX, y + 1, closeX, y + TITLE_HEIGHT,
                minimizeContains(mouseX, mouseY) ? 0xFF666666 : 0xFF555555);
        graphics.fill(closeX, y + 1, right - 1, y + TITLE_HEIGHT,
                closeContains(mouseX, mouseY) ? 0xFFE05050 : 0xFF555555);

        ItemStack backpack = backpackStack();
        Component title = backpack.isEmpty() ? Component.translatable("w_backpacks.window.missing") : backpack.getHoverName();
        if (width() >= 82) {
            graphics.drawString(minecraft.font, title, x + 5, y + 5, 0xFFFFFFFF, false);
        }
        graphics.drawString(minecraft.font, minimized ? "+" : "-", minimizeX + 5, y + 5, 0xFFFFFFFF, false);
        graphics.drawString(minecraft.font, "×", closeX + 4, y + 5, 0xFFFFFFFF, false);

        if (minimized) {
            return;
        }

        int gridX = gridX();
        int gridY = y + TITLE_HEIGHT + PADDING;
        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < visibleColumns; col++) {
                int logicalColumn = scrollColumn + col;
                int logicalRow = scrollRow + row;
                if (logicalColumn >= layoutColumns || logicalRow >= totalRows()) {
                    continue;
                }
                int slot = logicalRow * layoutColumns + logicalColumn;
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

    private void renderScrollIndicators(GuiGraphics graphics, int right, int bottom, int gridX, int gridY) {
        if (hasVerticalOverflow()) {
            int trackHeight = visibleRows * SLOT_SIZE;
            int thumbHeight = Math.max(8, trackHeight * visibleRows / totalRows());
            int travel = Math.max(0, trackHeight - thumbHeight);
            int maxScroll = maxScrollRow();
            int thumbY = gridY + (maxScroll == 0 ? 0 : travel * scrollRow / maxScroll);
            graphics.fill(right - 3, gridY, right - 1, gridY + trackHeight, 0xFF555555);
            graphics.fill(right - 3, thumbY, right - 1, thumbY + thumbHeight, 0xFFE0E0E0);
        }

        if (hasHorizontalOverflow()) {
            int trackWidth = visibleColumns * SLOT_SIZE;
            int thumbWidth = Math.max(8, trackWidth * visibleColumns / layoutColumns);
            int travel = Math.max(0, trackWidth - thumbWidth);
            int maxScroll = maxScrollColumn();
            int thumbX = gridX + (maxScroll == 0 ? 0 : travel * scrollColumn / maxScroll);
            graphics.fill(gridX, bottom - 3, gridX + trackWidth, bottom - 1, 0xFF555555);
            graphics.fill(thumbX, bottom - 3, thumbX + thumbWidth, bottom - 1, 0xFFE0E0E0);
        }
    }

    private boolean hasVerticalOverflow() {
        return totalRows() > visibleRows;
    }

    private boolean hasHorizontalOverflow() {
        return layoutColumns > visibleColumns;
    }

    private int maxScrollRow() {
        return Math.max(0, totalRows() - visibleRows);
    }

    private int maxScrollColumn() {
        return Math.max(0, layoutColumns - visibleColumns);
    }

    private int gridX() {
        int contentWidth = visibleColumns * SLOT_SIZE;
        return x + Math.max(PADDING, (width() - contentWidth) / 2);
    }

    private void clampLayout() {
        layoutColumns = clampColumns(layoutColumns);
        visibleColumns = Math.max(1, Math.min(layoutColumns, visibleColumns));
        visibleRows = Math.max(1, Math.min(totalRows(), visibleRows));
        clampScroll();
    }

    private void clampScroll() {
        scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow));
        scrollColumn = Math.max(0, Math.min(maxScrollColumn(), scrollColumn));
    }

    private int clampColumns(int columns) {
        return Math.max(1, Math.min(capacity, columns));
    }

    private static int clampCapacity(int capacity) {
        return Math.max(1, Math.min(256, capacity));
    }

    private static int roundedSlots(double pixels) {
        return (int) Math.round(pixels / SLOT_SIZE);
    }

    private static int rowsForColumns(int capacity, int columns) {
        return (capacity + columns - 1) / columns;
    }

    private static int columnsForRows(int capacity, int rows) {
        return (capacity + rows - 1) / rows;
    }

    private static int widthForColumns(int columns) {
        return Math.max(MIN_WINDOW_WIDTH, PADDING * 2 + columns * SLOT_SIZE);
    }

    private static int heightForRows(int rows) {
        return TITLE_HEIGHT + PADDING * 2 + rows * SLOT_SIZE;
    }
}

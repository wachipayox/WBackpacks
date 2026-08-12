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
    private static final int MIN_WINDOW_WIDTH = 72;
    private static final int MAX_COLUMNS = 9;

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
    private int scrollSlot;
    private boolean open = true;

    BackpackWindow(String id, int capacity, int x, int y, int zIndex, int scrollSlot, int visibleColumns, int visibleRows) {
        this.id = id;
        this.capacity = clampCapacity(capacity);
        this.x = x;
        this.y = y;
        this.zIndex = zIndex;
        this.visibleColumns = clampColumns(visibleColumns <= 0 ? Math.min(MAX_COLUMNS, this.capacity) : visibleColumns);
        this.visibleRows = visibleRows <= 0 ? Math.min(6, totalRows()) : visibleRows;
        this.scrollSlot = Math.max(0, scrollSlot);
        clampLayout();
    }

    String id() { return id; }
    int x() { return x; }
    int y() { return y; }
    int zIndex() { return zIndex; }
    int scrollSlot() { return scrollSlot; }
    int visibleColumns() { return visibleColumns; }
    int visibleRows() { return visibleRows; }
    boolean isOpen() { return open; }

    void setCapacity(int capacity) {
        this.capacity = clampCapacity(capacity);
        clampLayout();
    }

    void setOpen(boolean open) { this.open = open; }
    void setZIndex(int zIndex) { this.zIndex = zIndex; }
    void moveTo(int x, int y) { this.x = x; this.y = y; }

    int totalRows() {
        return (capacity + visibleColumns - 1) / visibleColumns;
    }

    int width() {
        return Math.max(MIN_WINDOW_WIDTH, PADDING * 2 + visibleColumns * SLOT_SIZE);
    }

    int height() {
        return TITLE_HEIGHT + PADDING * 2 + visibleRows * SLOT_SIZE;
    }

    int maxVisibleColumns() {
        return Math.min(MAX_COLUMNS, capacity);
    }

    int maxVisibleRows() {
        return totalRows();
    }

    boolean contains(double mouseX, double mouseY) {
        return open && mouseX >= x && mouseX < x + width() && mouseY >= y && mouseY < y + height();
    }

    boolean titleContains(double mouseX, double mouseY) {
        return contains(mouseX, mouseY) && mouseY < y + TITLE_HEIGHT;
    }

    boolean closeContains(double mouseX, double mouseY) {
        return titleContains(mouseX, mouseY) && mouseX >= x + width() - TITLE_HEIGHT;
    }

    ResizeEdge resizeEdgeAt(double mouseX, double mouseY) {
        if (!open) {
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

    void resizeFrom(ResizeEdge edge, int originalX, int originalY, int originalColumns, int originalRows,
                    double deltaX, double deltaY, int screenWidth, int screenHeight) {
        int columns = originalColumns;
        int rows = originalRows;

        if (edge.right) {
            columns = originalColumns + roundedSlots(deltaX);
        } else if (edge.left) {
            columns = originalColumns - roundedSlots(deltaX);
        }
        columns = Math.max(1, Math.min(Math.min(MAX_COLUMNS, capacity), columns));

        int rowsForColumns = (capacity + columns - 1) / columns;
        if (edge.bottom) {
            rows = originalRows + roundedSlots(deltaY);
        } else if (edge.top) {
            rows = originalRows - roundedSlots(deltaY);
        }
        rows = Math.max(1, Math.min(rowsForColumns, rows));

        int maxRowsOnScreen = Math.max(1, (screenHeight - TITLE_HEIGHT - PADDING * 2) / SLOT_SIZE);
        rows = Math.min(rows, maxRowsOnScreen);

        int oldWidth = widthForColumns(originalColumns);
        int oldHeight = heightForRows(originalRows);
        int newWidth = widthForColumns(columns);
        int newHeight = heightForRows(rows);

        visibleColumns = columns;
        visibleRows = rows;

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

        clampLayout();
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
        if (capacity <= visibleSlotCount() || delta == 0) {
            scrollSlot = 0;
            return;
        }
        int step = horizontal ? 1 : visibleColumns;
        if (delta > 0) {
            scrollSlot -= step;
        } else {
            scrollSlot += step;
        }
        clampScroll();
    }

    int slotAt(double mouseX, double mouseY) {
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
        int slot = scrollSlot + row * visibleColumns + col;
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
        graphics.fill(x, y, right, bottom, 0xE8C6C6C6);
        graphics.fill(x + 1, y + 1, right - 1, y + TITLE_HEIGHT, 0xF03A3A3A);
        graphics.fill(right - TITLE_HEIGHT, y + 1, right - 1, y + TITLE_HEIGHT, closeContains(mouseX, mouseY) ? 0xFFE05050 : 0xFF555555);

        ItemStack backpack = backpackStack();
        Component title = backpack.isEmpty() ? Component.translatable("w_backpacks.window.missing") : backpack.getHoverName();
        graphics.drawString(minecraft.font, title, x + 5, y + 5, 0xFFFFFFFF, false);
        graphics.drawString(minecraft.font, "×", right - 12, y + 5, 0xFFFFFFFF, false);

        int gridX = x + PADDING;
        int gridY = y + TITLE_HEIGHT + PADDING;
        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < visibleColumns; col++) {
                int slot = scrollSlot + row * visibleColumns + col;
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
        int maxScroll = maxScrollSlot();
        if (maxScroll <= 0) {
            return;
        }

        int row = scrollSlot / visibleColumns;
        int maxRow = Math.max(1, (maxScroll + visibleColumns - 1) / visibleColumns);
        int trackHeight = visibleRows * SLOT_SIZE;
        int thumbHeight = Math.max(8, trackHeight * visibleSlotCount() / capacity);
        int travelY = Math.max(0, trackHeight - thumbHeight);
        int thumbY = gridY + travelY * Math.min(row, maxRow) / maxRow;
        graphics.fill(right - 3, gridY, right - 1, gridY + trackHeight, 0xFF555555);
        graphics.fill(right - 3, thumbY, right - 1, thumbY + thumbHeight, 0xFFE0E0E0);

        int columnOffset = scrollSlot % visibleColumns;
        if (visibleColumns > 1) {
            int trackWidth = visibleColumns * SLOT_SIZE;
            int thumbWidth = Math.max(8, trackWidth / visibleColumns);
            int travelX = Math.max(0, trackWidth - thumbWidth);
            int thumbX = gridX + travelX * columnOffset / (visibleColumns - 1);
            graphics.fill(gridX, bottom - 3, gridX + trackWidth, bottom - 1, 0xFF555555);
            graphics.fill(thumbX, bottom - 3, thumbX + thumbWidth, bottom - 1, 0xFFE0E0E0);
        }
    }

    private int visibleSlotCount() {
        return visibleColumns * visibleRows;
    }

    private int maxScrollSlot() {
        return Math.max(0, capacity - visibleSlotCount());
    }

    private void clampLayout() {
        visibleColumns = clampColumns(visibleColumns);
        visibleRows = Math.max(1, Math.min(totalRows(), visibleRows));
        clampScroll();
    }

    private void clampScroll() {
        scrollSlot = Math.max(0, Math.min(maxScrollSlot(), scrollSlot));
    }

    private int clampColumns(int columns) {
        return Math.max(1, Math.min(Math.min(MAX_COLUMNS, capacity), columns));
    }

    private static int clampCapacity(int capacity) {
        return Math.max(1, Math.min(256, capacity));
    }

    private static int roundedSlots(double pixels) {
        return (int) Math.round(pixels / SLOT_SIZE);
    }

    private static int widthForColumns(int columns) {
        return Math.max(MIN_WINDOW_WIDTH, PADDING * 2 + columns * SLOT_SIZE);
    }

    private static int heightForRows(int rows) {
        return TITLE_HEIGHT + PADDING * 2 + rows * SLOT_SIZE;
    }
}

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
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 5;
    private static final int MAX_VISIBLE_ROWS = 6;

    private final String id;
    private int capacity;
    private int x;
    private int y;
    private int zIndex;
    private int scrollRow;
    private boolean open = true;

    BackpackWindow(String id, int capacity, int x, int y, int zIndex, int scrollRow) {
        this.id = id;
        this.capacity = Math.max(1, Math.min(256, capacity));
        this.x = x;
        this.y = y;
        this.zIndex = zIndex;
        this.scrollRow = scrollRow;
        clampScroll();
    }

    String id() { return id; }
    int x() { return x; }
    int y() { return y; }
    int zIndex() { return zIndex; }
    int scrollRow() { return scrollRow; }
    boolean isOpen() { return open; }

    void setCapacity(int capacity) {
        this.capacity = Math.max(1, Math.min(256, capacity));
        clampScroll();
    }

    void setOpen(boolean open) { this.open = open; }
    void setZIndex(int zIndex) { this.zIndex = zIndex; }
    void moveTo(int x, int y) { this.x = x; this.y = y; }

    int columns() {
        return Math.min(9, capacity);
    }

    int totalRows() {
        return (capacity + columns() - 1) / columns();
    }

    int visibleRows() {
        return Math.min(MAX_VISIBLE_ROWS, totalRows());
    }

    int width() {
        return PADDING * 2 + columns() * SLOT_SIZE;
    }

    int height() {
        return TITLE_HEIGHT + PADDING * 2 + visibleRows() * SLOT_SIZE;
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

    void clampToScreen(int screenWidth, int screenHeight) {
        int maxX = Math.max(0, screenWidth - Math.min(width(), TITLE_HEIGHT));
        int maxY = Math.max(0, screenHeight - TITLE_HEIGHT);
        x = Math.max(-width() + TITLE_HEIGHT, Math.min(maxX, x));
        y = Math.max(0, Math.min(maxY, y));
    }

    void scroll(double delta) {
        if (totalRows() <= visibleRows()) {
            return;
        }
        if (delta > 0) {
            scrollRow--;
        } else if (delta < 0) {
            scrollRow++;
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
        if (col < 0 || col >= columns() || row < 0 || row >= visibleRows()) {
            return -1;
        }
        int slot = (scrollRow + row) * columns() + col;
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
        for (int row = 0; row < visibleRows(); row++) {
            for (int col = 0; col < columns(); col++) {
                int slot = (scrollRow + row) * columns() + col;
                if (slot >= capacity) {
                    continue;
                }
                int slotX = gridX + col * SLOT_SIZE;
                int slotY = gridY + row * SLOT_SIZE;
                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF8B8B8B);
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

        if (totalRows() > visibleRows()) {
            int barX = right - 3;
            int trackTop = gridY;
            int trackHeight = visibleRows() * SLOT_SIZE;
            int thumbHeight = Math.max(8, trackHeight * visibleRows() / totalRows());
            int travel = trackHeight - thumbHeight;
            int maxScroll = totalRows() - visibleRows();
            int thumbY = trackTop + (maxScroll == 0 ? 0 : travel * scrollRow / maxScroll);
            graphics.fill(barX, trackTop, barX + 2, trackTop + trackHeight, 0xFF555555);
            graphics.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, 0xFFE0E0E0);
        }
    }

    private void clampScroll() {
        scrollRow = Math.max(0, Math.min(Math.max(0, totalRows() - visibleRows()), scrollRow));
    }
}

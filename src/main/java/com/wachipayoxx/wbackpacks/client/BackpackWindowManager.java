package com.wachipayoxx.wbackpacks.client;

import com.wachipayoxx.wbackpacks.backpack.BackpackAccess;
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
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BackpackWindowManager {
    private static final BackpackWindowManager INSTANCE = new BackpackWindowManager();
    private static final int MAX_OPEN_WINDOWS = 8;

    private final Map<String, BackpackWindow> windows = new HashMap<>();
    private final Set<String> restoreRequests = new HashSet<>();
    private final BackpackWindowStateStore state = new BackpackWindowStateStore();

    private BackpackWindow dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private int nextZ = 1;

    public static BackpackWindowManager get() {
        return INSTANCE;
    }

    public void open(String id, int capacity) {
        BackpackWindow existing = windows.get(id);
        if (existing != null) {
            existing.setCapacity(capacity);
            existing.setOpen(true);
            bringToFront(existing);
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
        BackpackWindow window = new BackpackWindow(
                id,
                capacity,
                state.x(id, fallbackX),
                state.y(id, fallbackY),
                Math.max(state.z(id, nextZ), nextZ++),
                state.scroll(id));
        windows.put(id, window);
        bringToFront(window);
        restoreRequests.remove(id);
        state.save(window);
    }

    public void render(AbstractContainerScreen<?> screen, GuiGraphics graphics, int mouseX, int mouseY) {
        requestPersistedWindows(screen);
        List<BackpackWindow> ordered = openWindows();
        ordered.sort(Comparator.comparingInt(BackpackWindow::zIndex));
        for (BackpackWindow window : ordered) {
            window.render(graphics, mouseX, mouseY);
        }

        BackpackWindow hovered = topAt(mouseX, mouseY);
        if (hovered != null) {
            int slot = hovered.slotAt(mouseX, mouseY);
            ItemStack stack = hovered.stackInSlot(slot);
            if (!stack.isEmpty() && screen.getMenu().getCarried().isEmpty()) {
                graphics.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
            }
        }

        if (!screen.getMenu().getCarried().isEmpty() && hovered != null) {
            ItemStack carried = screen.getMenu().getCarried();
            graphics.renderItem(carried, mouseX - 8, mouseY - 8);
            graphics.renderItemDecorations(Minecraft.getInstance().font, carried, mouseX - 8, mouseY - 8);
        }
    }

    public boolean mousePressed(double mouseX, double mouseY, int button, boolean shiftDown) {
        BackpackWindow window = topAt(mouseX, mouseY);
        if (window == null) {
            return false;
        }
        bringToFront(window);
        if (button == 0 && window.closeContains(mouseX, mouseY)) {
            window.setOpen(false);
            state.save(window);
            return true;
        }
        if (button == 0 && window.titleContains(mouseX, mouseY)) {
            dragging = window;
            dragOffsetX = (int) mouseX - window.x();
            dragOffsetY = (int) mouseY - window.y();
            return true;
        }
        int slot = window.slotAt(mouseX, mouseY);
        if (slot >= 0 && (button == 0 || button == 1)) {
            window.clickSlot(slot, button, shiftDown);
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (dragging == null || button != 0) {
            return topAt(mouseX, mouseY) != null;
        }
        dragging.moveTo((int) mouseX - dragOffsetX, (int) mouseY - dragOffsetY);
        dragging.clampToScreen(screenWidth, screenHeight);
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null && button == 0) {
            BackpackWindow finished = dragging;
            dragging = null;
            state.save(finished);
            return true;
        }
        return topAt(mouseX, mouseY) != null;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        BackpackWindow window = topAt(mouseX, mouseY);
        if (window == null) {
            return false;
        }
        window.scroll(delta);
        state.save(window);
        return true;
    }

    public boolean blocksPoint(double mouseX, double mouseY) {
        return topAt(mouseX, mouseY) != null;
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

    private void bringToFront(BackpackWindow window) {
        window.setZIndex(nextZ++);
        state.save(window);
    }

    private BackpackWindow topAt(double x, double y) {
        return openWindows().stream()
                .filter(window -> window.contains(x, y))
                .max(Comparator.comparingInt(BackpackWindow::zIndex))
                .orElse(null);
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
}

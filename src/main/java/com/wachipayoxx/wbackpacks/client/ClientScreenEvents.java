package com.wachipayoxx.wbackpacks.client;

import com.wachipayoxx.wbackpacks.WBackpacks;
import com.wachipayoxx.wbackpacks.backpack.BackpackAccess;
import com.wachipayoxx.wbackpacks.network.RequestOpenBackpackPayload;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = WBackpacks.MOD_ID, value = Dist.CLIENT)
public final class ClientScreenEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderOnTop(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen) {
            BackpackWindowManager.get().render(screen, event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        } else {
            BackpackWindowManager.get().resetCursor();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }

        BackpackWindowManager manager = BackpackWindowManager.get();
        if (manager.mousePressed(screen, event.getMouseX(), event.getMouseY(), event.getButton(), Screen.hasShiftDown())) {
            event.setCanceled(true);
            return;
        }

        Slot slot = screen.getSlotUnderMouse();
        int menuSlot = slot == null ? -1 : screen.getMenu().slots.indexOf(slot);

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && Screen.hasShiftDown()
                && screen.getMenu().getCarried().isEmpty()
                && menuSlot >= 0
                && manager.tryQuickMoveFromMenu(screen, slot, menuSlot)) {
            event.setCanceled(true);
            return;
        }

        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || !Screen.hasControlDown()) {
            return;
        }
        if (slot == null || menuSlot < 0 || !BackpackAccess.isBackpack(slot.getItem())) {
            return;
        }
        if (screen.getMinecraft().player == null || slot.container != screen.getMinecraft().player.getInventory()) {
            return;
        }

        PacketDistributor.sendToServer(new RequestOpenBackpackPayload(menuSlot));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen
                && BackpackWindowManager.get().mouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton(), screen.width, screen.height)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?>
                && BackpackWindowManager.get().mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?>
                && BackpackWindowManager.get().mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaY(), Screen.hasShiftDown())) {
            event.setCanceled(true);
        }
    }

    private ClientScreenEvents() {
    }
}

package com.wachipayoxx.wbackpacks.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

final class BackpackTransferHelper {
    static ItemStack insertIntoBackpack(IItemHandler handler, int capacity, ItemStack input) {
        ItemStack remainder = input.copy();
        int slots = Math.min(capacity, handler.getSlots());
        for (int slot = 0; slot < slots && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, false);
        }
        return remainder;
    }

    static ItemStack insertIntoExternalContainer(ServerPlayer player, ItemStack input) {
        ItemStack remainder = input.copy();
        if (remainder.isEmpty()) {
            return ItemStack.EMPTY;
        }

        for (Slot target : player.containerMenu.slots) {
            if (remainder.isEmpty()) {
                break;
            }
            if (target.container == player.getInventory() || !target.mayPlace(remainder)) {
                continue;
            }
            ItemStack stored = target.getItem();
            if (stored.isEmpty() || !ItemStack.isSameItemSameComponents(stored, remainder)) {
                continue;
            }

            int max = Math.min(target.getMaxStackSize(remainder), remainder.getMaxStackSize());
            int room = max - stored.getCount();
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, remainder.getCount());
            stored.grow(moved);
            remainder.shrink(moved);
            target.setChanged();
        }

        for (Slot target : player.containerMenu.slots) {
            if (remainder.isEmpty()) {
                break;
            }
            if (target.container == player.getInventory() || !target.getItem().isEmpty() || !target.mayPlace(remainder)) {
                continue;
            }

            int moved = Math.min(remainder.getCount(), target.getMaxStackSize(remainder));
            if (moved <= 0) {
                continue;
            }
            target.set(remainder.copyWithCount(moved));
            target.setChanged();
            remainder.shrink(moved);
        }

        return remainder;
    }

    private BackpackTransferHelper() {
    }
}

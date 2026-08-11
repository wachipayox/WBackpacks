package com.wachipayoxx.wbackpacks.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ComponentItemHandler;

public final class BackpackItemHandler extends ComponentItemHandler {
    public static final int STORAGE_SLOTS = 256;

    public BackpackItemHandler(ItemStack stack) {
        super(stack, DataComponents.CONTAINER, STORAGE_SLOTS);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !(stack.getItem() instanceof BackpackItem) && super.isItemValid(slot, stack);
    }
}

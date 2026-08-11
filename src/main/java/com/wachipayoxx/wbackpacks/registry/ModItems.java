package com.wachipayoxx.wbackpacks.registry;

import com.wachipayoxx.wbackpacks.WBackpacks;
import com.wachipayoxx.wbackpacks.item.BackpackItem;
import com.wachipayoxx.wbackpacks.item.BackpackItemHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WBackpacks.MOD_ID);

    public static final DeferredItem<BackpackItem> BACKPACK = ITEMS.registerItem("backpack", properties ->
            new BackpackItem(properties
                    .stacksTo(1)
                    .component(DataComponents.CONTAINER, ItemContainerContents.EMPTY)));

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.ItemHandler.ITEM,
                (stack, context) -> new BackpackItemHandler(stack), BACKPACK);
    }

    private ModItems() {
    }
}

package com.wachipayoxx.wbackpacks;

import com.mojang.logging.LogUtils;
import com.wachipayoxx.wbackpacks.config.WBackpacksConfig;
import com.wachipayoxx.wbackpacks.network.ModNetwork;
import com.wachipayoxx.wbackpacks.registry.ModDataComponents;
import com.wachipayoxx.wbackpacks.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(WBackpacks.MOD_ID)
public final class WBackpacks {
    public static final String MOD_ID = "w_backpacks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WBackpacks(IEventBus modBus, ModContainer modContainer) {
        ModDataComponents.COMPONENTS.register(modBus);
        ModItems.ITEMS.register(modBus);

        modBus.addListener(ModItems::registerCapabilities);
        modBus.addListener(ModNetwork::register);
        modBus.addListener(this::addCreativeTabContents);

        modContainer.registerConfig(ModConfig.Type.COMMON, WBackpacksConfig.SPEC);
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.BACKPACK);
        }
    }
}

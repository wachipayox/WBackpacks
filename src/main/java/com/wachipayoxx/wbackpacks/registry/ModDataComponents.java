package com.wachipayoxx.wbackpacks.registry;

import com.mojang.serialization.Codec;
import com.wachipayoxx.wbackpacks.WBackpacks;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, WBackpacks.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> BACKPACK_ID =
            COMPONENTS.registerComponentType("backpack_id", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private ModDataComponents() {
    }
}

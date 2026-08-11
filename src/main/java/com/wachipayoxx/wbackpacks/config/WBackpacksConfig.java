package com.wachipayoxx.wbackpacks.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class WBackpacksConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue BACKPACK_CAPACITY = BUILDER
            .comment("Number of accessible slots in the base backpack. Existing contents above this limit are preserved if the value is reduced.")
            .defineInRange("backpackCapacity", 27, 1, 256);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private WBackpacksConfig() {
    }
}

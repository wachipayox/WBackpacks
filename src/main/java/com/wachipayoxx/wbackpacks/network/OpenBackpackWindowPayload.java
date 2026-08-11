package com.wachipayoxx.wbackpacks.network;

import com.wachipayoxx.wbackpacks.WBackpacks;
import com.wachipayoxx.wbackpacks.client.BackpackWindowManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenBackpackWindowPayload(String backpackId, int capacity) implements CustomPacketPayload {
    public static final Type<OpenBackpackWindowPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(WBackpacks.MOD_ID, "open_window"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBackpackWindowPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.backpackId);
                buf.writeVarInt(payload.capacity);
            },
            buf -> new OpenBackpackWindowPayload(buf.readUtf(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenBackpackWindowPayload payload, IPayloadContext context) {
        BackpackWindowManager.get().open(payload.backpackId, payload.capacity);
    }
}

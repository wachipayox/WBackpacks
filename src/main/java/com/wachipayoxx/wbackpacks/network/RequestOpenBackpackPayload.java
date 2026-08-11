package com.wachipayoxx.wbackpacks.network;

import com.wachipayoxx.wbackpacks.WBackpacks;
import com.wachipayoxx.wbackpacks.backpack.BackpackAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestOpenBackpackPayload(int menuSlot) implements CustomPacketPayload {
    public static final Type<RequestOpenBackpackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(WBackpacks.MOD_ID, "request_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestOpenBackpackPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.menuSlot),
            buf -> new RequestOpenBackpackPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestOpenBackpackPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (payload.menuSlot < 0 || payload.menuSlot >= player.containerMenu.slots.size()) {
            return;
        }

        Slot slot = player.containerMenu.getSlot(payload.menuSlot);
        if (slot.container != player.getInventory()) {
            return;
        }

        ItemStack stack = slot.getItem();
        if (!BackpackAccess.isBackpack(stack)) {
            return;
        }

        String id = BackpackAccess.ensureUniqueId(player, stack);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        PacketDistributor.sendToPlayer(player, new OpenBackpackWindowPayload(id, BackpackAccess.configuredCapacity()));
    }
}

package com.wachipayoxx.wbackpacks.network;

import com.wachipayoxx.wbackpacks.WBackpacks;
import com.wachipayoxx.wbackpacks.backpack.BackpackAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BackpackSlotClickPayload(
        String backpackId,
        int slot,
        int button,
        boolean quickMove,
        boolean quickMoveToContainer) implements CustomPacketPayload {
    public static final Type<BackpackSlotClickPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(WBackpacks.MOD_ID, "slot_click"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackSlotClickPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.backpackId);
                buf.writeVarInt(payload.slot);
                buf.writeByte(payload.button);
                buf.writeBoolean(payload.quickMove);
                buf.writeBoolean(payload.quickMoveToContainer);
            },
            buf -> new BackpackSlotClickPayload(
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readByte(),
                    buf.readBoolean(),
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BackpackSlotClickPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        int capacity = BackpackAccess.configuredCapacity();
        if (payload.slot < 0 || payload.slot >= capacity || (payload.button != 0 && payload.button != 1)) {
            return;
        }

        ItemStack backpack = BackpackAccess.findOwned(player, payload.backpackId);
        if (backpack.isEmpty()) {
            return;
        }
        IItemHandler handler = BackpackAccess.handler(backpack);
        if (handler == null) {
            return;
        }

        if (payload.quickMove) {
            if (payload.quickMoveToContainer && player.containerMenu != player.inventoryMenu) {
                quickMoveToContainer(player, handler, payload.slot);
            } else {
                quickMoveToPlayer(player, handler, payload.slot);
            }
        } else {
            clickWithCarried(player, handler, payload.slot, payload.button);
        }

        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static void quickMoveToPlayer(ServerPlayer player, IItemHandler handler, int slot) {
        ItemStack simulated = handler.extractItem(slot, Integer.MAX_VALUE, true);
        if (simulated.isEmpty()) {
            return;
        }
        ItemStack remainder = simulated.copy();
        player.getInventory().add(remainder);
        int moved = simulated.getCount() - remainder.getCount();
        if (moved > 0) {
            handler.extractItem(slot, moved, false);
        }
    }

    private static void quickMoveToContainer(ServerPlayer player, IItemHandler handler, int slot) {
        ItemStack simulated = handler.extractItem(slot, Integer.MAX_VALUE, true);
        if (simulated.isEmpty()) {
            return;
        }
        ItemStack remainder = BackpackTransferHelper.insertIntoExternalContainer(player, simulated);
        int moved = simulated.getCount() - remainder.getCount();
        if (moved > 0) {
            handler.extractItem(slot, moved, false);
        }
    }

    private static void clickWithCarried(ServerPlayer player, IItemHandler handler, int slot, int button) {
        ItemStack carried = player.containerMenu.getCarried();
        ItemStack stored = handler.getStackInSlot(slot);

        if (carried.isEmpty()) {
            if (stored.isEmpty()) {
                return;
            }
            int amount = button == 0 ? stored.getCount() : (stored.getCount() + 1) / 2;
            player.containerMenu.setCarried(handler.extractItem(slot, amount, false));
            return;
        }

        int requested = button == 0 ? carried.getCount() : 1;
        ItemStack toInsert = carried.copyWithCount(requested);
        ItemStack remainder = handler.insertItem(slot, toInsert, false);
        int inserted = requested - remainder.getCount();
        if (inserted > 0) {
            carried.shrink(inserted);
            player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            return;
        }

        if (button == 0 && !stored.isEmpty() && handler.isItemValid(slot, carried)
                && carried.getCount() <= Math.min(handler.getSlotLimit(slot), carried.getMaxStackSize())) {
            ItemStack old = handler.extractItem(slot, stored.getCount(), false);
            ItemStack failed = handler.insertItem(slot, carried, false);
            if (failed.isEmpty()) {
                player.containerMenu.setCarried(old);
            } else {
                handler.insertItem(slot, old, false);
            }
        }
    }
}

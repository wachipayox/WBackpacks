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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MenuSlotQuickMovePayload(String backpackId, int menuSlot) implements CustomPacketPayload {
    public static final Type<MenuSlotQuickMovePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(WBackpacks.MOD_ID, "menu_slot_quick_move"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MenuSlotQuickMovePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.backpackId);
                buf.writeVarInt(payload.menuSlot);
            },
            buf -> new MenuSlotQuickMovePayload(buf.readUtf(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MenuSlotQuickMovePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (payload.menuSlot < 0 || payload.menuSlot >= player.containerMenu.slots.size()) {
            return;
        }

        Slot source = player.containerMenu.slots.get(payload.menuSlot);
        if (!source.hasItem() || !source.mayPickup(player)) {
            return;
        }

        boolean sourceIsExternal = source.container != player.getInventory();
        ItemStack backpack = BackpackAccess.findOwned(player, payload.backpackId);
        if (backpack.isEmpty()) {
            if (sourceIsExternal) {
                fallbackToVanillaQuickMove(player, payload.menuSlot);
            }
            return;
        }
        IItemHandler handler = BackpackAccess.handler(backpack);
        if (handler == null) {
            if (sourceIsExternal) {
                fallbackToVanillaQuickMove(player, payload.menuSlot);
            }
            return;
        }

        ItemStack sourceStack = source.getItem();
        if (sourceStack == backpack) {
            return;
        }

        ItemStack remainder = sourceStack.copy();

        // Backpacks are deliberately not nested. A backpack coming from a chest can still fall
        // back to the player's inventory, but it will never be inserted into another backpack.
        if (!BackpackAccess.isBackpack(sourceStack)) {
            remainder = BackpackTransferHelper.insertIntoBackpack(
                    handler,
                    BackpackAccess.configuredCapacity(),
                    remainder);
        }

        if (sourceIsExternal && !remainder.isEmpty()) {
            player.getInventory().add(remainder);
        }

        int moved = sourceStack.getCount() - remainder.getCount();
        if (moved <= 0) {
            return;
        }

        ItemStack taken = sourceStack.copyWithCount(moved);
        sourceStack.shrink(moved);
        if (sourceStack.isEmpty()) {
            source.set(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        source.onTake(player, taken);

        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static void fallbackToVanillaQuickMove(ServerPlayer player, int menuSlot) {
        player.containerMenu.quickMoveStack(player, menuSlot);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}

package com.wachipayoxx.wbackpacks.backpack;

import com.wachipayoxx.wbackpacks.config.WBackpacksConfig;
import com.wachipayoxx.wbackpacks.item.BackpackItem;
import com.wachipayoxx.wbackpacks.registry.ModDataComponents;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public final class BackpackAccess {
    public static boolean isBackpack(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BackpackItem;
    }

    public static Optional<String> id(ItemStack stack) {
        if (!isBackpack(stack)) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.get(ModDataComponents.BACKPACK_ID.get()));
    }

    public static String ensureUniqueId(ServerPlayer player, ItemStack target) {
        String id = target.get(ModDataComponents.BACKPACK_ID.get());
        if (id == null || id.isBlank() || hasDuplicate(player, target, id)) {
            id = UUID.randomUUID().toString();
            target.set(ModDataComponents.BACKPACK_ID.get(), id);
        }
        return id;
    }

    private static boolean hasDuplicate(Player player, ItemStack target, String id) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack other = player.getInventory().getItem(i);
            if (other != target && isBackpack(other) && id.equals(other.get(ModDataComponents.BACKPACK_ID.get()))) {
                return true;
            }
        }
        return false;
    }

    public static ItemStack findOwned(Player player, String id) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isBackpack(stack) && id.equals(stack.get(ModDataComponents.BACKPACK_ID.get()))) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static IItemHandler handler(ItemStack backpack) {
        return backpack.getCapability(Capabilities.ItemHandler.ITEM);
    }

    public static int configuredCapacity() {
        return WBackpacksConfig.BACKPACK_CAPACITY.getAsInt();
    }

    private BackpackAccess() {
    }
}

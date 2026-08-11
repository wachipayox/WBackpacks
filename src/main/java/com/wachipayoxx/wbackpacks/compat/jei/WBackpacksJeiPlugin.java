package com.wachipayoxx.wbackpacks.compat.jei;

import com.wachipayoxx.wbackpacks.WBackpacks;
import com.wachipayoxx.wbackpacks.client.BackpackWindowManager;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class WBackpacksJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WBackpacks.MOD_ID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public java.util.Collection<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas() {
                return BackpackWindowManager.get().exclusionAreas();
            }
        });
    }
}

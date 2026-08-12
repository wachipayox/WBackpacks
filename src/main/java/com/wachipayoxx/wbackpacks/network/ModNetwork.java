package com.wachipayoxx.wbackpacks.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToServer(RequestOpenBackpackPayload.TYPE, RequestOpenBackpackPayload.STREAM_CODEC, RequestOpenBackpackPayload::handle);
        registrar.playToClient(OpenBackpackWindowPayload.TYPE, OpenBackpackWindowPayload.STREAM_CODEC, OpenBackpackWindowPayload::handle);
        registrar.playToServer(BackpackSlotClickPayload.TYPE, BackpackSlotClickPayload.STREAM_CODEC, BackpackSlotClickPayload::handle);
        registrar.playToServer(MenuSlotQuickMovePayload.TYPE, MenuSlotQuickMovePayload.STREAM_CODEC, MenuSlotQuickMovePayload::handle);
    }

    private ModNetwork() {
    }
}

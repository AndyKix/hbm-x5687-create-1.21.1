package com.hbm.port.client;

import com.hbm.port.HbmPort;
import com.hbm.port.storage.CrateContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = HbmPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PortClient {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(HbmPort.IRON_FURNACE_MENU.get(), IronFurnaceScreen::new);
        event.register(CrateContent.IRON_MENU.get(), StorageCrateScreen::new);
        event.register(CrateContent.STEEL_MENU.get(), StorageCrateScreen::new);
    }
}

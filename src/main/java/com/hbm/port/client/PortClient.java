package com.hbm.port.client;

import com.hbm.port.HbmPort;
import com.hbm.port.storage.CrateContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import com.hbm.port.material.RawMaterials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = HbmPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PortClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(RawMaterials.item("gem_alexandrite"),
                ResourceLocation.fromNamespaceAndPath("hbm", "block_light"), (stack, level, entity, seed) -> {
                    var mc = Minecraft.getInstance();
                    return mc.player == null || mc.level == null ? 0 : mc.level.getBrightness(LightLayer.BLOCK, mc.player.blockPosition()) / 15F;
                }));
    }
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(HbmPort.IRON_FURNACE_MENU.get(), IronFurnaceScreen::new);
        event.register(CrateContent.IRON_MENU.get(), StorageCrateScreen::new);
        event.register(CrateContent.STEEL_MENU.get(), StorageCrateScreen::new);
    }
}

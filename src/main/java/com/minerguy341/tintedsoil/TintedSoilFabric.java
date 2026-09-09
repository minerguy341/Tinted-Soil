package com.minerguy341.tintedsoil;

//? if fabric {
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;

public class TintedSoilFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        TintedSoilBlocks.register(
                (id, block) -> Registry.register(BuiltInRegistries.BLOCK, id, block),
                (id, item) -> Registry.register(BuiltInRegistries.ITEM, id, item));

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.NATURAL_BLOCKS).register(entries -> {
            entries.accept(TintedSoilBlocks.TINTED_GRASS_BLOCK_ITEM);
            entries.accept(TintedSoilBlocks.TINTED_SOIL_ITEM);
            entries.accept(TintedSoilBlocks.TINTED_COARSE_SOIL_ITEM);
        });

        TintedSoil.onCommonSetup();
        TintedSoil.LOGGER.info("Tinted Soil ready for Minecraft {} (Fabric)", TintedSoil.MINECRAFT);
    }
}
//?}

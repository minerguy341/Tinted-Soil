package com.minerguy341.tintedsoil;

//? if neoforge {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;

@Mod(TintedSoil.MOD_ID)
public class TintedSoilNeoForge {
    public TintedSoilNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::onRegister);
        modEventBus.addListener(this::onBuildCreativeTab);
        modEventBus.addListener(this::onCommonSetup);
    }

    private void onRegister(RegisterEvent event) {
        // NeoForge freezes the vanilla registries, so registration has to run from this event
        // rather than at class-load time the way the Fabric entrypoint does.
        event.register(Registries.BLOCK, helper ->
                TintedSoilBlocks.register(helper::register, (id, item) -> { }));
        event.register(Registries.ITEM, helper ->
                TintedSoilBlocks.register((id, block) -> { }, helper::register));
    }

    private void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(TintedSoilBlocks.TINTED_GRASS_BLOCK_ITEM);
            event.accept(TintedSoilBlocks.TINTED_DIRT_ITEM);
            event.accept(TintedSoilBlocks.TINTED_COARSE_DIRT_ITEM);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // The tool-interaction maps are shared mutable state, and common setup runs in
        // parallel across mods, so the writes go on the main thread.
        event.enqueueWork(TintedSoil::onCommonSetup);
        TintedSoil.LOGGER.info("Tinted Soil ready for Minecraft {} (NeoForge)", TintedSoil.MINECRAFT);
    }
}
*///?}

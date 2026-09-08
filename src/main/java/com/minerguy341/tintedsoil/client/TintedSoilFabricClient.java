package com.minerguy341.tintedsoil.client;

//? if fabric {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;

public class TintedSoilFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TintedSoilColors.registerBlockColors(ColorProviderRegistry.BLOCK::register);
        LegacyItemColors.registerFabric();
    }
}
//?}

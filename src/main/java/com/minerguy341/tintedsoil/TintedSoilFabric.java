package com.minerguy341.tintedsoil;

//? if fabric {
import net.fabricmc.api.ModInitializer;

/**
 * Registers nothing on purpose.
 *
 * <p>Tinted Soil adds no blocks, items, tags or worldgen: it retints the soil blocks that
 * are already there, entirely on the client. Everything the mod does starts from
 * {@link com.minerguy341.tintedsoil.client.TintedSoilFabricClient}; this exists so the
 * mod is loadable on a server, where it does nothing at all.
 */
public class TintedSoilFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        TintedSoil.LOGGER.info("Tinted Soil ready for Minecraft {} (Fabric)", TintedSoil.MINECRAFT);
    }
}
//?}

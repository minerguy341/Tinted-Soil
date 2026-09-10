package com.minerguy341.tintedsoil;

//? if neoforge {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/^*
 * Registers nothing on purpose.
 *
 * <p>Tinted Soil adds no blocks, items, tags or worldgen: it retints the soil blocks that
 * are already there, entirely on the client. Everything the mod does starts from
 * {@link com.minerguy341.tintedsoil.client.TintedSoilNeoForgeClient}; this exists so the
 * mod has a mod-side entrypoint on a dedicated server, where it does nothing at all.
 *^/
@Mod(TintedSoil.MOD_ID)
public class TintedSoilNeoForge {
    public TintedSoilNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        TintedSoil.LOGGER.info("Tinted Soil ready for Minecraft {} (NeoForge)", TintedSoil.MINECRAFT);
    }
}
*///?}

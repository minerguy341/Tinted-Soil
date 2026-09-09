package com.minerguy341.tintedsoil.client;

//? if neoforge {
/*import com.minerguy341.tintedsoil.TintedSoil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/^*
 * Client-only mod entrypoint.
 *
 * <p>A dist-scoped {@code @Mod} class is used rather than {@code @EventBusSubscriber}
 * because that annotation's {@code bus} attribute defaults to the game bus, was deprecated
 * in NeoForge 21.1, and was removed outright in 21.8. Registering on the mod bus explicitly
 * behaves identically on both.
 *^/
@Mod(value = TintedSoil.MOD_ID, dist = Dist.CLIENT)
public final class TintedSoilNeoForgeClient {
    public TintedSoilNeoForgeClient(IEventBus modEventBus) {
        modEventBus.addListener(RegisterColorHandlersEvent.Block.class,
                event -> TintedSoilColors.registerBlockColors(event::register));
        LegacyItemColors.registerNeoForge(modEventBus);
        SoilSpriteSources.register();
    }
}
*///?}

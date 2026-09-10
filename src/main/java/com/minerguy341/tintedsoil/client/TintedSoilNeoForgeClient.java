package com.minerguy341.tintedsoil.client;

//? if neoforge {
/*import com.minerguy341.tintedsoil.TintedSoil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/^*
 * Client-only mod entrypoint, and the only thing that has to happen before the first
 * resource reload: the sprite source type has to be known before atlas definitions are
 * parsed, because an unrecognised source type there is a hard error.
 *
 * <p>A dist-scoped {@code @Mod} class is used rather than {@code @EventBusSubscriber}
 * because that annotation's {@code bus} attribute defaults to the game bus, was deprecated
 * in NeoForge 21.1, and was removed outright in 21.8.
 *^/
@Mod(value = TintedSoil.MOD_ID, dist = Dist.CLIENT)
public final class TintedSoilNeoForgeClient {
    public TintedSoilNeoForgeClient(IEventBus modEventBus) {
        SoilSpriteSources.register();
    }
}
*///?}

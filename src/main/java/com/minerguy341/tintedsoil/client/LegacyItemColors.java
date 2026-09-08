package com.minerguy341.tintedsoil.client;

/**
 * Inventory tinting for versions before item model definitions existed.
 *
 * <p>This lives in its own file because Stonecutter comment blocks cannot nest: the loader
 * entrypoints are already wrapped in a loader condition, so a version condition inside them
 * would produce nested block comments. Keeping the version gate at the top level of an
 * otherwise unconditional file avoids that.
 *
 * <p>On 1.21.4+ the equivalent lives in {@code assets/tintedsoil/items/*.json} instead:
 * Minecraft deleted {@code ItemColor} and NeoForge replaced
 * {@code RegisterColorHandlersEvent.Item} with {@code ItemTintSources}.
 */
public final class LegacyItemColors {
    private LegacyItemColors() {
    }

    public static void registerFabric() {
        //? if fabric && <1.21.2 {
        TintedSoilColors.registerItemColors(
                net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM::register);
        //?}
    }

    /** Takes {@code Object} so the signature stays valid on loaders/versions without the event. */
    public static void registerNeoForge(Object modEventBus) {
        //? if neoforge && <1.21.2 {
        /*((net.neoforged.bus.api.IEventBus) modEventBus).addListener(
                net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item.class,
                event -> TintedSoilColors.registerItemColors(event::register));
        *///?}
    }
}

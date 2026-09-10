package com.minerguy341.tintedsoil.client;

//? if fabric {
import net.fabricmc.api.ClientModInitializer;

/**
 * The only thing that has to happen before the first resource reload.
 *
 * <p>Model replacement, render layers and tints are all reached through mixins rather
 * than through either loader's API, so this and its NeoForge twin are down to one line:
 * the sprite source type has to be known before atlas definitions are parsed, because an
 * unrecognised source type there is a hard error.
 */
public class TintedSoilFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SoilSpriteSources.register();
    }
}
//?}

package com.minerguy341.tintedsoil.client;

//? if >=1.21.4 {
/*import com.minerguy341.tintedsoil.TintedSoil;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.Constant;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/^*
 * Gives soil items the tint their retinted models now need.
 *
 * <p>From 1.21.4 an item's tints are part of its model definition rather than something
 * code registers, and vanilla's `items/dirt.json` declares none -- it never needed any.
 * Its model is the same one the block uses, though, so once that model samples a
 * greyscale and carries tint index 1, an untouched definition renders dirt in the
 * inventory as pale grey. This walks the loaded definitions and adds the missing tint.
 *
 * <p>Index 0 is padded with plain white rather than a grass source: a bare soil model
 * never references it, and a grass block's definition already supplies its own.
 *^/
public final class SoilItemTints {
    private SoilItemTints() {
    }

    /^* A soil colour that follows the texture the block is actually wearing. ^/
    private record SoilTint(Block block) implements ItemTintSource {
        // Never serialised -- these are added after the definitions are parsed and nothing
        // re-encodes them -- but a codec that round-trips honestly costs four lines and
        // beats handing back some other source's codec.
        private static final MapCodec<SoilTint> CODEC = ResourceLocation.CODEC
                .xmap(id -> new SoilTint(TintedSoil.block(id)),
                        tint -> BuiltInRegistries.BLOCK.getKey(tint.block()))
                .fieldOf("block");

        @Override
        public int calculate(ItemStack stack, ClientLevel level, LivingEntity entity) {
            return 0xFF000000 | TintedSoilColors.itemTint(this.block);
        }

        @Override
        public MapCodec<? extends ItemTintSource> type() {
            return CODEC;
        }
    }

    /^* @return the loaded item definitions, with soil items tinted ^/
    public static ClientItemInfoLoader.LoadedClientInfos retint(
            ClientItemInfoLoader.LoadedClientInfos infos, SoilDefinitions definitions) {
        Map<ResourceLocation, ClientItem> contents = infos.contents();
        Map<ResourceLocation, ClientItem> tinted = null;

        for (ResourceLocation blockId : definitions.blocks().keySet()) {
            Block block = TintedSoil.block(blockId);
            if (block == null) {
                continue;   // the mod that adds it is not installed
            }
            Item item = block.asItem();
            if (item == Items.AIR) {
                continue;   // a block with no item of its own has no icon to fix
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            ClientItem existing = contents.get(itemId);
            if (existing == null
                    || !(existing.model() instanceof BlockModelWrapper.Unbaked wrapper)) {
                continue;   // something other than a plain block model; leave it alone
            }

            List<ItemTintSource> tints = new ArrayList<>(wrapper.tints());
            while (tints.size() <= TintedSoilColors.SOIL_TINT_INDEX) {
                tints.add(new Constant(-1));
            }
            tints.set(TintedSoilColors.SOIL_TINT_INDEX, new SoilTint(block));

            if (tinted == null) {
                tinted = new HashMap<>(contents);
            }
            tinted.put(itemId, new ClientItem(
                    new BlockModelWrapper.Unbaked(wrapper.model(), List.copyOf(tints)),
                    existing.properties(), existing.registrySwapper()));
        }

        return tinted == null ? infos : new ClientItemInfoLoader.LoadedClientInfos(tinted);
    }
}
*///?}

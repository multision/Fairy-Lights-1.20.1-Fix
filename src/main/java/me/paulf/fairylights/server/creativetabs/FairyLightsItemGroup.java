package me.paulf.fairylights.server.creativetabs;

import me.paulf.fairylights.FairyLights;
import me.paulf.fairylights.server.block.FLBlocks;
import me.paulf.fairylights.server.block.LightBlock;
import me.paulf.fairylights.server.item.*;
import me.paulf.fairylights.server.item.crafting.FLCraftingRecipes;
import me.paulf.fairylights.util.styledstring.StyledString;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

public final class FairyLightsItemGroup
{
    public FairyLightsItemGroup()
    {
        super();
    }

    public static final DeferredRegister<CreativeModeTab> TAB_REG = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FairyLights.ID);

    public static final RegistryObject<CreativeModeTab> GENERAL = TAB_REG.register("general", () -> new CreativeModeTab.Builder(CreativeModeTab.Row.TOP, 1)
                                                                                                      .icon(() -> new ItemStack(FLItems.HANGING_LIGHTS.get()))
                                                                                                      .title(Component.literal("FairyLights")).displayItems((config, output) -> {


          for (final DyeColor color : DyeColor.values())
          {
              ItemStack hangingLights = FLCraftingRecipes.makeHangingLights(new ItemStack(FLItems.HANGING_LIGHTS.get()), color);
              
              // Add captured color information to preserve it even if NBT is stripped
              if (hangingLights.hasTag()) {
                  CompoundTag tag = hangingLights.getTag();
                  tag.putString("CapturedColor", color.getName());
                  // Store the RGB value for this dye color
                  int rgb = getDyeColorRGB(color);
                  tag.putInt("CapturedRGB", rgb);
              }
              
              output.accept(hangingLights);
          }

          for (final DyeColor color : DyeColor.values())
          {
              final ItemStack stack = new ItemStack(FLItems.PENNANT_BUNTING.get());
              DyeableItem.setColor(stack, color);
              ItemStack pennantBunting = FLCraftingRecipes.makePennant(stack, color);
              
              // Add captured color information to preserve it even if NBT is stripped
              if (pennantBunting.hasTag()) {
                  CompoundTag tag = pennantBunting.getTag();
                  tag.putString("CapturedColor", color.getName());
                  // Store the RGB value for this dye color
                  int rgb = getDyeColorRGB(color);
                  tag.putInt("CapturedRGB", rgb);
              }
              
              output.accept(pennantBunting);
          }
          output.acceptAll(generateCollection(FLItems.TINSEL.get()));

          final ItemStack bunting = new ItemStack(FLItems.LETTER_BUNTING.get(), 1);
          bunting.getOrCreateTag().put("text", StyledString.serialize(new StyledString()));
          output.accept(bunting);
          output.accept(new ItemStack(FLItems.GARLAND.get()));

          output.acceptAll(generateCollection(FLItems.TRIANGLE_PENNANT.get()));
          output.acceptAll(generateCollection(FLItems.SPEARHEAD_PENNANT.get()));
          output.acceptAll(generateCollection(FLItems.SWALLOWTAIL_PENNANT.get()));
          output.acceptAll(generateCollection(FLItems.SQUARE_PENNANT.get()));

          output.acceptAll(generateCollection(FLItems.FAIRY_LIGHT.get()));
          output.acceptAll(generateCollection(FLItems.PAPER_LANTERN.get()));
          output.acceptAll(generateCollection(FLItems.ORB_LANTERN.get()));
          output.acceptAll(generateCollection(FLItems.FLOWER_LIGHT.get()));
          output.acceptAll(generateCollection(FLItems.CANDLE_LANTERN_LIGHT.get()));
          output.acceptAll(generateCollection(FLItems.OIL_LANTERN_LIGHT.get()));
          output.acceptAll(generateCollection(FLItems.JACK_O_LANTERN.get()));
          output.acceptAll(generateCollection(FLItems.SKULL_LIGHT.get()));
          output.acceptAll(generateCollection(FLItems.GHOST_LIGHT.get()));
          output.acceptAll(generateCollection(FLItems.SPIDER_LIGHT.get()));
          output.acceptAll(generateCollection(FLItems.WITCH_LIGHT.get()));
          output.acceptAll(generateCollection(FLItems.SNOWFLAKE_LIGHT.get()));
          output.acceptAll(generateCollection(FLItems.HEART_LIGHT.get()));
          output.acceptAll(generateCollection(FLItems.MOON_LIGHT.get()));
          output.acceptAll(generateCollection(FLItems.STAR_LIGHT.get()));
          output.acceptAll(generateCollection(FLItems.ICICLE_LIGHTS.get()));
          output.acceptAll(generateCollection(FLItems.METEOR_LIGHT.get()));

          output.accept(new ItemStack(FLItems.OIL_LANTERN.get()));
          output.accept(new ItemStack(FLItems.CANDLE_LANTERN.get()));
          output.accept(new ItemStack(FLItems.INCANDESCENT_LIGHT.get()));
      }).build());

    private static Collection<ItemStack> generateCollection(final @NotNull Item item)
    {
        final List<ItemStack> stacks = new ArrayList<>();
        for (final DyeColor color : DyeColor.values())
        {
            stacks.add(DyeableItem.setColor(new ItemStack(item), color));
        }
        return stacks;
    }

    private static int getDyeColorRGB(DyeColor color) {
        switch (color) {
            case WHITE: return 0xFFFFFF;
            case ORANGE: return 0xFFA500;
            case MAGENTA: return 0xFF00FF;
            case LIGHT_BLUE: return 0x87CEEB;
            case YELLOW: return 0xFFFF00;
            case LIME: return 0x99CC00;
            case PINK: return 0xFFC0CB;
            case GRAY: return 0x808080;
            case LIGHT_GRAY: return 0xD3D3D3;
            case CYAN: return 0x00FFFF;
            case PURPLE: return 0x800080;
            case BLUE: return 0x0000FF;
            case BROWN: return 0xA52A2A;
            case GREEN: return 0x00FF00;
            case RED: return 0xFF0000;
            case BLACK: return 0x000000;
            default: return 0xFFFFFF; // Fallback
        }
    }
}

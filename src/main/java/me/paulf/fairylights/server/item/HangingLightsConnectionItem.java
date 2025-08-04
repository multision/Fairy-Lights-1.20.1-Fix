package me.paulf.fairylights.server.item;

import me.paulf.fairylights.FairyLights;
import me.paulf.fairylights.server.connection.ConnectionTypes;
import me.paulf.fairylights.server.item.crafting.FLCraftingRecipes;
import me.paulf.fairylights.server.string.StringType;
import me.paulf.fairylights.server.string.StringTypes;
import me.paulf.fairylights.util.RegistryObjects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public final class HangingLightsConnectionItem extends ConnectionItem {
    private static final Logger LOGGER = LogManager.getLogger();
    
    public HangingLightsConnectionItem(final Properties properties) {
        super(properties, ConnectionTypes.HANGING_LIGHTS);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // Proactively capture color information before NBT might be stripped
        ItemStack stack = context.getItemInHand();
        if (stack.hasTag() && stack.getTag().contains("pattern", Tag.TAG_LIST)) {
            // NBT is present, capture the color information for future use
            captureColorInformation(stack);
        }
        
        // Let the parent class handle NBT validation and repair
        return super.useOn(context);
    }

    /**
     * Captures color information from an item stack and stores it for future use.
     * This should be called when the item still has its original NBT data.
     */
    private void captureColorInformation(ItemStack stack) {
        if (!stack.hasTag()) return;
        
        CompoundTag tag = stack.getTag();
        if (!tag.contains("pattern", Tag.TAG_LIST)) return;
        
        ListTag pattern = tag.getList("pattern", Tag.TAG_COMPOUND);
        if (pattern.size() == 0) return;
        
        // Try to extract color from the first light in the pattern
        CompoundTag firstLight = pattern.getCompound(0);
        if (firstLight.contains("tag", Tag.TAG_COMPOUND)) {
            CompoundTag lightTag = firstLight.getCompound("tag");
            if (lightTag.contains("CustomColor", Tag.TAG_INT)) {
                int color = lightTag.getInt("CustomColor");
                DyeColor dyeColor = getClosestDyeColor(color);
                
                // Store the color information in a way that's less likely to be stripped
                tag.putString("CapturedColor", dyeColor.getName());
                tag.putInt("CapturedRGB", color);
                
                LOGGER.debug("Captured color information: {} (RGB: {})", dyeColor.getName(), color);
            }
        }
    }

    @Override
    public ItemStack repairNBT(ItemStack stack) {
        try {
            // First, try to extract the original color from any remaining NBT data
            DyeColor originalColor = extractOriginalColor(stack);
            
            // Create a default colored hanging lights item with the original color
            ItemStack repairedStack = FLCraftingRecipes.makeHangingLights(
                new ItemStack(FLItems.HANGING_LIGHTS.get()), 
                originalColor
            );
            
            LOGGER.info("Successfully repaired NBT for Hanging Lights item with original color: {}", originalColor.getName());
            return repairedStack;
        } catch (Exception e) {
            LOGGER.error("Failed to repair NBT for Hanging Lights item: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the original color from the item's NBT data.
     * This method tries to find the color information that was present before NBT stripping.
     */
    private DyeColor extractOriginalColor(ItemStack stack) {
        if (!stack.hasTag()) {
            LOGGER.debug("No NBT found, using default color");
            return DyeColor.WHITE; // Use white as a neutral default
        }
        
        CompoundTag tag = stack.getTag();
        LOGGER.debug("Extracting color from NBT: {}", tag.toString());
        
        // Check for our captured color information first (most reliable)
        if (tag.contains("CapturedColor", Tag.TAG_STRING)) {
            String colorName = tag.getString("CapturedColor");
            LOGGER.debug("Found captured color: {}", colorName);
            try {
                return DyeColor.valueOf(colorName.toUpperCase());
            } catch (IllegalArgumentException e) {
                LOGGER.debug("Invalid captured color name: {}", colorName);
            }
        }
        
        // Check for our backup color information
        if (tag.contains("OriginalColor", Tag.TAG_STRING)) {
            String colorName = tag.getString("OriginalColor");
            LOGGER.debug("Found backup color: {}", colorName);
            try {
                return DyeColor.valueOf(colorName.toUpperCase());
            } catch (IllegalArgumentException e) {
                LOGGER.debug("Invalid backup color name: {}", colorName);
            }
        }
        
        // Check for any color-related NBT
        if (tag.contains("CustomColor", Tag.TAG_INT)) {
            int color = tag.getInt("CustomColor");
            LOGGER.debug("Found CustomColor: {}", color);
            return getClosestDyeColor(color);
        }
        
        // Check for pattern data that might be partially intact
        if (tag.contains("pattern", Tag.TAG_LIST)) {
            ListTag pattern = tag.getList("pattern", Tag.TAG_COMPOUND);
            LOGGER.debug("Found pattern with {} items", pattern.size());
            if (pattern.size() > 0) {
                CompoundTag firstLight = pattern.getCompound(0);
                LOGGER.debug("First light NBT: {}", firstLight.toString());
                if (firstLight.contains("tag", Tag.TAG_COMPOUND)) {
                    CompoundTag lightTag = firstLight.getCompound("tag");
                    LOGGER.debug("Light tag: {}", lightTag.toString());
                    if (lightTag.contains("CustomColor", Tag.TAG_INT)) {
                        int color = lightTag.getInt("CustomColor");
                        LOGGER.debug("Found CustomColor in light: {}", color);
                        return getClosestDyeColor(color);
                    }
                }
            }
        }
        
        // If we can't find any color information, use a neutral default
        LOGGER.debug("No color information found, using default white");
        return DyeColor.WHITE;
    }

    /**
     * Converts an RGB color to the closest Minecraft dye color.
     */
    private DyeColor getClosestDyeColor(int rgb) {
        // Simple mapping of common RGB values to dye colors
        // This is a basic implementation - could be made more sophisticated
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        
        // Simple color matching logic
        if (r > 200 && g > 200 && b > 200) return DyeColor.WHITE;
        if (r < 50 && g < 50 && b < 50) return DyeColor.BLACK;
        if (r > 200 && g < 100 && b < 100) return DyeColor.RED;
        if (r < 100 && g > 200 && b < 100) return DyeColor.LIME;
        if (r < 100 && g < 100 && b > 200) return DyeColor.BLUE;
        if (r > 200 && g > 200 && b < 100) return DyeColor.YELLOW;
        if (r > 200 && g < 100 && b > 200) return DyeColor.MAGENTA;
        if (r < 100 && g > 200 && b > 200) return DyeColor.CYAN;
        if (r > 150 && g > 100 && b < 100) return DyeColor.ORANGE;
        if (r > 100 && g < 100 && b > 150) return DyeColor.PURPLE;
        if (r < 100 && g > 150 && b < 100) return DyeColor.GREEN;
        if (r > 150 && g < 150 && b < 150) return DyeColor.BROWN;
        if (r > 150 && g > 150 && b < 150) return DyeColor.LIGHT_GRAY;
        if (r > 100 && g > 100 && b > 100) return DyeColor.GRAY;
        if (r > 200 && g > 100 && b > 100) return DyeColor.PINK;
        if (r < 150 && g > 100 && b > 150) return DyeColor.LIGHT_BLUE;
        
        // Default fallback
        return DyeColor.WHITE;
    }

    @Override
    public void appendHoverText(final ItemStack stack, @Nullable final Level world, final List<Component> tooltip, final TooltipFlag flag) {
        final CompoundTag compound = stack.getTag();
        if (compound != null) {
            final ResourceLocation name = RegistryObjects.getName(FairyLights.STRING_TYPES.get(), getString(compound));
            tooltip.add(Component.translatable("item." + name.getNamespace() + "." + name.getPath()).withStyle(ChatFormatting.GRAY));
        }
        if (compound != null && compound.contains("pattern", Tag.TAG_LIST)) {
            final ListTag tagList = compound.getList("pattern", Tag.TAG_COMPOUND);
            final int tagCount = tagList.size();
            if (tagCount > 0) {
                tooltip.add(Component.empty());
            }
            for (int i = 0; i < tagCount; i++) {
                final ItemStack lightStack = ItemStack.of(tagList.getCompound(i));
                tooltip.add(lightStack.getHoverName());
                lightStack.getItem().appendHoverText(lightStack, world, tooltip, flag);
            }
        } else {
            // Show warning if NBT is missing
            tooltip.add(Component.literal("Server obstructed NBT data, placing this item should fix it.").withStyle(ChatFormatting.RED));
        }
    }

    public static StringType getString(final CompoundTag tag) {
        return Objects.requireNonNull(FairyLights.STRING_TYPES.get().getValue(ResourceLocation.tryParse(tag.getString("string"))));
    }

    public static void setString(final CompoundTag tag, final StringType string) {
        tag.putString("string", RegistryObjects.getName(FairyLights.STRING_TYPES.get(), string).toString());
    }
}

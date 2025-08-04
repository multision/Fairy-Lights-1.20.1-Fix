package me.paulf.fairylights.server.item;

import me.paulf.fairylights.server.connection.ConnectionTypes;
import me.paulf.fairylights.server.item.crafting.FLCraftingRecipes;
import me.paulf.fairylights.util.styledstring.StyledString;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class PennantBuntingConnectionItem extends ConnectionItem {
    private static final Logger LOGGER = LogManager.getLogger();

    public PennantBuntingConnectionItem(final Item.Properties properties) {
        super(properties, ConnectionTypes.PENNANT_BUNTING);
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
        
        // Try to extract color from the first pennant in the pattern
        CompoundTag firstPennant = pattern.getCompound(0);
        if (firstPennant.contains("tag", Tag.TAG_COMPOUND)) {
            CompoundTag pennantTag = firstPennant.getCompound("tag");
            if (pennantTag.contains("CustomColor", Tag.TAG_INT)) {
                int color = pennantTag.getInt("CustomColor");
                DyeColor dyeColor = getClosestDyeColor(color);
                
                // Store the color information in a way that's less likely to be stripped
                tag.putString("CapturedColor", dyeColor.getName());
                tag.putInt("CapturedRGB", color);
                
                LOGGER.debug("Captured color information for Pennant Bunting: {} (RGB: {})", dyeColor.getName(), color);
            }
        }
    }

    @Override
    public ItemStack repairNBT(ItemStack stack) {
        try {
            // First, try to extract the original color from any remaining NBT data
            DyeColor originalColor = extractOriginalColor(stack);
            
            // Create a default colored pennant bunting item with the original color
            ItemStack repairedStack = FLCraftingRecipes.makePennant(
                new ItemStack(FLItems.PENNANT_BUNTING.get()), 
                originalColor
            );
            
            LOGGER.info("Successfully repaired NBT for Pennant Bunting item with original color: {}", originalColor.getName());
            return repairedStack;
        } catch (Exception e) {
            LOGGER.error("Failed to repair NBT for Pennant Bunting item: {}", e.getMessage());
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
        LOGGER.debug("Extracting color from Pennant Bunting NBT: {}", tag.toString());
        
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
                CompoundTag firstPennant = pattern.getCompound(0);
                LOGGER.debug("First pennant NBT: {}", firstPennant.toString());
                if (firstPennant.contains("tag", Tag.TAG_COMPOUND)) {
                    CompoundTag pennantTag = firstPennant.getCompound("tag");
                    LOGGER.debug("Pennant tag: {}", pennantTag.toString());
                    if (pennantTag.contains("CustomColor", Tag.TAG_INT)) {
                        int color = pennantTag.getInt("CustomColor");
                        LOGGER.debug("Found CustomColor in pennant: {}", color);
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
    public void appendHoverText(final ItemStack stack, final Level world, final List<Component> tooltip, final TooltipFlag flag) {
        final CompoundTag compound = stack.getTag();
        if (compound == null) {
            return;
        }
        if (compound.contains("text", Tag.TAG_COMPOUND)) {
            final CompoundTag text = compound.getCompound("text");
            final StyledString s = StyledString.deserialize(text);
            if (s.length() > 0) {
                tooltip.add(Component.translatable("format.fairylights.text", s.toTextComponent()).withStyle(ChatFormatting.GRAY));
            }
        }
        if (compound.contains("pattern", Tag.TAG_LIST)) {
            final ListTag tagList = compound.getList("pattern", Tag.TAG_COMPOUND);
            final int tagCount = tagList.size();
            if (tagCount > 0) {
                tooltip.add(Component.empty());
            }
            for (int i = 0; i < tagCount; i++) {
                final ItemStack item = ItemStack.of(tagList.getCompound(i));
                tooltip.add(item.getHoverName());
            }
        }
        
        // Add warning if NBT data is missing
        if (!hasValidNBT(stack)) {
            tooltip.add(Component.literal("Server obstructed NBT data, placing this item should fix it.").withStyle(ChatFormatting.RED));
        }
    }
}

package me.paulf.fairylights.server;

import me.paulf.fairylights.FairyLights;
import me.paulf.fairylights.server.block.FLBlocks;
import me.paulf.fairylights.server.block.FastenerBlock;
import me.paulf.fairylights.server.block.entity.FastenerBlockEntity;
import me.paulf.fairylights.server.capability.CapabilityHandler;
import me.paulf.fairylights.server.connection.HangingLightsConnection;
import me.paulf.fairylights.server.entity.FenceFastenerEntity;
import me.paulf.fairylights.server.fastener.BlockFastener;
import me.paulf.fairylights.server.fastener.FenceFastener;
import me.paulf.fairylights.server.fastener.PlayerFastener;
import me.paulf.fairylights.server.feature.light.Light;
import me.paulf.fairylights.server.item.ConnectionItem;
import me.paulf.fairylights.server.item.HangingLightsConnectionItem;
import me.paulf.fairylights.server.item.PennantBuntingConnectionItem;
import me.paulf.fairylights.server.jingle.Jingle;
import me.paulf.fairylights.server.jingle.JingleLibrary;
import me.paulf.fairylights.server.jingle.JingleManager;
import me.paulf.fairylights.server.net.clientbound.JingleMessage;
import me.paulf.fairylights.server.net.clientbound.UpdateEntityFastenerMessage;
import me.paulf.fairylights.server.sound.FLSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.NoteBlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;
import java.util.List;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;

public final class ServerEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public void onEntityJoinWorld(final EntityJoinLevelEvent event) {
        final Entity entity = event.getEntity();
        if (entity instanceof Player || entity instanceof FenceFastenerEntity) {
            entity.getCapability(CapabilityHandler.FASTENER_CAP).ifPresent(f -> f.setWorld(event.getLevel()));
        }
    }

    @SubscribeEvent
    public void onAttachEntityCapability(final AttachCapabilitiesEvent<Entity> event) {
        final Entity entity = event.getObject();
        if (entity instanceof Player) {
            event.addCapability(CapabilityHandler.FASTENER_ID, new PlayerFastener((Player) entity));
        } else if (entity instanceof FenceFastenerEntity) {
            event.addCapability(CapabilityHandler.FASTENER_ID, new FenceFastener((FenceFastenerEntity) entity));
        }
    }

    @SubscribeEvent
    public void onAttachBlockEntityCapability(final AttachCapabilitiesEvent<BlockEntity> event) {
        final BlockEntity entity = event.getObject();
        if (entity instanceof FastenerBlockEntity) {
            event.addCapability(CapabilityHandler.FASTENER_ID, new BlockFastener((FastenerBlockEntity) entity, ServerProxy.buildBlockView()));
        }
    }

    /**
     * Ensures that a player has the required fastener capability attached.
     * This is a fallback method for hybrid servers where capabilities might not be properly attached.
     */
    public static void ensurePlayerCapability(final Player player) {
        if (!player.getCapability(CapabilityHandler.FASTENER_CAP).isPresent()) {
            // Force attach the capability if it's missing
            player.getCapability(CapabilityHandler.FASTENER_CAP).resolve().orElseGet(() -> {
                final PlayerFastener fastener = new PlayerFastener(player);
                fastener.setWorld(player.level());
                return fastener;
            });
        }
    }

    /**
     * Monitors when items are added to player inventories and repairs NBT for connection items.
     * This is more efficient than checking every tick.
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            // Check inventory when player logs in
            checkAndRepairInventoryNBT(event.getEntity());
        }
    }

    /**
     * Captures color information from Hanging Lights items when they are added to the player's inventory.
     * This event fires when items are moved from creative menu to inventory.
     */
    @SubscribeEvent
    public void onPlayerContainerOpen(final PlayerContainerEvent.Open event) {
        if (event.getEntity().level().isClientSide()) return;
        
        // Check if any connection items are being added to the inventory
        for (int i = 0; i < event.getEntity().getInventory().getContainerSize(); i++) {
            ItemStack stack = event.getEntity().getInventory().getItem(i);
            if (!stack.isEmpty() && (stack.getItem() instanceof HangingLightsConnectionItem || stack.getItem() instanceof PennantBuntingConnectionItem)) {
                captureConnectionItemColor(stack);
            }
        }
    }

    /**
     * Captures color information from Hanging Lights items when the player's inventory changes.
     * This is a more comprehensive approach to catch all inventory modifications.
     */
    @SubscribeEvent
    public void onPlayerContainerChanged(final PlayerContainerEvent.Close event) {
        if (event.getEntity().level().isClientSide()) return;
        
        // Check if any connection items are in the inventory
        for (int i = 0; i < event.getEntity().getInventory().getContainerSize(); i++) {
            ItemStack stack = event.getEntity().getInventory().getItem(i);
            if (!stack.isEmpty() && (stack.getItem() instanceof HangingLightsConnectionItem || stack.getItem() instanceof PennantBuntingConnectionItem)) {
                captureConnectionItemColor(stack);
            }
        }
    }

    /**
     * Captures color information from Hanging Lights items when they are picked up.
     * This event fires when items are added to the player's inventory from any source.
     */
    @SubscribeEvent
    public void onPlayerPickupItem(final PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        
        ItemStack stack = event.getStack();
        if (!stack.isEmpty() && (stack.getItem() instanceof HangingLightsConnectionItem || stack.getItem() instanceof PennantBuntingConnectionItem)) {
            captureConnectionItemColor(stack);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide()) {
            // Update fastener capabilities
            event.player.getCapability(CapabilityHandler.FASTENER_CAP).ifPresent(fastener -> {
                if (fastener.update()) {
                    ServerProxy.sendToPlayersWatchingEntity(new UpdateEntityFastenerMessage(event.player, fastener.serializeNBT()), event.player);
                }
            });
        }
    }

    /**
     * Monitors when items are added to player inventories and repairs NBT for connection items.
     * This helps with hybrid servers that strip NBT when items are placed in inventories.
     */
    private void checkAndRepairInventoryNBT(Player player) {
        // Check main inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ConnectionItem) {
                ItemStack repairedStack = repairConnectionItemNBT(stack);
                if (repairedStack != null && repairedStack != stack) {
                    player.getInventory().setItem(i, repairedStack);
                    LOGGER.info("Repaired NBT for connection item in player {} inventory slot {}", 
                               player.getName().getString(), i);
                }
            }
        }
        
        // Check offhand
        ItemStack offhandStack = player.getOffhandItem();
        if (!offhandStack.isEmpty() && offhandStack.getItem() instanceof ConnectionItem) {
            ItemStack repairedStack = repairConnectionItemNBT(offhandStack);
            if (repairedStack != null && repairedStack != offhandStack) {
                player.setItemInHand(InteractionHand.OFF_HAND, repairedStack);
                LOGGER.info("Repaired NBT for connection item in player {} offhand", 
                           player.getName().getString());
            }
        }
    }

    /**
     * Attempts to repair NBT for a connection item.
     */
    private ItemStack repairConnectionItemNBT(ItemStack stack) {
        if (stack.getItem() instanceof ConnectionItem) {
            ConnectionItem connectionItem = (ConnectionItem) stack.getItem();
            if (!connectionItem.hasValidNBT(stack)) {
                return connectionItem.repairNBT(stack);
            }
        }
        return null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onNoteBlockPlay(final NoteBlockEvent.Play event) {
        final Level world = (Level) event.getLevel();
        final BlockPos pos = event.getPos();
        final Block noteBlock = world.getBlockState(pos).getBlock();
        final BlockState below = world.getBlockState(pos.below());
        if (below.getBlock() == FLBlocks.FASTENER.get() && below.getValue(FastenerBlock.FACING) == Direction.DOWN) {
            final int note = event.getVanillaNoteId();
            final float pitch = (float) Math.pow(2, (note - 12) / 12D);
            world.playSound(null, pos, FLSounds.JINGLE_BELL.get(), SoundSource.RECORDS, 3, pitch);
            world.addParticle(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, note / 24D, 0, 0);
            if (!world.isClientSide()) {
                final Packet<?> pkt = new ClientboundBlockEventPacket(pos, noteBlock, event.getInstrument().ordinal(), note);
                final PlayerList players = world.getServer().getPlayerList();
                players.broadcast(null, pos.getX(), pos.getY(), pos.getZ(), 64, world.dimension(), pkt);
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        final Player player = event.getEntity();
        final ItemStack stack = event.getItemStack();
        
        // Capture color information from Hanging Lights and Pennant Bunting items before NBT is stripped
        if (!stack.isEmpty() && (stack.getItem() instanceof HangingLightsConnectionItem || stack.getItem() instanceof PennantBuntingConnectionItem)) {
            captureConnectionItemColor(stack);
        }
        
        final Level world = event.getLevel();
        final BlockPos pos = event.getPos();
        if (!(world.getBlockState(pos).getBlock() instanceof FenceBlock)) {
            return;
        }
        boolean checkHanging = stack.getItem() == Items.LEAD;
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            final ItemStack offhandStack = player.getOffhandItem();
            if (offhandStack.getItem() instanceof ConnectionItem) {
                if (checkHanging) {
                    event.setCanceled(true);
                    return;
                } else {
                    event.setUseBlock(Event.Result.DENY);
                }
            }
        }
        if (!checkHanging && !world.isClientSide()) {
            final double range = 7;
            final int x = pos.getX();
            final int y = pos.getY();
            final int z = pos.getZ();
            final AABB area = new AABB(x - range, y - range, z - range, x + range, y + range, z + range);
            for (final Mob entity : world.getEntitiesOfClass(Mob.class, area)) {
                if (entity.isLeashed() && entity.getLeashHolder() == player) {
                    checkHanging = true;
                    break;
                }
            }
        }
        if (checkHanging) {
            final HangingEntity entity = FenceFastenerEntity.findHanging(world, pos);
            if (entity != null && !(entity instanceof LeashFenceKnotEntity)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Captures color information from connection items when they are first interacted with.
     * This should be called before the hybrid server strips the NBT data.
     */
    private void captureConnectionItemColor(ItemStack stack) {
        if (!stack.hasTag()) return;
        
        CompoundTag tag = stack.getTag();
        if (!tag.contains("pattern", Tag.TAG_LIST)) return;
        
        ListTag pattern = tag.getList("pattern", Tag.TAG_COMPOUND);
        if (pattern.size() == 0) return;
        
        // Try to extract color from the first item in the pattern
        CompoundTag firstItem = pattern.getCompound(0);
        if (firstItem.contains("tag", Tag.TAG_COMPOUND)) {
            CompoundTag itemTag = firstItem.getCompound("tag");
            if (itemTag.contains("CustomColor", Tag.TAG_INT)) {
                int color = itemTag.getInt("CustomColor");
                DyeColor dyeColor = getClosestDyeColor(color);
                
                // Store the color information in a way that's less likely to be stripped
                tag.putString("CapturedColor", dyeColor.getName());
                tag.putInt("CapturedRGB", color);
                
                String itemType = stack.getItem() instanceof HangingLightsConnectionItem ? "Hanging Lights" : "Pennant Bunting";
                LOGGER.info("Captured color information for {}: {} (RGB: {})", itemType, dyeColor.getName(), color);
            }
        }
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

    public static boolean tryJingle(final Level world, final HangingLightsConnection hangingLights) {
        String lib;
        if (FairyLights.CHRISTMAS.isOccurringNow()) {
            lib = JingleLibrary.CHRISTMAS;
        } else if (FairyLights.HALLOWEEN.isOccurringNow()) {
            lib = JingleLibrary.HALLOWEEN;
        } else {
            lib = JingleLibrary.RANDOM;
        }
        return tryJingle(world, hangingLights, lib);
    }

    public static boolean tryJingle(final Level world, final HangingLightsConnection hangingLights, final String lib) {
        if (world.isClientSide()) return false;
        final Light<?>[] lights = hangingLights.getFeatures();
        final Jingle jingle = JingleManager.INSTANCE.get(lib).getRandom(world.random, lights.length);
        if (jingle != null) {
            final int lightOffset = lights.length / 2 - jingle.getRange() / 2;
            hangingLights.play(jingle, lightOffset);
            ServerProxy.sendToPlayersWatchingChunk(new JingleMessage(hangingLights, lightOffset, jingle), world, hangingLights.getFastener().getPos());
            return true;
        }
        return false;
    }
}

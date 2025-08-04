package me.paulf.fairylights.server.item;

import me.paulf.fairylights.server.ServerEventHandler;
import me.paulf.fairylights.server.block.FLBlocks;
import me.paulf.fairylights.server.block.FastenerBlock;
import me.paulf.fairylights.server.capability.CapabilityHandler;
import me.paulf.fairylights.server.connection.Connection;
import me.paulf.fairylights.server.connection.ConnectionType;
import me.paulf.fairylights.server.entity.FenceFastenerEntity;
import me.paulf.fairylights.server.fastener.Fastener;
import me.paulf.fairylights.server.fastener.PlayerFastener;
import me.paulf.fairylights.server.sound.FLSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

public abstract class ConnectionItem extends Item {
    private static final Logger LOGGER = LogManager.getLogger();
    private final RegistryObject<? extends ConnectionType<?>> type;

    public ConnectionItem(final Properties properties, final RegistryObject<? extends ConnectionType<?>> type) {
        super(properties);
        this.type = type;
    }

    public final ConnectionType<?> getConnectionType() {
        return (ConnectionType<?>) this.type.get();
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Player user = context.getPlayer();
        if (user == null) {
            return super.useOn(context);
        }
        
        // Validate and repair NBT if needed (common issue with hybrid servers)
        ItemStack stack = context.getItemInHand();
        if (!hasValidNBT(stack)) {
            LOGGER.warn("Detected invalid NBT for connection item, attempting repair for player {}", user.getName().getString());
            ItemStack repairedStack = repairNBT(stack);
            if (repairedStack != null) {
                user.setItemInHand(context.getHand(), repairedStack);
                stack = repairedStack;
            }
        }
        
        final Level world = context.getLevel();
        final Direction side = context.getClickedFace();
        final BlockPos clickPos = context.getClickedPos();
        final Block fastener = FLBlocks.FASTENER.get();
        if (this.isConnectionInOtherHand(world, user, stack)) {
            return InteractionResult.PASS;
        }
        final BlockState fastenerState = fastener.defaultBlockState().setValue(FastenerBlock.FACING, side);
        final BlockState currentBlockState = world.getBlockState(clickPos);
        final BlockPlaceContext blockContext = new BlockPlaceContext(context);
        final BlockPos placePos = blockContext.getClickedPos();
        if (currentBlockState.getBlock() == fastener) {
            if (!world.isClientSide()) {
                this.connect(stack, user, world, clickPos);
            }
            return InteractionResult.SUCCESS;
        } else if (blockContext.canPlace() && fastenerState.canSurvive(world, placePos)) {
            if (!world.isClientSide()) {
                this.connect(stack, user, world, placePos, fastenerState);
            }
            return InteractionResult.SUCCESS;
        } else if (isFence(currentBlockState)) {
            final HangingEntity entity = FenceFastenerEntity.findHanging(world, clickPos);
            if (entity == null || entity instanceof FenceFastenerEntity) {
                if (!world.isClientSide()) {
                    this.connectFence(stack, user, world, clickPos, (FenceFastenerEntity) entity);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * Checks if the item stack has valid NBT data for connection items.
     * This helps detect when hybrid servers strip NBT from creative menu items.
     */
    public boolean hasValidNBT(ItemStack stack) {
        if (!stack.hasTag()) {
            return false;
        }
        
        CompoundTag tag = stack.getTag();
        
        // For hanging lights, check for pattern and string
        if (stack.getItem() instanceof HangingLightsConnectionItem) {
            return tag.contains("pattern", Tag.TAG_LIST) && tag.contains("string", Tag.TAG_STRING);
        }
        
        // For pennant buntings, check for pattern
        if (stack.getItem() instanceof PennantBuntingConnectionItem) {
            return tag.contains("pattern", Tag.TAG_LIST);
        }
        
        // For other connection items, check for basic NBT structure
        // This can be overridden by subclasses for specific validation
        return true;
    }

    /**
     * Attempts to repair NBT data for connection items.
     * This is a fallback method that can be overridden by subclasses.
     */
    public ItemStack repairNBT(ItemStack stack) {
        // Default implementation - subclasses should override this
        LOGGER.warn("No NBT repair method available for item: {}", stack.getItem());
        return null;
    }

    private boolean isConnectionInOtherHand(final Level world, final Player user, final ItemStack stack) {
        // Ensure player capability is attached (fallback for hybrid servers)
        ServerEventHandler.ensurePlayerCapability(user);
        
        final Optional<Fastener<?>> attacherOpt = user.getCapability(CapabilityHandler.FASTENER_CAP).resolve();
        if (attacherOpt.isEmpty()) {
            // Capability not available (common on hybrid servers), assume no connection in other hand
            return false;
        }
        final Fastener<?> attacher = attacherOpt.get();
        return attacher.getFirstConnection().filter(connection -> {
            final CompoundTag nbt = connection.serializeLogic();
            return nbt.isEmpty() ? stack.hasTag() : !NbtUtils.compareNbt(nbt, stack.getTag(), true);
        }).isPresent();
    }

    private void connect(final ItemStack stack, final Player user, final Level world, final BlockPos pos) {
        final BlockEntity entity = world.getBlockEntity(pos);
        if (entity != null) {
            entity.getCapability(CapabilityHandler.FASTENER_CAP).ifPresent(fastener -> this.connect(stack, user, world, fastener));
        }
    }

    private void connect(final ItemStack stack, final Player user, final Level world, final BlockPos pos, final BlockState state) {
        if (world.setBlock(pos, state, 3)) {
            state.getBlock().setPlacedBy(world, pos, state, user, stack);
            final SoundType sound = state.getBlock().getSoundType(state, world, pos, user);
            world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                sound.getPlaceSound(),
                SoundSource.BLOCKS,
                (sound.getVolume() + 1) / 2,
                sound.getPitch() * 0.8F
            );
            final BlockEntity entity = world.getBlockEntity(pos);
            if (entity != null) {
                entity.getCapability(CapabilityHandler.FASTENER_CAP).ifPresent(destination -> this.connect(stack, user, world, destination, false));
            }
        }
    }

    public void connect(final ItemStack stack, final Player user, final Level world, final Fastener<?> fastener) {
        this.connect(stack, user, world, fastener, true);
    }

    public void connect(final ItemStack stack, final Player user, final Level world, final Fastener<?> fastener, final boolean playConnectSound) {
        // Ensure player capability is attached (fallback for hybrid servers)
        ServerEventHandler.ensurePlayerCapability(user);
        
        final Optional<Fastener<?>> attacherOpt = user.getCapability(CapabilityHandler.FASTENER_CAP).resolve();
        if (attacherOpt.isPresent()) {
            final Fastener<?> attacher = attacherOpt.get();
            LOGGER.debug("Using existing player capability for {}", user.getName().getString());
            boolean playSound = playConnectSound;
            final Optional<Connection> placing = attacher.getFirstConnection();
            if (placing.isPresent()) {
                final Connection conn = placing.get();
                if (conn.reconnect(fastener)) {
                    conn.onConnect(world, user, stack);
                    stack.shrink(1);
                } else {
                    playSound = false;
                }
            } else {
                final CompoundTag data = stack.getTag();
                fastener.connect(world, attacher, this.getConnectionType(), data == null ? new CompoundTag() : data, false);
            }
            if (playSound) {
                final Vec3 pos = fastener.getConnectionPoint();
                world.playSound(null, pos.x, pos.y, pos.z, FLSounds.CORD_CONNECT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        } else {
            // Capability not available (common on hybrid servers), create temporary player fastener
            LOGGER.warn("Player capability not available, creating temporary connection for player {}", user.getName().getString());
            final PlayerFastener tempAttacher = new PlayerFastener(user);
            tempAttacher.setWorld(world);
            final CompoundTag data = stack.getTag();
            try {
                fastener.connect(world, tempAttacher, this.getConnectionType(), data == null ? new CompoundTag() : data, false);
                stack.shrink(1);
                if (playConnectSound) {
                    final Vec3 pos = fastener.getConnectionPoint();
                    world.playSound(null, pos.x, pos.y, pos.z, FLSounds.CORD_CONNECT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                LOGGER.info("Successfully created temporary connection for player {}", user.getName().getString());
            } catch (Exception e) {
                LOGGER.error("Failed to create connection for player {}: {}", user.getName().getString(), e.getMessage());
                // Even if connection fails, consume the item to prevent it from returning to inventory
                stack.shrink(1);
            }
        }
    }

    private void connectFence(final ItemStack stack, final Player user, final Level world, final BlockPos pos, FenceFastenerEntity fastener) {
        final boolean playConnectSound;
        if (fastener == null) {
            fastener = FenceFastenerEntity.create(world, pos);
            playConnectSound = false;
        } else {
            playConnectSound = true;
        }
        final Optional<Fastener<?>> fastenerOpt = fastener.getCapability(CapabilityHandler.FASTENER_CAP).resolve();
        if (fastenerOpt.isPresent()) {
            this.connect(stack, user, world, fastenerOpt.get(), playConnectSound);
        } else {
            // Capability not available (common on hybrid servers), log warning and skip connection
            LOGGER.warn("Failed to get fastener capability for fence fastener at {}", pos);
        }
    }

    public static boolean isFence(final BlockState state) {
        return state.isSolid() && state.is(BlockTags.FENCES);
    }
}

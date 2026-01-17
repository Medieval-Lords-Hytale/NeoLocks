package me.ascheladd.hytale.quicksigns.listener;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.ascheladd.hytale.quicksigns.QuickSigns;
import me.ascheladd.hytale.quicksigns.storage.SignHologramStorage;
import me.ascheladd.hytale.quicksigns.util.SignUtil;

/**
 * Handles block breaking events for signs - deletes associated holograms.
 */
public class BlockBreakListener extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    
    private final SignHologramStorage signHologramStorage;
    
    public BlockBreakListener(SignHologramStorage signHologramStorage) {
        super(BreakBlockEvent.class);
        this.signHologramStorage = signHologramStorage;
    }
    
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
    
    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull BreakBlockEvent ev
    ) {
        BlockType blockType = ev.getBlockType();
        var targetBlock = ev.getTargetBlock();
        int blockX = targetBlock.x;
        int blockY = targetBlock.y;
        int blockZ = targetBlock.z;
        
        String worldId = store.getExternalData().getWorld().getName();
        var item = blockType != null ? blockType.getItem() : null;
        System.out.println("Item breaking: " + blockX + "," + blockY + "," + blockZ + " in world " + worldId + ": " + (item != null ? item.getId() : "null"));

        // Check if breaking an editable sign - delete associated holograms
        if (SignUtil.isEditableSign(item)) {
            // Get entity ref if available (may be null for physics-based breaks)
            Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
            deleteSignHolograms(worldId, blockX, blockY, blockZ, entityRef);
        }
    }
    
    /**
     * Deletes all sign text holograms at the specified location.
     */
    private void deleteSignHolograms(String worldId, int x, int y, int z, Ref<EntityStore> playerEntityRef) {
        List<UUID> uuids = signHologramStorage.removeSignHolograms(worldId, x, y, z);
        
        if (uuids == null || uuids.isEmpty()) {
            QuickSigns.debug("No sign holograms found in storage for this location");
            return;
        }
        
        QuickSigns.debug("Deleting " + uuids.size() + " sign text holograms at " + worldId + ":" + x + ":" + y + ":" + z);
        for (UUID uuid : uuids) {
            QuickSigns.debug("Attempting to delete hologram UUID: " + uuid);
            deleteHologramByUuid(playerEntityRef, uuid);
        }
    }
    
    /**
     * Deletes a hologram entity by its UUID
     */
    private void deleteHologramByUuid(Ref<EntityStore> playerEntityRef, UUID entityUuid) {
        QuickSigns.debug("deleteHologramByUuid called for UUID: " + entityUuid);
        
        try {
            var store = playerEntityRef.getStore();
            var world = store.getExternalData().getWorld();
            
            QuickSigns.debug("Scheduling hologram deletion on world thread");
            
            // Execute on world thread for thread safety
            world.execute(() -> {
                var entityStore = world.getEntityStore();
                
                // Note: getRefFromUUID is a method on EntityStore, not Store<EntityStore>
                Ref<EntityStore> hologramRef = entityStore.getRefFromUUID(java.util.Objects.requireNonNull(entityUuid));
                
                if (hologramRef == null) {
                    QuickSigns.logger().atWarning().log("Could not find entity with UUID: " + entityUuid);
                    QuickSigns.logger().atWarning().log("Entity may have already been removed or UUID is invalid");
                    return;
                }
                
                // Validate reference before deletion
                if (!hologramRef.isValid()) {
                    QuickSigns.debug("Hologram reference is not valid for UUID: " + entityUuid);
                    QuickSigns.debug("Entity may have already been removed");
                    return;
                }
                
                try {
                    Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
                    entityStore.getStore().removeEntity(hologramRef, holder, RemoveReason.REMOVE);
                    QuickSigns.debug("✓ Successfully deleted hologram with UUID: " + entityUuid);
                } catch (Exception e) {
                    QuickSigns.logger().atSevere().log("Failed to remove hologram entity: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            QuickSigns.logger().atSevere().log("Failed to schedule hologram deletion: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

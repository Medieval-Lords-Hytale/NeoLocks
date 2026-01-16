package me.ascheladd.hytale.neolocks.listener;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.ascheladd.hytale.neolocks.model.LockedChest;
import me.ascheladd.hytale.neolocks.storage.ChestLockStorage;
import me.ascheladd.hytale.neolocks.ui.LockConfirmationPage;
import me.ascheladd.hytale.neolocks.util.ChestUtil;
import me.ascheladd.hytale.neolocks.util.ChestUtil.ChestPosition;

/**
 * Handles sign placement on chests for locking/unlocking.
 */
public class SignPlacementListener extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    
    private static final String PERMISSION_LOCK = "neolocks.lock";
    
    private final ChestLockStorage storage;
    
    public SignPlacementListener(ChestLockStorage storage) {
        super(PlaceBlockEvent.class);
        this.storage = storage;
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
        @Nonnull PlaceBlockEvent ev
    ) {
        // Check if placing a sign
        if (!isSign(ev.getItemInHand())) {
            return;
        }
        
        // Get the entity reference from the archetype chunk
        Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        
        // Get the player component from entity ref
        Player player = store.getComponent(entityRef, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
        if (player == null) {
            return; // Not a valid player interaction
        }
        
        // Check if player has permission to lock chests
        if (!player.hasPermission(PERMISSION_LOCK)) {
            player.sendMessage(Message.raw("You don't have permission to lock chests.").color("#FF0000"));
            return;
        }
        
        var targetBlock = ev.getTargetBlock();
        int x = targetBlock.x;
        int y = targetBlock.y;
        int z = targetBlock.z;
        
        String worldId = store.getExternalData().getWorld().getName();
        
        // Get all positions for the chest at this location (handles double chests)
        ChestPosition[] chestPositions = ChestUtil.getChestPositions(entityRef, x, y, z);
        
        if (chestPositions.length == 0) {
            return; // No chest at this location
        }
        
        // Check if any part of the chest is already locked
        for (ChestPosition pos : chestPositions) {
            if (storage.isLocked(worldId, pos.x, pos.y, pos.z)) {
                LockedChest existingLock = storage.getLockedChest(worldId, pos.x, pos.y, pos.z);
                
                // If owned by this player, allow unlocking
                if (existingLock.isOwnedBy(playerRef.getUuid())) {
                    // Unlock all parts of the chest
                    for (ChestPosition unlockPos : chestPositions) {
                        storage.unlockChest(worldId, unlockPos.x, unlockPos.y, unlockPos.z);
                    }
                    // Send unlock confirmation message
                    player.sendMessage(Message.raw("Chest unlocked!").color("#00FF00"));
                    ev.setCancelled(true);
                    return;
                }
                
                // Already locked by someone else
                player.sendMessage(Message.raw("This chest is already locked by another player.").color("#FF0000"));
                ev.setCancelled(true);
                return;
            }
        }
        
        // Cancel the sign placement event
        ev.setCancelled(true);
        
        // Convert ChestPosition[] to LockConfirmationPage.ChestPosition[]
        LockConfirmationPage.ChestPosition[] uiChestPositions = 
            new LockConfirmationPage.ChestPosition[chestPositions.length];
        for (int i = 0; i < chestPositions.length; i++) {
            uiChestPositions[i] = new LockConfirmationPage.ChestPosition(
                chestPositions[i].x,
                chestPositions[i].y,
                chestPositions[i].z
            );
        }
        
        // Open the lock confirmation UI
        LockConfirmationPage confirmPage = new LockConfirmationPage(
            playerRef,
            storage,
            playerRef.getUuid(),
            playerRef.getUsername(),
            worldId,
            x,
            y,
            z,
            uiChestPositions
        );
        
        player.getPageManager().openCustomPage(
            entityRef,
            store,
            confirmPage
        );
    }
    
    /**
     * Checks if an ItemStack will place a sign block.
     */
    private boolean isSign(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        
        String blockKey = itemStack.getBlockKey();
        return blockKey != null && (
            blockKey.equals("sign") ||
            blockKey.contains("sign") ||
            blockKey.equals("hytale:sign")
        );
    }
}

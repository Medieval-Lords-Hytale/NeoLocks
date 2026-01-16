package me.ascheladd.hytale.neolocks.listener;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.ascheladd.hytale.neolocks.model.LockedChest;
import me.ascheladd.hytale.neolocks.storage.ChestLockStorage;
import me.ascheladd.hytale.neolocks.ui.LockedChestPage;
import me.ascheladd.hytale.neolocks.util.ChestUtil;
import me.ascheladd.hytale.neolocks.util.ChestUtil.ChestPosition;

/**
 * Handles chest opening events.
 * Listens for when players try to open chests.
 */
public class ChestOpenListener extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    
    private static final String PERMISSION_BYPASS = "neolocks.bypass";
    
    private final ChestLockStorage storage;
    
    public ChestOpenListener(ChestLockStorage storage) {
        super(UseBlockEvent.Pre.class);
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
        @Nonnull UseBlockEvent.Pre ev
    ) {
        System.out.println("=== UseBlockEvent.Pre Debug Info ===");
        
        // Event-level information
        System.out.println("InteractionType: " + ev.getInteractionType());
        System.out.println("Is Cancelled: " + ev.isCancelled());
        
        // Target block information
        var targetBlock = ev.getTargetBlock();
        System.out.println("Target Block Position: " + targetBlock);
        
        // BlockType information
        var blockType = ev.getBlockType();
        System.out.println("BlockType: " + blockType);
        if (blockType != null) {
            System.out.println("  - Block ID: " + blockType.getId());
            System.out.println("  - Block Group: " + blockType.getGroup());
            System.out.println("  - Is State: " + blockType.isState());
            System.out.println("  - Is Unknown: " + blockType.isUnknown());
            System.out.println("  - Interaction Hitbox Type: " + blockType.getInteractionHitboxType());
            System.out.println("  - Interactions: " + blockType.getInteractions());
            System.out.println("  - Flags: " + blockType.getFlags());
            System.out.println("  - Is Trigger: " + blockType.isTrigger());
            
            var blockEntity = blockType.getBlockEntity();
            if (blockEntity != null) {
                System.out.println("  - Has Block Entity: true");
                var archetype = blockEntity.getArchetype();
                if (archetype != null) {
                    System.out.println("    - Component Count: " + archetype.count());
                }
            } else {
                System.out.println("  - Has Block Entity: false");
            }
            
            var data = blockType.getData();
            if (data != null) {
                var tags = data.getRawTags();
                System.out.println("  - Tags: " + (tags != null ? tags.keySet() : "null"));
            }
        }
        
        // Context information
        var context = ev.getContext();
        if (context != null) {
            System.out.println("Context Info:");
            System.out.println("  - Target Block (from context): " + context.getTargetBlock());
            System.out.println("  - Target Entity: " + context.getTargetEntity());
            System.out.println("  - Held Item: " + context.getHeldItem());
            System.out.println("  - Original Item Type: " + context.getOriginalItemType());
        }
        
        System.out.println("=== End Debug Info ===\n");
        
        Ref<EntityStore> entityRef = ev.getContext().getEntity();
        
        // Check if this is a chest interaction
        if (!ChestUtil.isChest(ev.getBlockType())) {
            System.out.println("Fail 1");
            return; // Not a chest, ignore
        }
        
        // Only handle chest opening (Use interaction)
        if (ev.getInteractionType() != InteractionType.Use) {
            System.out.println("Fail 2");
            return;
        }
        
        int x = targetBlock.x;
        int y = targetBlock.y;
        int z = targetBlock.z;
        
        String worldId = entityRef.getStore().getExternalData().getWorld().getName();
        
        // Get the player component from entity ref
        Player player = entityRef.getStore().getComponent(entityRef, Player.getComponentType());
        PlayerRef playerRef = entityRef.getStore().getComponent(entityRef, PlayerRef.getComponentType());
        if (player == null) {
            System.out.println("Fail 31");
            return; // Not a valid player interaction
        }
        
        handleChestOpen(ev, playerRef, player, worldId, x, y, z);
    }
    
    /**
     * Handles when a player tries to open a chest.
     */
    private void handleChestOpen(
        UseBlockEvent.Pre event,
        PlayerRef playerRef,
        Player player,
        String worldId,
        int x,
        int y,
        int z
    ) {
        // Get entity ref from event context
        Ref<EntityStore> entityRef = event.getContext().getEntity();
        
        // Get all positions for this chest (handles double chests)
        ChestPosition[] chestPositions = ChestUtil.getChestPositions(entityRef, x, y, z);
        
        // Check if any part of the chest is locked
        LockedChest lockedChest = null;
        for (ChestPosition pos : chestPositions) {
            if (storage.isLocked(worldId, pos.x, pos.y, pos.z)) {
                lockedChest = storage.getLockedChest(worldId, pos.x, pos.y, pos.z);
                break; // Found a locked part
            }
        }
        
        // If no part is locked, allow opening
        if (lockedChest == null) {
            System.out.println("Allow 1");
            return;
        }
        
        // Check if player owns this chest
        if (lockedChest.isOwnedBy(playerRef.getUuid())) {
            // Owner can open their own chest
            System.out.println("Allow 2");
            return;
        }
        
        // Check if player has bypass permission
        if (player.hasPermission(PERMISSION_BYPASS)) {
            System.out.println("Allow 3");
            // Player can bypass locks
            return;
        }
        
        // Cancel the chest opening
        event.setCancelled(true);
        System.out.println("Locked 1");
        
        // Show locked chest UI
        LockedChestPage lockedPage = new LockedChestPage(
            playerRef,
            lockedChest
        );
        
        player.getPageManager().openCustomPage(
            entityRef,
            entityRef.getStore(),
            lockedPage
        );
    }
}
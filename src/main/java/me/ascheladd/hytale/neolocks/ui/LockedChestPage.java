package me.ascheladd.hytale.neolocks.ui;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.ascheladd.hytale.neolocks.model.LockedChest;

/**
 * UI page shown when a player tries to open a locked chest they don't own.
 * Displays information about the chest being locked and who owns it.
 */
public class LockedChestPage extends CustomUIPage {
    
    private final LockedChest lockedChest;
    
    public LockedChestPage(@Nonnull PlayerRef playerRef, LockedChest lockedChest) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.lockedChest = lockedChest;
    }
    
    @Override
    public void build(
        @Nonnull Ref<EntityStore> playerEntity,
        @Nonnull UICommandBuilder ui,
        @Nonnull UIEventBuilder events,
        @Nonnull Store<EntityStore> store
    ) {
        // Clear existing UI
        ui.clear("root");
        
        // Create main container
        ui.append("root", "main-container");
        
        // Title
        ui.append("main-container", "title-text");
        ui.set("title-text.text", "Chest Locked!");
        
        // Lock icon or visual indicator (if available)
        ui.append("main-container", "lock-icon");
        ui.set("lock-icon.text", "🔒"); // Using emoji as placeholder
        
        // Owner information
        ui.append("main-container", "owner-text");
        ui.set("owner-text.text", "This chest is locked by:");
        
        ui.append("main-container", "owner-name-text");
        String ownerName = lockedChest.getOwnerName();
        ui.set("owner-name-text.text", ownerName != null ? ownerName : "Unknown");
        
        // Location information
        ui.append("main-container", "location-text");
        ui.set("location-text.text", "Location: " + 
            lockedChest.getX() + ", " + 
            lockedChest.getY() + ", " + 
            lockedChest.getZ());
        
        // Close button
        ui.append("main-container", "close-button");
        ui.set("close-button.text", "OK");
        ui.set("close-button.type", "button");
        
        // Register button click event
        events.addEventBinding(CustomUIEventBindingType.Activating, "close-button");
    }
    
    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> playerEntity,
        @Nonnull Store<EntityStore> store,
        String eventId
    ) {
        if ("close-clicked".equals(eventId)) {
            close();
        }
    }
    
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> playerEntity, @Nonnull Store<EntityStore> store) {
        // Cleanup when page closes
    }
}

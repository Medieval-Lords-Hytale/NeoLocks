package me.ascheladd.hytale.quicksigns.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.ascheladd.hytale.quicksigns.QuickSigns;
import me.ascheladd.hytale.quicksigns.storage.SignHologramStorage;
import me.ascheladd.hytale.quicksigns.util.HologramUtil;

/**
 * UI page for entering sign text when placing a sign.
 * Allows up to 4 lines of text, 16 characters each.
 */
public class SignTextInputPage extends CustomUIPage {
    
    private final String worldId;
    private final int signX;
    private final double signY;
    private final int signZ;
    private final SignHologramStorage signHologramStorage;
    
    /**
     * Create sign text input page.
     * 
     * @param playerRef Player reference
     * @param playerId Player UUID
     * @param playerName Player name
     * @param worldId World ID
     * @param signX Sign X coordinate
     * @param signY Sign Y coordinate
     * @param signZ Sign Z coordinate
     * @param signHologramStorage Sign hologram storage for persistence
     */
    public SignTextInputPage(
        @Nonnull PlayerRef playerRef,
        UUID playerId,
        String playerName,
        String worldId,
        int signX,
        double signY,
        int signZ,
        SignHologramStorage signHologramStorage
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss);
        this.worldId = worldId;
        this.signX = signX;
        this.signY = signY;
        this.signZ = signZ;
        this.signHologramStorage = signHologramStorage;
        QuickSigns.debug("SignTextInputPage created for sign at " + worldId + ":" + signX + ":" + signY + ":" + signZ);
    }
    
    @Override
    public void build(
        @Nonnull Ref<EntityStore> playerEntity,
        @Nonnull UICommandBuilder ui,
        @Nonnull UIEventBuilder events,
        @Nonnull Store<EntityStore> store
    ) {
        // Load the UI file (path relative to Common/UI/Custom/)
        ui.append("Pages/QuickSigns/SignTextInput.ui");
        
        // Register button click events with captured text field values
        // Using @ prefix tells Hytale to capture the element's current value
        EventData confirmData = new EventData()
            .append("ButtonAction", "confirm")
            .append("@Line1", "#Line1Input.Value")
            .append("@Line2", "#Line2Input.Value")
            .append("@Line3", "#Line3Input.Value");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmButton", confirmData, false);
        
        EventData cancelData = new EventData().append("ButtonAction", "cancel");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", cancelData, false);
    }
    
    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> playerEntity,
        @Nonnull Store<EntityStore> store,
        String rawData
    ) {
        // Parse the raw JSON event data
        // Format: {"ButtonAction":"confirm","@Line1":"text1","@Line2":"text2"...}
        if (rawData == null || !rawData.contains("ButtonAction")) {
            return;
        }
        
        // Simple JSON parsing
        String action = extractJsonValue(rawData, "ButtonAction");
        
        if ("confirm".equals(action)) {
            // Extract text from all 3 lines
            String line1 = extractJsonValue(rawData, "@Line1");
            String line2 = extractJsonValue(rawData, "@Line2");
            String line3 = extractJsonValue(rawData, "@Line3");
            
            // Create multi-line hologram with stacked entities
            
            String[] lines = new String[3];
            lines[0] = line1 != null ? line1.trim() : "";
            lines[1] = line2 != null ? line2.trim() : "";
            lines[2] = line3 != null ? line3.trim() : "";
            
            // Cut off lines longer than 16 characters
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].length() > 16) {
                    lines[i] = lines[i].substring(0, 16);
                }
            }
            
            // Determine how many lines to create based on which lines have content
            // Base on how what line is last
            List<String> displayLines = new ArrayList<>();
            // 3 lines
            if (!lines[2].isEmpty()) {
                displayLines.add(lines[0]);
                displayLines.add(lines[1]);
                displayLines.add(lines[2]);
            // 2  lines
            } else if (!lines[1].isEmpty()) {
                displayLines.add(lines[0]);
                displayLines.add(lines[1]);
            // 1 line
            } else if (!lines[0].isEmpty()) {
                displayLines.add(lines[0]);
            }
            else {
                return; // No lines entered
            }
                
            // Get player position for offset calculation
            var transformComponent = store.getComponent(playerEntity, TransformComponent.getComponentType());
            if (transformComponent == null) return;
            var playerPos = transformComponent.getPosition();
            
            var world = store.getExternalData().getWorld();
            
            final List<String> finalLines = displayLines;
            final double playerX = playerPos.getX();
            final double playerZ = playerPos.getZ();
            
            world.execute(() -> {
                // Delete existing holograms at this sign location if any
                deleteExistingHolograms(world, worldId, signX, (int) signY, signZ);
                
                // Create multi-line hologram with stacked entities
                final double lineSpacing = 0.25;
                
                for (int i = 0; i < finalLines.size(); i++) {
                    String lineText = finalLines.get(i);
                    
                    // Calculate Y offset: center the text stack on the sign
                    // More lines means lower starting position so all lines fit on sign
                    // Formula: y + ((size - 1 - index) - (size - 1) / 2) * spacing
                    double yOffset = ((finalLines.size() - 1 - i) - (finalLines.size() - 1) / 2.0) * lineSpacing;
                    
                    HologramUtil.HologramResult result = HologramUtil.createHologram(
                        world,
                        signX,
                        signY + yOffset,
                        signZ,
                        playerX,
                        playerZ,
                        lineText
                    );
                    
                    // Persist the sign-hologram mapping using UUID for deletion when sign is broken
                    if (result != null) {
                        if (signHologramStorage != null && result.entityUuid != null) {
                            signHologramStorage.registerSignHologram(worldId, signX, (int) signY, signZ, result.entityUuid);
                            QuickSigns.debug("Persisted sign hologram mapping: " + worldId + ":" + signX + ":" + signY + ":" + signZ + " -> UUID:" + result.entityUuid);
                        } else {
                            if (signHologramStorage == null) {
                                QuickSigns.logger().atWarning().log("SignHologramStorage is null, hologram mapping will not persist!");
                            }
                            if (result.entityUuid == null) {
                                QuickSigns.logger().atWarning().log("Entity UUID is null, hologram mapping will not persist!");
                            }
                        }
                        
                        QuickSigns.debug("Created sign hologram line " + (i+1) + ": " + lineText);
                    } else {
                        QuickSigns.logger().atSevere().log("Failed to create hologram for line " + (i+1));
                    }
                }
            });
            
            close();
        } else if ("cancel".equals(action)) {
            // Just close the page without creating hologram
            close();
        }
    }
    
    /**
     * Simple JSON value extractor to avoid external dependencies.
     * Extracts value for a given key from JSON string.
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            return null;
        }
        startIndex += searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) {
            return null;
        }
        return json.substring(startIndex, endIndex);
    }
    
    /**
     * Deletes existing holograms at the sign location before creating new ones.
     */
    private void deleteExistingHolograms(
        World world,
        String worldId, 
        int x, 
        int y, 
        int z
    ) {
        List<UUID> existingUuids = signHologramStorage.removeSignHolograms(worldId, x, y, z);
        
        if (existingUuids == null || existingUuids.isEmpty()) {
            QuickSigns.debug("No existing holograms to delete at " + worldId + ":" + x + ":" + y + ":" + z);
            return;
        }
        
        QuickSigns.debug("Deleting " + existingUuids.size() + " existing sign holograms before creating new ones");
        
        var entityStore = world.getEntityStore();
        
        for (UUID uuid : existingUuids) {
            try {
                Ref<EntityStore> hologramRef = entityStore.getRefFromUUID(Objects.requireNonNull(uuid));
                
                if (hologramRef == null || !hologramRef.isValid()) {
                    QuickSigns.debug("Hologram UUID " + uuid + " not found or invalid, skipping");
                    continue;
                }
                
                // Remove entity
                com.hypixel.hytale.component.Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
                entityStore.getStore().removeEntity(hologramRef, holder, com.hypixel.hytale.component.RemoveReason.REMOVE);
                QuickSigns.debug("Deleted existing hologram UUID: " + uuid);
            } catch (Exception e) {
                QuickSigns.logger().atSevere().log("Failed to delete hologram UUID " + uuid + ": " + e.getMessage());
            }
        }
    }
    
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> playerEntity, @Nonnull Store<EntityStore> store) {
        // Cleanup when page closes
    }
}



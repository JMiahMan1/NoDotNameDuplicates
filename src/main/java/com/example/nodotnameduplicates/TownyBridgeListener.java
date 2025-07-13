package com.example.nodotnameduplicates;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

public class TownyBridgeListener implements Listener {

    private final NoDotNameDuplicates plugin;

    public TownyBridgeListener(NoDotNameDuplicates plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDataSync(PlayerDataSyncEvent event) {
        Player loggingInPlayer = event.getLoggingInPlayer();
        updateTownyResidentFile(loggingInPlayer.getName(), loggingInPlayer.getUniqueId());
    }

    private String normalizeName(String name) {
        return name.startsWith(".") ? name.substring(1).toLowerCase() : name.toLowerCase();
    }

    private void updateTownyResidentFile(String playerName, UUID loginUUID) {
        String baseName = normalizeName(playerName);
        String javaName = baseName;

        if (playerName.startsWith(".")) {
            for (Map.Entry<String, String> entry : plugin.getLinkedPlayers().entrySet()) {
                if (entry.getValue().equalsIgnoreCase(baseName)) {
                    javaName = entry.getKey();
                    break;
                }
            }
        }
        
        // --- ADDED LOGGING ---
        plugin.log("Towny bridge triggered by '" + playerName + "'. Checking resident file for '" + javaName + "'.");

        Path townyFile = Paths.get(plugin.getDataFolder().getParentFile().getAbsolutePath(),
                                "Towny", "data", "residents", javaName + ".txt");

        if (!Files.exists(townyFile)) {
            plugin.log("No Towny resident file found for " + javaName + ". Skipping UUID update.");
            return;
        }

        try {
            java.util.List<String> lines = Files.readAllLines(townyFile, StandardCharsets.UTF_8);
            boolean updated = false;

            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().toLowerCase().startsWith("uuid=")) {
                    lines.set(i, "uuid=" + loginUUID.toString());
                    updated = true;
                    break;
                }
            }

            if (updated) {
                Files.write(townyFile, lines, StandardCharsets.UTF_8);
                plugin.log("Successfully updated Towny resident file for '" + javaName + "' with UUID from '" + playerName + "'.");
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to read or write Towny resident file for " + javaName + ": " + e.getMessage());
        }
    }
}

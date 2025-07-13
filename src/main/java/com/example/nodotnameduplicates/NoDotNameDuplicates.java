package com.example.nodotnameduplicates;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NoDotNameDuplicates extends JavaPlugin implements Listener {

    private World defaultWorld;
    private boolean debug;
    private Map<String, String> linkedPlayers;
    private Path whitelistPath;
    private boolean autoWhitelist;
    private boolean townyIntegrationEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        this.defaultWorld = Bukkit.getWorlds().get(0);

        whitelistPath = Paths.get(Bukkit.getWorldContainer().getAbsolutePath(), "whitelist.json");
        try {
            if (!Files.exists(whitelistPath)) {
                Files.createDirectories(whitelistPath.getParent());
                Files.write(whitelistPath, "[]".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            getLogger().severe("Could not create whitelist file: " + e.getMessage());
        }

        getServer().getPluginManager().registerEvents(this, this);

        if (this.townyIntegrationEnabled) {
            if (Bukkit.getPluginManager().getPlugin("Towny") != null) {
                log("Towny integration is enabled.");
                getServer().getPluginManager().registerEvents(new TownyBridgeListener(this), this);
            } else {
                getLogger().warning("Towny integration was enabled in the config, but the Towny plugin was not found.");
            }
        } else {
            log("Towny integration is disabled in the config.");
        }

        log("Plugin enabled. Default world: " + defaultWorld.getName());
    }

    private void loadSettings() {
        FileConfiguration config = getConfig();
        this.debug = config.getBoolean("debug", false);
        this.autoWhitelist = config.getBoolean("auto-whitelist", true);
        this.townyIntegrationEnabled = config.getBoolean("integrations.towny.enabled", true);

        this.linkedPlayers = new HashMap<>();
        if (config.isConfigurationSection("linkedPlayers")) {
            for (String javaName : config.getConfigurationSection("linkedPlayers").getKeys(false)) {
                String bedrockName = config.getString("linkedPlayers." + javaName);
                if (bedrockName != null) {
                    linkedPlayers.put(javaName.toLowerCase(), bedrockName.toLowerCase());
                }
            }
        }
    }
    
    public void log(String msg) {
        if (debug) {
            getLogger().info("[DEBUG] " + msg);
        }
    }

    public Map<String, String> getLinkedPlayers() {
        return linkedPlayers;
    }

    private String normalizeName(String name) {
        return name.startsWith(".") ? name.substring(1).toLowerCase() : name.toLowerCase();
    }

    private File getPlayerDataFile(UUID uuid) {
        return new File(defaultWorld.getWorldFolder(), "playerdata/" + uuid + ".dat");
    }

    private OfflinePlayer getOfflinePlayerByName(String name) {
        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            if (p.getName() != null && p.getName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return Bukkit.getOfflinePlayer(name);
    }
    
    public String getCounterpartName(String playerName) {
        String baseName = normalizeName(playerName);
        boolean isBedrock = playerName.startsWith(".");

        for (Map.Entry<String, String> entry : linkedPlayers.entrySet()) {
            String javaName = entry.getKey();
            String bedrockName = entry.getValue();

            if (isBedrock && bedrockName.equalsIgnoreCase(baseName)) {
                return javaName;
            }
            if (!isBedrock && javaName.equalsIgnoreCase(baseName)) {
                return "." + bedrockName;
            }
        }
        return isBedrock ? baseName : "." + baseName;
    }

    private void syncIfNewer(File source, File target) {
        try {
            if (!target.exists() || source.lastModified() > target.lastModified()) {
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log("Synced data from " + source.getName() + " to " + target.getName());
            }
        } catch (IOException e) {
            getLogger().severe("Sync error: " + e.getMessage());
        }
    }

    private void syncBidirectional(File f1, File f2) {
        if (!f1.exists() || !f2.exists()) return;
        if (f1.lastModified() > f2.lastModified()) {
            syncIfNewer(f1, f2);
        } else if (f2.lastModified() > f1.lastModified()) {
            syncIfNewer(f2, f1);
        }
    }

    private void syncPlayerFiles(String playerName, UUID uuid) {
        String counterpartName = getCounterpartName(playerName);
        log("syncPlayerFiles: " + playerName + " -> counterpart: " + counterpartName);

        OfflinePlayer p2 = getOfflinePlayerByName(counterpartName);
        if (p2 == null || p2.getUniqueId() == null) {
            log("No UUID found for counterpart " + counterpartName);
            return;
        }

        File f1 = getPlayerDataFile(uuid);
        File f2 = getPlayerDataFile(p2.getUniqueId());

        if (f1.exists() && f2.exists()) {
            syncBidirectional(f1, f2);
        } else if (f1.exists()) {
            syncIfNewer(f1, f2);
        } else if (f2.exists()) {
            syncIfNewer(f2, f1);
        }
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String joiningName = event.getName();
        log("onPreLogin triggered for: " + joiningName);

        // This check must remain synchronous to prevent kick bypasses
        for (Player online : Bukkit.getOnlinePlayers()) {
            String counterpartName = getCounterpartName(joiningName);
            if (online.getName().equalsIgnoreCase(counterpartName)) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "Another account linked to you is already online.");
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        
        // Run sync and other tasks after the player has fully joined
        Bukkit.getScheduler().runTask(this, () -> {
            // Whitelist check
            if (name.startsWith(".") && autoWhitelist) {
                log("Processing Bedrock whitelist for " + name);
                try {
                    addBedrockUserToWhitelistIfNeeded(uuid, name);
                } catch (IOException e) {
                    getLogger().warning("Failed to update whitelist for Bedrock user " + name + ": " + e.getMessage());
                }
            }

            // Data sync
            syncPlayerFiles(name, uuid);

            // Fire the event for bridges like Towny
            log("Firing PlayerDataSyncEvent for " + name + "...");
            PlayerDataSyncEvent syncEvent = new PlayerDataSyncEvent(player);
            Bukkit.getPluginManager().callEvent(syncEvent);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            syncPlayerFiles(event.getPlayer().getName(), event.getPlayer().getUniqueId());
        }, 20L);
    }

    private JSONArray readWhitelistJson() throws IOException {
        return new JSONArray(new String(Files.readAllBytes(whitelistPath), StandardCharsets.UTF_8));
    }

    private void writeWhitelistJson(JSONArray jsonArray) throws IOException {
        Files.write(whitelistPath, jsonArray.toString(2).getBytes(StandardCharsets.UTF_8));
        if (Bukkit.getServer().hasWhitelist()) {
            Bukkit.getServer().reloadWhitelist();
            log("Whitelist reloaded due to update.");
        }
    }

    private Set<String> getWhitelistUsernames(JSONArray whitelistJson) {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < whitelistJson.length(); i++) {
            JSONObject entry = whitelistJson.getJSONObject(i);
            String name = entry.optString("name", "").toLowerCase();
            if (!name.isEmpty()) set.add(name);
        }
        return set;
    }
    
    private void addBedrockUserToWhitelistIfNeeded(UUID bedrockUuid, String bedrockUsername) throws IOException {
        String baseName = normalizeName(bedrockUsername);
        boolean allowedByLinkedPlayers = false;

        // Check if the Bedrock user's base name is a value in the linkedPlayers map
        if (linkedPlayers.containsValue(baseName)) {
            allowedByLinkedPlayers = true;
        }

        if (!allowedByLinkedPlayers) {
            log("Bedrock user " + bedrockUsername + " not found in linkedPlayers map. Whitelist check skipped.");
            return;
        }

        JSONArray whitelistJson = readWhitelistJson();
        if (getWhitelistUsernames(whitelistJson).contains(bedrockUsername.toLowerCase())) {
            log("Bedrock user " + bedrockUsername + " already in whitelist JSON.");
            return;
        }

        JSONObject newEntry = new JSONObject();
        newEntry.put("uuid", bedrockUuid.toString());
        newEntry.put("name", bedrockUsername);
        whitelistJson.put(newEntry);
        writeWhitelistJson(whitelistJson);

        log("Added Bedrock user '" + bedrockUsername + "' to JSON whitelist.");
    }
}

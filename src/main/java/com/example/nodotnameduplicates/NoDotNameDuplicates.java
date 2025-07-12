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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NoDotNameDuplicates extends JavaPlugin implements Listener {

    private World defaultWorld;
    private boolean debug;
    private Map<String, String> linkedPlayers;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        this.defaultWorld = Bukkit.getWorlds().get(0);
        getServer().getPluginManager().registerEvents(this, this);
        log("Plugin enabled. Default world: " + defaultWorld.getName());
    }

    private void loadSettings() {
        FileConfiguration config = getConfig();
        this.debug = config.getBoolean("debug", false);
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

    private void log(String msg) {
        if (debug) {
            getLogger().info("[DEBUG] " + msg);
        }
    }

    private String normalizeName(String name) {
        return name.startsWith(".") ? name.substring(1).toLowerCase() : name.toLowerCase();
    }

    private File getPlayerDataFile(UUID uuid) {
        return new File(defaultWorld.getWorldFolder(), "playerdata/" + uuid + ".dat");
    }

    private UUID getUUIDFromName(String name) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        return player != null ? player.getUniqueId() : null;
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
        String baseName = normalizeName(playerName);
        String counterpartName = playerName.startsWith(".") ? baseName : "." + baseName;

        // Override counterpart if linkedPlayers config has a custom mapping
        for (Map.Entry<String, String> entry : linkedPlayers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(baseName)) {
                counterpartName = entry.getValue();
            } else if (entry.getValue().equalsIgnoreCase(baseName)) {
                counterpartName = entry.getKey();
            }
        }

        UUID uuid1 = uuid;
        UUID uuid2 = getUUIDFromName(counterpartName);
        if (uuid2 == null) return;

        File f1 = getPlayerDataFile(uuid1);
        File f2 = getPlayerDataFile(uuid2);

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
        String joiningBase = normalizeName(joiningName);

        for (Player online : Bukkit.getOnlinePlayers()) {
            String onlineName = online.getName();
            String onlineBase = normalizeName(onlineName);

            boolean isSameBaseName = joiningBase.equalsIgnoreCase(onlineBase);

            boolean isManualLinkConflict = (
                (linkedPlayers.containsKey(joiningBase) && linkedPlayers.get(joiningBase).equalsIgnoreCase(onlineBase)) ||
                (linkedPlayers.containsKey(onlineBase) && linkedPlayers.get(onlineBase).equalsIgnoreCase(joiningBase)) ||
                (linkedPlayers.containsValue(joiningBase) && linkedPlayers.containsKey(onlineBase) &&
                 linkedPlayers.get(onlineBase).equalsIgnoreCase(joiningBase)) ||
                (linkedPlayers.containsValue(onlineBase) && linkedPlayers.containsKey(joiningBase) &&
                 linkedPlayers.get(joiningBase).equalsIgnoreCase(onlineBase))
            );

            if (isSameBaseName || isManualLinkConflict) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "Another account linked to you is already online.");
                return;
            }
        }

        UUID joiningUUID = event.getUniqueId();
        Bukkit.getScheduler().runTask(this, () -> syncPlayerFiles(joiningName, joiningUUID));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            syncPlayerFiles(event.getPlayer().getName(), event.getPlayer().getUniqueId());
        }, 40L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            syncPlayerFiles(event.getPlayer().getName(), event.getPlayer().getUniqueId());
        }, 20L);
    }
}

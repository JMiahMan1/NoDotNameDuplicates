package com.example.nodotnameduplicates;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerDataSyncEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final Player loggingInPlayer;

    public PlayerDataSyncEvent(Player loggingInPlayer) {
        this.loggingInPlayer = loggingInPlayer;
    }

    /**
     * Gets the player who is currently logging in and triggered the sync.
     * @return The Player object.
     */
    public Player getLoggingInPlayer() {
        return loggingInPlayer;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

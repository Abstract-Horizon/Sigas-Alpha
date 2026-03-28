package org.ah.sigas.broker.game;

import java.util.HashMap;
import java.util.Map;

import org.ah.sigas.broker.Broker;

public class GameOptions {

    private int minPlayers = 2;
    private int maxPlayers = 2;
    private boolean allowLateJoin = false;
    private int heartbeatPeriod = 2000;
    private int maxQueueSize = 50;
    private boolean playerStatusToAll = false;
    private int disconnectedClientTimeout = 60000;  // 60s

    private Map<String, Object> other = new HashMap<String, Object>();

    public GameOptions() {
    }

    public void fromJSON(Map<String, Object> json) {
        if (json == null) {
            json = new HashMap<String, Object>();
        }

        minPlayers = getInt(json, "min_players", minPlayers);
        maxPlayers = getInt(json, "max_players", maxPlayers);
        allowLateJoin = getBoolean(json, "allow_late_join", allowLateJoin);
        heartbeatPeriod = getInt(json, "heartbeat_period", heartbeatPeriod);
        maxQueueSize = getInt(json, "max_queue_size", maxQueueSize);
        playerStatusToAll = getBoolean(json, "player_status_to_all", true);
        disconnectedClientTimeout = getInt(json, "disconnected_client_timeout", disconnectedClientTimeout);

        other.putAll(json);
        if (Broker.TRACE) {
            System.out.println("Gmae options:");
            System.out.println("    minPlayers: "+ minPlayers);
            System.out.println("    maxPlayers: "+ maxPlayers);
            System.out.println("    allowLateJoin: "+ allowLateJoin);
            System.out.println("    heartbeatPeriod: "+ heartbeatPeriod);
            System.out.println("    maxQueueSize: "+ maxQueueSize);
            System.out.println("    playerStatusToAll: "+ playerStatusToAll);
            System.out.println("    disconnectedClientTimeout: "+ disconnectedClientTimeout);
            System.out.println("  Other proprerties:");
            for (Map.Entry<String, Object> entry : other.entrySet()) {
                System.out.println("    " + entry.getKey() + ": " + entry.getValue());
            }
        }
    }

    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }

    public boolean isAllowLateJoin() { return allowLateJoin; }

    public int getHeartbeatPeriod() { return heartbeatPeriod; }

    public int getMaxQueueSize() { return maxQueueSize; }

    public boolean isPlayerStatusToAll() { return playerStatusToAll; }

    public int getDisconnectedClientTimeout() { return disconnectedClientTimeout; }

    @SuppressWarnings("unused")
    private String getString(Map<String, Object> json, String name, String defaultValue) {
        if (json.containsKey(name)) {
            Object value = json.get(name);
            json.remove(name);
            if (value instanceof String) {
                return ((String)value);
            }

            return value.toString();
        }

        return defaultValue;
    }

    private int getInt(Map<String, Object> json, String name, int defaultValue) {
        if (json.containsKey(name)) {
            Object value = json.get(name);
            json.remove(name);
            if (value instanceof Integer) {
                return ((Integer)value);
            }
            if (value instanceof String) {
                try {
                    return Integer.parseInt(((String)value));
                } catch (NumberFormatException ignore) { }
            }
        }

        return defaultValue;
    }

    private boolean getBoolean(Map<String, Object> json, String name, boolean defaultValue) {
        if (json.containsKey(name)) {
            Object value = json.get(name);
            json.remove(name);
            if (value instanceof Boolean) {
                return ((Boolean)value);
            }
            if (value instanceof String) {
                return ((String)value).toLowerCase().equals("true");
            }
        }

        return defaultValue;
    }
}

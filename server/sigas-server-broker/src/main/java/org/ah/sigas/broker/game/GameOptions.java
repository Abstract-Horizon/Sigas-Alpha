package org.ah.sigas.broker.game;

import java.util.HashMap;
import java.util.Map;

public class GameOptions {

    private int minPlayers = 2;
    private int maxPlayers = 2;
    private boolean allowLateJoin = false;
    private int heartbeatPeriod = 2000;

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

        other.putAll(json);
    }

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

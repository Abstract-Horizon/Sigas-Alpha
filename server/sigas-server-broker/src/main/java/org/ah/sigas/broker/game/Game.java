package org.ah.sigas.broker.game;

import java.util.ArrayList;
import java.util.List;

public class Game {

    private String gameId;
    private List<Client> clients = new ArrayList<>();

    private final long createdTimestamp = System.currentTimeMillis();
    private long lastActivity;

    public Game(String gameId) {
        this.gameId = gameId;
        lastActivity = createdTimestamp;
    }

    public long getCreatedTimestamp() { return createdTimestamp; }

    public String getGameId() { return gameId; }

    public void addClient(Client client) { clients.add(client); }

    public void touch() { lastActivity = System.currentTimeMillis(); }

    public long getLastActivity() { return lastActivity; }
}

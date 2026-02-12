package org.ah.sigas.broker.game;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ah.sigas.broker.message.Message;

public class Game {

    public static enum State {
        CREATED,
        RUNNING,
    }

    private String gameId;
    private List<Client> clients = new ArrayList<>();

    private Client masterClient;

    private final long createdTimestamp = System.currentTimeMillis();
    private long lastActivity;

    private State state = State.CREATED;

    public Game(String gameId) {
        this.gameId = gameId;
        lastActivity = createdTimestamp;
    }

    public long getCreatedTimestamp() { return createdTimestamp; }

    public String getGameId() { return gameId; }

    public void addClient(Client client) {
        clients.add(client);
        if (client.isMaster()) {
            masterClient = client;
        }
    }

    public long getLastActivity() { return lastActivity; }

    public State getState() { return state; }

    public void setState(State state) { this.state = state; }

    public List<Client> getClients() { return clients; }


    public void touch() { lastActivity = System.currentTimeMillis(); }


    public void receivedMessage(Client client, Message message) throws IOException {
        if (!client.isMaster()) {
            masterClient.sendMessage(message);
        }
    }
}

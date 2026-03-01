package org.ah.sigas.broker.game;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.ah.sigas.broker.Broker;
import org.ah.sigas.broker.message.Message;

public class Game {

    public static enum State {
        CREATED,
        RUNNING,
    }

    private String gameId;
    private Map<String, Client> clients = new HashMap<>();

    private Client masterClient;

    private final long createdTimestamp = System.currentTimeMillis();
    private long lastActivity;

    private State state = State.CREATED;
    private GameOptions gameOptions;

    public Game(String gameId, GameOptions gameOptions) {
        this.gameId = gameId;
        this.gameOptions = gameOptions;
        lastActivity = createdTimestamp;
    }

    public long getCreatedTimestamp() { return createdTimestamp; }

    public String getGameId() { return gameId; }

    public void addClient(Client client) {
        clients.put(client.getClientId(), client);
        if (client.isMaster()) {
            masterClient = client;
        }
    }

    public long getLastActivity() { return lastActivity; }

    public State getState() { return state; }

    public void setState(State state) { this.state = state; }

    public Client getMasterClient() { return masterClient; }

    public Map<String, Client> getClients() { return clients; }

    public GameOptions getGameOptions() { return gameOptions; }

    public void touch() { lastActivity = System.currentTimeMillis(); }


    public void receivedMessage(Client client, Message message) throws IOException {
        if (!client.isMaster()) {
            masterClient.sendMessage(message);
        } else {
            String clientId = message.getClientId();
            if ("00".equals(clientId)) {
                for (Client destinationClient : clients.values()) {
                    if (!destinationClient.isMaster()) {
                        destinationClient.sendMessage(message);
                    }
                }
            } else {
                Client destinationClient = clients.get(clientId);
                if (destinationClient == null) {
                    if (Broker.INFO) { client.log("Got server message for non-existent client '" + clientId + "'"); }
                } else {
                    destinationClient.sendMessage(message);
                }
            }
        }
    }
}

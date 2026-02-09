package org.ah.sigas.broker.game;

import java.util.LinkedList;

import org.ah.sigas.broker.Broker;
import org.ah.sigas.broker.ClientHandler;
import org.ah.sigas.broker.message.Message;

public class Client {

    private Game game;
    private boolean master;
    private String token;
    private final long createdTimestamp = System.currentTimeMillis();
    private long lastActivity;
    private ClientHandler clientInboundHandler;
    private ClientHandler clientOutboundHandler;

    private LinkedList <Message> receivedMessages = new LinkedList<>();

    public Client(Game game, String token, boolean master) {
        this.game = game;
        this.token = token;
        this.master = master;
        lastActivity = createdTimestamp;
    }

    public Game getGame() { return game; }
    public boolean isMaster() { return master; }
    public String getToken() { return token; }
    public long getCreatedTimestamp() { return createdTimestamp; }
    public long getLastActivity() { return lastActivity; }

    public ClientHandler getInboundHandler() { return clientInboundHandler; }
    public void setInboundHandler(ClientHandler clientInboundHandler) { this.clientInboundHandler = clientInboundHandler; }

    public ClientHandler getOutboundHandler() { return clientOutboundHandler; }
    public void setOutboundHandler(ClientHandler clientOutboundHandler) { this.clientOutboundHandler = clientOutboundHandler; }

    public void touch() { lastActivity = System.currentTimeMillis(); }

    public LinkedList<Message> getReceivedMessages() { return receivedMessages; }

    public void receivedMessage(String type, byte[] body) {
        if (Broker.TRACE) { System.out.println("Received message '" + type + "': \n" + new String(body)); }
        receivedMessages.add(Message.createMessage(type, body));
    }
}
